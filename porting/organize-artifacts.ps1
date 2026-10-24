[CmdletBinding()]
param([switch]$Preview)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$workspacePrefix = $workspace + [IO.Path]::DirectorySeparatorChar
$moves = [Collections.Generic.List[object]]::new()

function Test-CcrJar([string]$Path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($metadata in 'fabric.mod.json', 'plugin.yml') {
            $entry = $zip.GetEntry($metadata)
            if ($null -eq $entry) { continue }
            $reader = [IO.StreamReader]::new($entry.Open())
            try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if ($metadata -eq 'fabric.mod.json') {
                return ($text | ConvertFrom-Json).id -in @('cutcleanreborn', 'ccrverification')
            }
            return $text -match '(?m)^name:\s*CutCleanReborn\s*$'
        }
        return $false
    } finally { $zip.Dispose() }
}

function Archive-CcrJar([IO.FileInfo]$File, [string]$ArchiveRoot) {
    $source = [IO.Path]::GetFullPath($File.FullName)
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    $directory = [IO.Path]::GetFullPath((Join-Path $ArchiveRoot $hash))
    $destination = Join-Path $directory $File.Name
    $index = 1
    while (Test-Path -LiteralPath $destination) {
        $index++
        $destination = Join-Path $directory "$($File.BaseName)-$index.jar"
    }
    foreach ($path in $source, $directory, $destination) {
        if (-not $path.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Artifact path is outside this workspace: $path"
        }
    }
    if (-not $Preview) {
        $null = New-Item -ItemType Directory -Path $directory -Force
        Move-Item -LiteralPath $source -Destination $destination
    }
    $moves.Add([pscustomobject]@{
        source=$source.Substring($workspacePrefix.Length)
        archive=$destination.Substring($workspacePrefix.Length)
        sha256=$hash
    })
}

foreach ($module in Get-ChildItem -LiteralPath $workspace -Directory | Sort-Object Name) {
    if ($module.Name -notmatch '^(Paper|Purpur|Spigot|Bukkit|Fabric)_(\d[\w.]*)$') { continue }
    $platform = $Matches[1]
    $family = $Matches[2]
    $properties = @{}
    foreach ($line in Get-Content -LiteralPath (Join-Path $module.FullName 'gradle.properties')) {
        if ($line -match '^([^#=\s]+)=(.*)$') { $properties[$Matches[1]]=$Matches[2].Trim() }
    }
    $version = if ($platform -eq 'Fabric') { $properties['mod_version'] } else { $properties['version'] }
    $currentName = "cutcleanreborn-$version+$family.jar"
    $backup = Join-Path $module.FullName 'build/artifact-backups'
    # Only generated artifacts and isolated QA servers; never run-server/ or client profiles.
    foreach ($relative in 'build/libs', 'build/smoke-server/plugins', 'build/smoke-server/production/mods',
            'build/smoke-server/persistence/mods', 'build/manual-server/plugins', 'build/client-test-server/plugins') {
        $directory = Join-Path $module.FullName $relative
        if (-not (Test-Path -LiteralPath $directory -PathType Container)) { continue }
        foreach ($file in Get-ChildItem -LiteralPath $directory -File -Filter '*.jar') {
            if ($file.Name -eq $currentName) { continue }
            if ($relative -ne 'build/libs' -and $file.Name -notlike 'cutcleanreborn-*.jar') { continue }
            if (Test-CcrJar $file.FullName) { Archive-CcrJar $file $backup }
        }
    }
    $distribution = Join-Path $workspace "dist/$platform/$family"
    if (-not (Test-Path -LiteralPath $distribution -PathType Container)) { continue }
    foreach ($file in Get-ChildItem -LiteralPath $distribution -File -Filter '*.jar') {
        if ($file.Name -ne $currentName -and (Test-CcrJar $file.FullName)) {
            Archive-CcrJar $file (Join-Path $workspace "dist/archive/$platform/$family")
        }
    }
}

$receipt = [pscustomobject]@{ generated_at=[DateTimeOffset]::Now.ToString('o'); preview=[bool]$Preview; moves=$moves.ToArray() }
if (-not $Preview) {
    $receipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $workspace 'validation/results/artifact-layout-latest.json') -Encoding UTF8
}
$moves | Select-Object source, archive
Write-Host "$($moves.Count) CCR artifacts $(if ($Preview) { 'would be archived' } else { 'archived' }); no files deleted."
