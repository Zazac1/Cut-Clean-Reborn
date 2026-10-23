[CmdletBinding()]
param([string[]]$Project)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$false
$workspace=Split-Path -Parent $PSScriptRoot
$output=Join-Path $workspace 'build/branch-validation'
$null=New-Item -ItemType Directory -Path $output -Force
$results=[System.Collections.Generic.List[object]]::new()
Push-Location -LiteralPath $workspace
try {
    $manifest=(& git show HEAD:branch-exports.json) -join "`n" | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read committed export manifest' }
    foreach ($entry in $manifest.projects.PSObject.Properties | Where-Object {$_.Value.enabled} | Sort-Object Name) {
        if ($Project -and $entry.Name -notin $Project) { continue }
        $branch=$entry.Value.branch
        try {
            $revision=(& git rev-parse --verify "refs/heads/$branch") -join ''
            if ($LASTEXITCODE -ne 0) { throw 'Export branch missing: synchronize locally first' }
            $stem=$entry.Name+'-'+$revision.Substring(0,12)
            $archive=Join-Path $output "$stem.zip"
            $checkout=Join-Path $output $stem
            if (-not (Test-Path -LiteralPath $checkout)) {
                & git archive --format=zip -o $archive $revision
                if ($LASTEXITCODE -ne 0) { throw 'Git archive failed' }
                Expand-Archive -LiteralPath $archive -DestinationPath $checkout
            }
            # Sibling modules are intentionally absent: this must build on its own.
            Push-Location -LiteralPath $checkout
            try {
                $extra=if ($entry.Name.StartsWith('Fabric_')) { 'qaJar' } else { 'smokePluginJar' }
                & .\gradlew.bat build $extra --console=plain --max-workers=2 2>&1 |
                    Tee-Object -FilePath (Join-Path $output "$stem-build.log")
                $code=$LASTEXITCODE
            } finally { Pop-Location }
            if ($code -ne 0) { throw "Standalone Gradle build failed ($code)" }
            $suites=@(Get-ChildItem -LiteralPath (Join-Path $checkout 'build/test-results/test') -Filter 'TEST-*.xml')
            if ($suites.Count -eq 0) { throw 'No standalone unit test result' }
            $total=0
            foreach ($file in $suites) {
                [xml]$suite=Get-Content -LiteralPath $file.FullName
                if ([int]$suite.testsuite.failures -ne 0 -or [int]$suite.testsuite.errors -ne 0) { throw 'Standalone tests failed' }
                $total += [int]$suite.testsuite.tests
            }
            if ($total -lt 1) { throw 'Empty standalone test suite' }
            $results.Add([pscustomobject]@{project=$entry.Name;branch=$branch;revision=$revision;status='PASS';tests=$total;detail='Standalone sources, release and verification compilation'})
        } catch { $results.Add([pscustomobject]@{project=$entry.Name;branch=$branch;revision='';status='FAIL';tests=0;detail=$_.Exception.Message}) }
    }
} finally { Pop-Location }
$target=Join-Path $PSScriptRoot 'results'
$null=New-Item -ItemType Directory -Path $target -Force
$report=[pscustomobject]@{generated_at=[DateTimeOffset]::Now.ToString('o');results=$results.ToArray()}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $target 'exports.json') -Encoding UTF8
$results | Format-Table -AutoSize
if (@($results | Where-Object status -EQ 'FAIL').Count -gt 0 -or $results.Count -eq 0) { exit 1 }
exit 0
