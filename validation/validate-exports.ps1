[CmdletBinding()]
param([string[]]$Project)

Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$false
$workspace=Split-Path -Parent $PSScriptRoot
$output=Join-Path $workspace 'validation/build/branch-validation'
$attempt=[DateTimeOffset]::Now.ToString('yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$attemptOutput=Join-Path $output $attempt
$null=New-Item -ItemType Directory -Path $output -Force
$null=New-Item -ItemType Directory -Path $attemptOutput
$results=[System.Collections.Generic.List[object]]::new()
$source=''
Push-Location -LiteralPath $workspace
try {
    & (Join-Path $PSScriptRoot 'check-export-trees.ps1') -Project $Project
    if ($LASTEXITCODE -ne 0) { throw 'Export trees do not match main; synchronize locally before building' }
    $integrity=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'results/export-trees.json') -Raw | ConvertFrom-Json
    $source=[string]$integrity.source_revision
    $manifest=(& git show HEAD:validation/git/branch-exports.json) -join "`n" | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read committed export manifest' }
    foreach ($entry in $manifest.projects.PSObject.Properties | Where-Object {$_.Value.enabled} | Sort-Object Name) {
        if ($Project -and $entry.Name -notin $Project) { continue }
        $branch=$entry.Value.branch
        try {
            $revision=(& git rev-parse --verify "refs/heads/$branch") -join ''
            if ($LASTEXITCODE -ne 0) { throw 'Export branch missing: synchronize locally first' }
            $proof=@($integrity.results | Where-Object project -EQ $entry.Name)
            if ($proof.Count -ne 1 -or $proof[0].status -ne 'PASS' -or $proof[0].revision -ne $revision) {
                throw 'Branch moved after source-tree integrity check'
            }
            $stem=$entry.Name+'-'+$revision.Substring(0,12)
            $archive=Join-Path $attemptOutput "$stem.zip"
            $checkout=Join-Path $attemptOutput $stem
            # Never trust a reused checkout or an old test XML. Every attempt
            # gets a fresh archive; ordinary dependency caches remain available.
            if (Test-Path -LiteralPath $checkout) { throw 'Fresh export checkout already exists' }
            & git archive --format=zip -o $archive $revision
            if ($LASTEXITCODE -ne 0) { throw 'Git archive failed' }
            Expand-Archive -LiteralPath $archive -DestinationPath $checkout
            # Sibling modules are intentionally absent: this must build on its own.
            Push-Location -LiteralPath $checkout
            try {
                $extra=if ($entry.Name.StartsWith('Fabric_')) { 'qaJar' } else { 'smokePluginJar' }
                & .\gradlew.bat build $extra --console=plain --max-workers=2 2>&1 |
                    Tee-Object -FilePath (Join-Path $attemptOutput "$stem-build.log")
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
            $current=(& git rev-parse --verify "refs/heads/$branch") -join ''
            if ($LASTEXITCODE -ne 0 -or $current -ne $revision) { throw 'Branch moved during standalone build' }
            $results.Add([pscustomobject]@{project=$entry.Name;branch=$branch;revision=$revision;status='PASS';tests=$total;checkout=$checkout;detail='Fresh standalone archive, current sources, release and verification compilation'})
        } catch { $results.Add([pscustomobject]@{project=$entry.Name;branch=$branch;revision='';status='FAIL';tests=0;detail=$_.Exception.Message}) }
    }
    $currentSource=(& git rev-parse HEAD) -join ''
    if ($LASTEXITCODE -ne 0 -or $currentSource -ne $source) { throw 'Main moved during standalone validation' }
} catch {
    # Replace a previous successful report even when preflight fails before the
    # first project builds. A stale green report must not survive a failed run.
    $results.Add([pscustomobject]@{project='export-preflight';branch='';revision='';status='FAIL';tests=0;detail=$_.Exception.Message})
} finally { Pop-Location }
$target=Join-Path $PSScriptRoot 'results'
$null=New-Item -ItemType Directory -Path $target -Force
$report=[pscustomobject]@{generated_at=[DateTimeOffset]::Now.ToString('o');source_revision=$source;attempt=$attempt;results=$results.ToArray()}
$history=Join-Path $target 'exports'
$null=New-Item -ItemType Directory -Path $history -Force
$json=$report | ConvertTo-Json -Depth 5
$json | Set-Content -LiteralPath (Join-Path $history "$attempt.json") -Encoding UTF8
$json | Set-Content -LiteralPath (Join-Path $target 'exports.json') -Encoding UTF8
$results | Format-Table -AutoSize
if (@($results | Where-Object status -EQ 'FAIL').Count -gt 0 -or $results.Count -eq 0) { exit 1 }
exit 0
