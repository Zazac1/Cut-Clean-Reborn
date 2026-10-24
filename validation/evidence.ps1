Set-StrictMode -Version Latest

# Hash all authored compilation/test inputs, not timestamps or generated class files.
function Get-ProjectFingerprint([string]$Workspace, [string]$ProjectName) {
    $module = Join-Path $Workspace $ProjectName
    $paths = [System.Collections.Generic.List[string]]::new()
    foreach ($name in 'build.gradle','gradle.properties','settings.gradle','gradle/wrapper/gradle-wrapper.properties') {
        $candidate = Join-Path $module $name
        if (Test-Path -LiteralPath $candidate) { $paths.Add($candidate) }
    }
    $sourcePath = Join-Path $module 'src'
    if (Test-Path -LiteralPath $sourcePath) {
        foreach ($file in Get-ChildItem -LiteralPath $sourcePath -Recurse -File) { $paths.Add($file.FullName) }
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
