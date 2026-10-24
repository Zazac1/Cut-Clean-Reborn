# Pure committed-source export: no checkout, index, worktree or untracked data is read.
function Get-TreeEntries([string]$Tree) {
    $entries = @{}
    foreach ($line in Invoke-Git @('ls-tree', $Tree)) {
        if ($line -notmatch '^(\d+) (blob|tree) ([0-9a-f]+)\t(.+)$') { throw "Unexpected Git tree record: $line" }
        $entries[$Matches[4]] = [pscustomobject]@{ Mode=$Matches[1]; Type=$Matches[2]; Object=$Matches[3]; Name=$Matches[4] }
    }
    return ,$entries
}

function Write-ExportTree([hashtable]$Entries) {
    $records = @($Entries.Keys | Sort-Object | ForEach-Object {
        $entry=$Entries[$_]
        "$($entry.Mode) $($entry.Type) $($entry.Object)`t$($entry.Name)"
    })
    # Windows PowerShell's native pipeline writes CRLF; mktree treats CR as part
    # of the filename. Send explicitly LF-terminated UTF-8 records instead.
    $start=[System.Diagnostics.ProcessStartInfo]::new('git','mktree')
    $start.WorkingDirectory=$PSScriptRoot
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    $start.StandardInputEncoding=[Text.UTF8Encoding]::new($false)
    $process=[System.Diagnostics.Process]::Start($start)
    try {
        if ($records.Count -gt 0) { $process.StandardInput.Write(($records -join "`n") + "`n") }
        $process.StandardInput.Close()
        $result=$process.StandardOutput.ReadToEnd().Trim()
        $failure=$process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0 -or $result -notmatch '^[0-9a-f]{40,64}$') { throw "Failed to compose a standalone export tree: $failure" }
        return $result
    } finally { $process.Dispose() }
}

function Set-ExportEntry([hashtable]$Entries, [string[]]$Parts, [object]$Entry) {
    $head=$Parts[0]
    if ($head -in @('.','..') -or [string]::IsNullOrWhiteSpace($head)) { throw 'Invalid export destination' }
    if ($Parts.Count -eq 1) {
        $Entries[$head]=[pscustomobject]@{ Mode=$Entry.Mode; Type=$Entry.Type; Object=$Entry.Object; Name=$head }
        return
    }
    if ($Entries.ContainsKey($head)) {
        if ($Entries[$head].Type -ne 'tree') { throw "Export directory conflicts with a file: $head" }
        $children=Get-TreeEntries $Entries[$head].Object
    } else { $children=@{} }
    Set-ExportEntry $children $Parts[1..($Parts.Count-1)] $Entry
    $Entries[$head]=[pscustomobject]@{ Mode='040000'; Type='tree'; Object=(Write-ExportTree $children); Name=$head }
}

function Get-StandaloneExportTree([string]$Source, [string]$Project, [object]$Spec) {
    $baseTree=(Invoke-Git @('rev-parse', "${Source}:$Project")) -join ''
    if (@($Spec.dependencies).Count -eq 0) { return $baseTree }
    $entries=Get-TreeEntries $baseTree
    foreach ($dependency in $Spec.dependencies) {
        $path=[string]$dependency.source
        if ($path.StartsWith('/') -or $path -match '(^|/)\.\.(/|$)') { throw 'Invalid source path in export manifest' }
        $object=(Invoke-Git @('rev-parse', "${Source}:$path")) -join ''
        $type=(Invoke-Git @('cat-file', '-t', $object)) -join ''
        $mode=if ($type -eq 'tree') { '040000' } elseif ($type -eq 'blob') { '100644' } else { throw 'Only source trees/files can be vendored' }
        $entry=[pscustomobject]@{ Mode=$mode; Type=$type; Object=$object; Name='' }
        Set-ExportEntry $entries ([string[]]$dependency.destination.Split('/')) $entry
    }
    return Write-ExportTree $entries
}
