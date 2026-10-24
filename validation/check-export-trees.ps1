[CmdletBinding()]
param([string[]]$Project)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$workspace=Split-Path -Parent $PSScriptRoot

function Read-Git([string[]]$Arguments) {
    $lines=@(& git -c core.quotepath=false @Arguments)
    if ($LASTEXITCODE -ne 0) { throw "Git read failed: $($Arguments -join ' ')" }
    return $lines
}

function Read-TreeFiles([string]$Revision) {
    $files=@{}
    foreach ($line in Read-Git @('ls-tree','-r',$Revision)) {
        if ($line -notmatch '^(\d+) blob ([a-f0-9]+)\t(.+)$') { throw "Unexpected source tree entry: $line" }
        $files[$Matches[3]]="$($Matches[1]) $($Matches[2])"
    }
    return ,$files
}

$results=[System.Collections.Generic.List[object]]::new()
Push-Location -LiteralPath $workspace
try {
    $source=(Read-Git @('rev-parse','HEAD')) -join ''
    $manifest=(Read-Git @('show',"${source}:branch-exports.json")) -join "`n" | ConvertFrom-Json
    $entries=@($manifest.projects.PSObject.Properties | Where-Object {$_.Value.enabled} | Sort-Object Name)
    foreach ($requested in $Project) {
        if ($requested -notin $entries.Name) { throw "Unknown or disabled export: $requested" }
    }
    foreach ($entry in $entries) {
        if ($Project -and $entry.Name -notin $Project) { continue }
        $branch=[string]$entry.Value.branch
        try {
            $revision=(Read-Git @('rev-parse',"refs/heads/$branch")) -join ''
            $expected=Read-TreeFiles "${source}:$($entry.Name)"
            foreach ($dependency in $entry.Value.dependencies) {
                $destination=[string]$dependency.destination
                foreach ($old in @($expected.Keys)) {
                    if ($old -ceq $destination -or $old.StartsWith("$destination/",[StringComparison]::Ordinal)) {
                        $expected.Remove($old)
                    }
                }
                $object=(Read-Git @('rev-parse',"${source}:$($dependency.source)")) -join ''
                $type=(Read-Git @('cat-file','-t',$object)) -join ''
                if ($type -eq 'blob') { $expected[$destination]="100644 $object" }
                elseif ($type -eq 'tree') {
                    $children=Read-TreeFiles $object
                    foreach ($path in $children.Keys) { $expected["$destination/$path"]=$children[$path] }
                } else { throw 'Unsupported dependency object' }
            }
            $actual=Read-TreeFiles $revision
            $differences=@(Compare-Object -ReferenceObject @($expected.Keys | Sort-Object) -DifferenceObject @($actual.Keys | Sort-Object))
            if ($differences.Count -gt 0) { throw 'Missing or unexpected exported files' }
            foreach ($path in $expected.Keys) {
                if ($expected[$path] -cne $actual[$path]) { throw "Content or file-mode mismatch: $path" }
            }
            foreach ($path in 'build.gradle','gradle.properties','settings.gradle','gradlew','gradlew.bat',
                'gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties') {
                if (-not $actual.ContainsKey($path)) { throw "Missing standalone build input: $path" }
            }
            if ($actual['gradlew'] -notmatch '^100755 ') { throw 'Unix build launcher is not executable' }
            $metadata=if ($entry.Value.PSObject.Properties.Name -contains 'metadata_path') {
                [string]$entry.Value.metadata_path
            } elseif ($entry.Name.StartsWith('Fabric_')) { 'src/main/resources/fabric.mod.json' }
            else { 'src/main/resources/plugin.yml' }
            if (-not $actual.ContainsKey($metadata)) { throw "Missing platform metadata: $metadata" }
            if (((Read-Git @('rev-parse',"refs/heads/$branch")) -join '') -ne $revision) { throw 'Branch moved during audit' }
            $results.Add([pscustomobject]@{project=$entry.Name;branch=$branch;revision=$revision;status='PASS';files=$actual.Count;detail='Exact committed sources, materialized dependencies and executable wrapper; no compilation implied'})
        } catch {
            $results.Add([pscustomobject]@{project=$entry.Name;branch=$branch;revision='';status='FAIL';files=0;detail=$_.Exception.Message})
        }
    }
    if (((Read-Git @('rev-parse','HEAD')) -join '') -ne $source) { throw 'Main moved during audit' }
} finally { Pop-Location }
$target=Join-Path $PSScriptRoot 'results'
$null=New-Item -ItemType Directory -Path $target -Force
[pscustomobject]@{
    generated_at=[DateTimeOffset]::Now.ToString('o');source_revision=$source
    scope='Source-tree integrity only; not standalone compilation';results=$results.ToArray()
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $target 'export-trees.json') -Encoding UTF8
$results | Format-Table project,status,files,detail -AutoSize
if ($results.Count -eq 0 -or @($results | Where-Object status -EQ 'FAIL').Count -gt 0) { exit 1 }
exit 0
