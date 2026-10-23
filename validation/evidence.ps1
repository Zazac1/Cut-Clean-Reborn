Set-StrictMode -Version Latest

# Hash all authored compilation/test inputs, not timestamps or generated class files.
function Get-ProjectFingerprint([string]$Workspace, [string]$ProjectName) {
    $module = Join-Path $Workspace $ProjectName
    $sourceModule = $module
    $logic = $null
    if ($ProjectName -match '^Purpur_(.+)$') {
        $sourceModule = Join-Path $Workspace "Paper_$($Matches[1])"
        $logic = Join-Path $Workspace 'build-logic/purpur.gradle'
    } elseif ($ProjectName -match '^(Spigot|Bukkit)_') {
        $sourceModule = Join-Path $Workspace 'platform-bukkit'
        $logic = Join-Path $Workspace 'build-logic/bukkit.gradle'
    }
    $paths = [System.Collections.Generic.List[string]]::new()
    foreach ($name in 'build.gradle','gradle.properties','settings.gradle','gradle/wrapper/gradle-wrapper.properties') {
        $candidate = Join-Path $module $name
        if (Test-Path -LiteralPath $candidate) { $paths.Add($candidate) }
    }
    if ($logic) { $paths.Add($logic) }
    $sourcePath = Join-Path $sourceModule 'src'
    if (Test-Path -LiteralPath $sourcePath) {
        foreach ($file in Get-ChildItem -LiteralPath $sourcePath -Recurse -File) { $paths.Add($file.FullName) }
    }
    # Fabric may share native sources; include these until each exported branch vendors them.
    if ($ProjectName -match '^Fabric_' -and $ProjectName -ne 'Fabric_1.21.1') {
        $productionLogic=Join-Path $Workspace 'build-logic/fabric-modern-production.gradle'
        if ($ProjectName -ne 'Fabric_1.21.1' -and (Test-Path -LiteralPath $productionLogic)) { $paths.Add($productionLogic) }
        foreach ($directory in 'platform-fabric-modern') {
            $shared = Join-Path $Workspace "$directory/src"
            if (Test-Path -LiteralPath $shared) {
                foreach ($file in Get-ChildItem -LiteralPath $shared -Recurse -File) { $paths.Add($file.FullName) }
            }
        }
    }
    $lines = foreach ($path in $paths | Sort-Object -Unique) {
        $relative = $path.Substring($Workspace.Length + 1).Replace('\','/')
        "$relative $((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash)"
    }
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))).Replace('-','').ToLowerInvariant() }
    finally { $hasher.Dispose() }
}

function Write-ValidationEvidence([string]$Workspace, [string]$ProjectName, [string]$Platform,
    [string]$Minecraft, [string]$Artifact, [int]$UnitTests, [string]$NativeResult) {
    $target = Join-Path $Workspace 'validation/results/evidence'
    $null = New-Item -ItemType Directory -Path $target -Force
    [pscustomobject]@{
        schema=1; verified_at=[DateTimeOffset]::Now.ToString('o'); project=$ProjectName
        platform=$Platform; minecraft=$Minecraft; artifact=(Split-Path -Leaf $Artifact)
        artifact_sha256=(Get-FileHash -LiteralPath $Artifact -Algorithm SHA256).Hash.ToLowerInvariant()
        source_fingerprint=(Get-ProjectFingerprint $Workspace $ProjectName)
        unit_tests=$UnitTests; native_result=$NativeResult
        real_player_tests='pending'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $target "$ProjectName.json") -Encoding UTF8
}
