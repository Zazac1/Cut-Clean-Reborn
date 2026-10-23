[CmdletBinding()]
param(
    [ValidateSet('Quick', 'Compile', 'Full')]
    [string]$Mode = 'Full',
    [string[]]$Version,
    [switch]$RerunTasks
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$resultsDirectory = Join-Path $PSScriptRoot 'results'
$null = New-Item -ItemType Directory -Path $resultsDirectory -Force
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check([string]$Project, [string]$Name, [bool]$Passed, [string]$Detail) {
    $status = if ($Passed) { 'PASS' } else { 'FAIL' }
    $checks.Add([pscustomobject]@{ Project=$Project; Check=$Name; Status=$status; Detail=$Detail })
    Write-Host "[$status] $Project - $Name : $Detail"
}

function Read-Properties([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') { $values[$Matches[1]] = $Matches[2] }
    }
    return $values
}

function Get-SourceMap([string]$ProjectDirectory) {
    $sources = @{}
    $sourceRoot = Join-Path $ProjectDirectory 'src'
    foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -Recurse -File) {
        $relative = $file.FullName.Substring($sourceRoot.Length + 1).Replace('\', '/')
        if ($relative -eq 'main/resources/plugin.yml') { continue }
        $sources[$relative] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    }
    foreach ($name in 'build.gradle','LICENSE') {
        $sources[$name] = (Get-FileHash -LiteralPath (Join-Path $ProjectDirectory $name) -Algorithm SHA256).Hash
    }
    return $sources
}

function Test-Artifact([System.IO.DirectoryInfo]$Project, [hashtable]$Properties) {
    $artifactName = "cutcleanreborn-$($Properties['version'])+$($Properties['minecraft_version']).jar"
    $artifact = Join-Path $Project.FullName "build/libs/$artifactName"
    if (-not (Test-Path -LiteralPath $artifact)) { throw "JAR absent : $artifactName" }
    $archive = [System.IO.Compression.ZipFile]::OpenRead($artifact)
    try {
        $entry = $archive.GetEntry('plugin.yml')
        if ($null -eq $entry) { throw 'plugin.yml absent du JAR' }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($metadata -notmatch '(?m)^name:\s*CutCleanReborn\s*$') { throw 'Identite du plugin incorrecte' }
        if ($metadata -notmatch ('(?m)^version:\s*' + [regex]::Escape($Properties['version']) + '\s*$')) { throw 'Version interne incorrecte' }
        if ($metadata -notmatch ('(?m)^api-version:\s*[''\"]?' + [regex]::Escape($Properties['minecraft_version']) + '[''\"]?\s*$')) { throw 'Version Minecraft incorrecte' }
        if ($null -eq $archive.GetEntry('config.yml')) { throw 'Configuration par defaut absente' }
        if (@($archive.Entries | Where-Object FullName -Match '^fr/isaac/cutcleanreborn/verification/').Count -ne 0) { throw 'Code de test present dans le JAR distribue' }
        if ($null -eq $archive.GetEntry('fr/isaac/cutcleanreborn/paper/CutCleanRebornPlugin.class')) { throw 'Classe principale absente' }
        Add-Check $Project.Name 'JAR' $true $artifactName
    } finally { $archive.Dispose() }
}

try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $projects = @(Get-ChildItem -LiteralPath $workspaceRoot -Directory | Where-Object Name -Match '^Paper_\d' | Sort-Object Name)
    if ($Version) {
        foreach ($requested in $Version) {
            if ($requested -notin $projects.Name) { throw "Projet inconnu : $requested" }
        }
        $projects = @($projects | Where-Object { $_.Name -in $Version })
    }
    if ($projects.Count -eq 0) { throw 'Aucun projet Paper trouve' }

    $reference = $null
    $referenceName = ''
    foreach ($project in $projects) {
        try {
            $required = 'build.gradle','gradle.properties','settings.gradle','gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar',
                'LICENSE','README.md','src/main/resources/plugin.yml','src/main/resources/config.yml',
                'src/main/java/fr/isaac/cutcleanreborn/paper/CutCleanRebornPlugin.java',
                'src/test/java/fr/isaac/cutcleanreborn/paper/GameplayRegressionTest.java',
                'src/smoke/java/fr/isaac/cutcleanreborn/verification/CCRSmoke.java','src/smoke/server.properties'
            foreach ($relative in $required) {
                if (-not (Test-Path -LiteralPath (Join-Path $project.FullName $relative))) { throw "Fichier requis absent : $relative" }
            }
            $buildScript = Get-Content -LiteralPath (Join-Path $project.FullName 'build.gradle') -Raw
            if ($buildScript -match 'paper-common|\.\./Paper_') { throw 'Dependance a un dossier source voisin' }
            $properties = Read-Properties (Join-Path $project.FullName 'gradle.properties')
            if ($project.Name -ne "Paper_$($properties['minecraft_version'])") { throw 'Nom de dossier incompatible avec minecraft_version' }
            if (-not $properties['version'] -or -not $properties['paper_version']) { throw 'Versions de compilation manquantes' }
            Add-Check $project.Name 'Structure autonome' $true 'Sources, configuration, tests et compilation locaux'

            $sources = Get-SourceMap $project.FullName
            if ($null -eq $reference) { $reference = $sources; $referenceName = $project.Name }
            else {
                $differences = @(@($reference.Keys) + @($sources.Keys) | Sort-Object -Unique | Where-Object {
                    -not $reference.ContainsKey($_) -or -not $sources.ContainsKey($_) -or $reference[$_] -ne $sources[$_]
                })
                Add-Check $project.Name 'Parite Paper' ($differences.Count -eq 0) $(if ($differences.Count -eq 0) { "Identique a $referenceName hors metadonnees de version" } else { $differences -join ', ' })
            }

            if ($Mode -eq 'Quick') { continue }
            $tasks = if ($Mode -eq 'Full') { @('build','smokeTest') } else { @('compileJava','compileTestJava','compileSmokeJava') }
            if ($Mode -eq 'Full') {
                foreach ($relative in "run-server/paper-$($properties['minecraft_version']).jar",'run-server/eula.txt') {
                    if (-not (Test-Path -LiteralPath (Join-Path $project.FullName $relative))) { throw "Prerequis serveur absent : $relative (voir validation/README.md)" }
                }
            }
            if ($RerunTasks) { $tasks += '--rerun-tasks' }
            $logPath = Join-Path $resultsDirectory "$($project.Name)-$Mode.log"
            Push-Location -LiteralPath $project.FullName
            try {
                & (Join-Path $project.FullName 'gradlew.bat') @tasks 2>&1 | Tee-Object -FilePath $logPath
                $buildExit = $LASTEXITCODE
            } finally { Pop-Location }
            if ($buildExit -ne 0) { throw "Gradle a echoue (code $buildExit), consulter $($project.Name)-$Mode.log" }
            Add-Check $project.Name 'Compilation' $true ($tasks -join ' ')
            if ($Mode -eq 'Full') {
                $testResults = @(Get-ChildItem -LiteralPath (Join-Path $project.FullName 'build/test-results/test') -Filter 'TEST-*.xml' -File)
                if ($testResults.Count -eq 0) { throw 'Aucun resultat de test unitaire' }
                $total = 0
                foreach ($testResult in $testResults) {
                    [xml]$suite = Get-Content -LiteralPath $testResult.FullName
                    if ([int]$suite.testsuite.failures -ne 0 -or [int]$suite.testsuite.errors -ne 0) { throw 'Echec des tests unitaires' }
                    $total += [int]$suite.testsuite.tests
                }
                if ($total -eq 0) { throw 'Suite unitaire vide' }
                Add-Check $project.Name 'Tests unitaires' $true "$total tests"
                $native = (Get-Content -LiteralPath (Join-Path $project.FullName 'build/smoke-server/smoke-result.txt') -Raw).Trim()
                if ($native -notmatch '^PASS: [1-9]\d* native checks$') { throw "Echec des tests serveur : $native" }
                Add-Check $project.Name 'Serveur natif' $true $native
                Test-Artifact $project $properties
            }
        } catch { Add-Check $project.Name 'Validation' $false $_.Exception.Message }
    }
} catch { Add-Check 'Workspace' 'Validation' $false $_.Exception.Message }

$failed = @($checks | Where-Object Status -EQ 'FAIL').Count
$summary = [pscustomobject]@{ GeneratedAt=[DateTimeOffset]::Now.ToString('o'); Mode=$Mode; Failed=$failed; Checks=@($checks.ToArray()) }
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resultsDirectory 'latest.json') -Encoding UTF8
$report = [System.Collections.Generic.List[string]]::new()
$report.Add('# Validation de CutCleanReborn')
$report.Add('')
$report.Add("Date : $($summary.GeneratedAt)  ")
$report.Add("Mode : $Mode  ")
$report.Add("Echecs : $failed")
$report.Add('')
$report.Add('| Projet | Controle | Etat | Detail |')
$report.Add('| --- | --- | --- | --- |')
foreach ($check in $checks) {
    $detail = $check.Detail.Replace('|', '/').Replace("`r", ' ').Replace("`n", ' ')
    $report.Add("| $($check.Project) | $($check.Check) | $($check.Status) | $detail |")
}
$report.Add('')
$report.Add('Les tests serveur simulent les interactions dans des mondes isoles. Ils ne prouvent pas le rendu visuel dans un client Minecraft.')
if ($Mode -ne 'Full') { $report.Add('Ce mode ne valide pas les interactions sur un serveur Minecraft. Utiliser Full pour la validation finale.') }
$report | Set-Content -LiteralPath (Join-Path $resultsDirectory 'latest.md') -Encoding UTF8
Write-Host "Rapport : $(Join-Path $resultsDirectory 'latest.md')"
if ($failed -gt 0) { exit 1 }
exit 0
