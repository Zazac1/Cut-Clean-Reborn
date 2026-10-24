[CmdletBinding()]
param(
    [ValidateSet('Quick', 'Compile', 'Full')]
    [string]$Mode = 'Full',
    [string[]]$Version,
    [ValidateSet('Paper', 'Purpur', 'Spigot', 'Bukkit', 'Fabric')]
    [string[]]$Platform = @('Paper', 'Purpur', 'Spigot', 'Bukkit', 'Fabric'),
    [switch]$RerunTasks
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$resultsDirectory = Join-Path $PSScriptRoot 'results'
$null = New-Item -ItemType Directory -Path $resultsDirectory -Force
$checks = [System.Collections.Generic.List[object]]::new()
. (Join-Path $PSScriptRoot 'evidence.ps1')
. (Join-Path $PSScriptRoot 'fabric-validation.ps1')

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
        if ($relative -in 'main/resources/plugin.yml','smoke/resources/plugin.yml') { continue }
        if ($relative -eq 'main/java/fr/isaac/cutcleanreborn/paper/AnvilTextInput.java') {
            # The old API opens a view through the player; newer Paper also exposes view.open().
            # Normalize only this documented equivalent operation, not the rest of the file.
            $text = [System.IO.File]::ReadAllText($file.FullName).Replace("`r`n", "`n").Replace('view.open();', 'player.openInventory(view);')
            $hasher = [System.Security.Cryptography.SHA256]::Create()
            try { $sources[$relative] = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($text))) }
            finally { $hasher.Dispose() }
        } else { $sources[$relative] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash }
    }
    foreach ($name in 'LICENSE') {
        $sources[$name] = (Get-FileHash -LiteralPath (Join-Path $ProjectDirectory $name) -Algorithm SHA256).Hash
    }
    return $sources
}

function Test-Artifact([System.IO.DirectoryInfo]$Project, [hashtable]$Properties) {
    $family = if ($Properties.ContainsKey('minecraft_family')) { $Properties['minecraft_family'] } else { $Properties['minecraft_version'] }
    $artifactName = "cutcleanreborn-$($Properties['version'])+$family.jar"
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
        if ($null -eq $archive.GetEntry('icon.png')) { throw 'Logo absent du JAR' }
        if (@($archive.Entries | Where-Object FullName -Match '^fr/isaac/cutcleanreborn/verification/').Count -ne 0) { throw 'Code de test present dans le JAR distribue' }
        $package = if ($Project.Name -match '^(Spigot|Bukkit)_') { 'bukkit' } else { 'paper' }
        if ($null -eq $archive.GetEntry("fr/isaac/cutcleanreborn/$package/CutCleanRebornPlugin.class")) { throw 'Classe principale absente' }
        Add-Check $Project.Name 'JAR' $true $artifactName
    } finally { $archive.Dispose() }
}

try {
    & (Join-Path $PSScriptRoot 'test-ui-copy.ps1')
    Add-Check 'Workspace' 'UI Copy' $true 'English player-facing text, concise navigation and no redundant Fabric page counter'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $projects = @(Get-ChildItem -LiteralPath $workspaceRoot -Directory | Where-Object {
        $_.Name -match '^(Paper|Purpur|Spigot|Bukkit|Fabric)_\d' -and ($_.Name.Split('_')[0] -in $Platform)
    } | Sort-Object Name)
    if ($Version) {
        foreach ($requested in $Version) {
            if ($requested -notin $projects.Name) { throw "Projet inconnu : $requested" }
        }
        $projects = @($projects | Where-Object { $_.Name -in $Version })
    }
    if ($projects.Count -eq 0) { throw 'Aucun projet trouve pour les plateformes demandees' }

    $reference = $null
    $referenceName = ''
    $portableReference = $null
    $portableReferenceName = ''
    foreach ($project in $projects) {
        try {
            if ($project.Name.StartsWith('Fabric_')) {
                Invoke-FabricProjectValidation $project $Mode ([bool]$RerunTasks)
                continue
            }
            $loader = $project.Name.Split('_')[0]
            $isPurpur = $loader -eq 'Purpur'
            $isPortable = $loader -in 'Spigot','Bukkit'
            $sourcePackage = if ($isPortable) { 'bukkit' } else { 'paper' }
            $required = 'build.gradle','gradle.properties','settings.gradle','gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar',
                'LICENSE','README.md'
            $sourceRequired = 'src/main/resources/plugin.yml','src/main/resources/config.yml',
                "src/main/java/fr/isaac/cutcleanreborn/$sourcePackage/CutCleanRebornPlugin.java",
                "src/test/java/fr/isaac/cutcleanreborn/$sourcePackage/GameplayRegressionTest.java",
                'src/smoke/java/fr/isaac/cutcleanreborn/verification/CCRSmoke.java','src/smoke/server.properties'
            foreach ($relative in $required) {
                if (-not (Test-Path -LiteralPath (Join-Path $project.FullName $relative))) { throw "Fichier requis absent : $relative" }
            }
            $buildScript = Get-Content -LiteralPath (Join-Path $project.FullName 'build.gradle') -Raw
            if (-not $isPurpur -and $buildScript -match 'paper-common|\.\./Paper_') { throw 'Dependance a un dossier source voisin' }
            $properties = Read-Properties (Join-Path $project.FullName 'gradle.properties')
            $family = if ($properties.ContainsKey('minecraft_family')) { $properties['minecraft_family'] } else { $properties['minecraft_version'] }
            if ($project.Name -ne "${loader}_$family") { throw 'Nom de dossier incompatible avec la famille Minecraft' }
            $apiKey = if ($isPortable) { 'bukkit_version' } else { 'paper_version' }
            if (-not $properties['version'] -or -not $properties[$apiKey]) { throw 'Versions de compilation manquantes' }
            $sourceDirectory = $project.FullName
            if ($buildScript -match '\.\./(?:platform-|build-logic|Paper_)') { throw 'Le projet depend encore de fichiers hors de son dossier' }
            if ($isPortable) {
                if ($buildScript -match 'io.papermc.paper:paper-api') { throw 'Le port portable depend de Paper' }
                if ($properties['server_platform'] -ne $loader.ToLowerInvariant()) { throw 'Plateforme serveur incoherente' }
            }
            if ($isPurpur) {
                $localSources = Get-SourceMap $project.FullName
                $paperSources = Get-SourceMap (Join-Path $workspaceRoot "Paper_$family")
                # Keep each module's existing copyright notice; compare all actual sources/resources.
                $localSources.Remove('LICENSE')
                $paperSources.Remove('LICENSE')
                $differences = @(@($paperSources.Keys) + @($localSources.Keys) | Sort-Object -Unique | Where-Object {
                    -not $paperSources.ContainsKey($_) -or -not $localSources.ContainsKey($_) -or $paperSources[$_] -ne $localSources[$_]
                })
                Add-Check $project.Name 'Parite Purpur' ($differences.Count -eq 0) $(if ($differences.Count -eq 0) { "Sources locales identiques a Paper_$family" } else { $differences -join ', ' })
            }
            foreach ($relative in $sourceRequired) {
                if (-not (Test-Path -LiteralPath (Join-Path $sourceDirectory $relative))) { throw "Source requise absente : $relative" }
            }
            Add-Check $project.Name 'Structure' $true 'Sources, configuration, tests et compilation dans le dossier de version'
            if ($isPortable) {
                $portableSources = Get-SourceMap $project.FullName
                if ($null -eq $portableReference) { $portableReference=$portableSources; $portableReferenceName=$project.Name }
                else {
                    $differences = @(@($portableReference.Keys) + @($portableSources.Keys) | Sort-Object -Unique | Where-Object {
                        -not $portableReference.ContainsKey($_) -or -not $portableSources.ContainsKey($_) -or $portableReference[$_] -ne $portableSources[$_]
                    })
                    Add-Check $project.Name 'Parite Bukkit-Spigot' ($differences.Count -eq 0) $(if ($differences.Count -eq 0) { "Sources locales identiques a $portableReferenceName" } else { $differences -join ', ' })
                }
            }

            if (-not $isPortable) { $sources = Get-SourceMap $sourceDirectory }
            if ($loader -eq 'Paper' -and $null -eq $reference) { $reference = $sources; $referenceName = $project.Name }
            elseif ($loader -eq 'Paper') {
                $differences = @(@($reference.Keys) + @($sources.Keys) | Sort-Object -Unique | Where-Object {
                    -not $reference.ContainsKey($_) -or -not $sources.ContainsKey($_) -or $reference[$_] -ne $sources[$_]
                })
                Add-Check $project.Name 'Parite Paper' ($differences.Count -eq 0) $(if ($differences.Count -eq 0) { "Identique a $referenceName hors metadonnees de version" } else { $differences -join ', ' })
            }

            if ($Mode -eq 'Quick') { continue }
            $tasks = if ($Mode -eq 'Full') { @('build','smokeTest') } else { @('compileJava','compileTestJava','compileSmokeJava') }
            if ($Mode -eq 'Full') {
                foreach ($relative in "run-server/$($loader.ToLowerInvariant())-$($properties['minecraft_version']).jar",'run-server/eula.txt') {
                    if (-not (Test-Path -LiteralPath (Join-Path $project.FullName $relative))) { throw "Prerequis serveur absent : $relative (voir validation/README.md)" }
                }
            }
            if ($RerunTasks) { $tasks += '--rerun-tasks' }
            $logPath = Join-Path $resultsDirectory "$($project.Name)-$Mode.log"
            $initialFingerprint = Get-ProjectFingerprint $workspaceRoot $project.Name
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
                $serverLog = Get-Content -LiteralPath (Join-Path $project.FullName 'build/smoke-server/logs/latest.log') -Raw
                $expectedRuntime = if ($isPortable) {
                    $serverBrand = if ($loader -eq 'Spigot') { '(?:CraftBukkit|Spigot)' } else { 'CraftBukkit' }
                    $revisionBrand = if ($loader -eq 'Spigot') { 'Spigot' } else { 'Bukkit' }
                    'This server is running ' + $serverBrand + ' version \S*-' + $revisionBrand + '-[^\r\n]*MC: ' + [regex]::Escape($properties['minecraft_version']) + '(?:[ )]| Unobfuscated)'
                } else { 'This server is running ' + $loader + ' version ' + [regex]::Escape($properties['minecraft_version']) + '[- ]' }
                if ($serverLog -notmatch $expectedRuntime) { throw "Le journal ne prouve pas l'execution du serveur $loader demande" }
                $expectedPlugin = 'Enabling CutCleanReborn v' + [regex]::Escape($properties['version']) + '(?:[\s]|$)'
                if ($serverLog -notmatch $expectedPlugin) { throw 'Le serveur de test a charge une autre version du plugin' }
                Add-Check $project.Name 'Version chargee' $true $properties['version']
                Add-Check $project.Name 'Plateforme native' $true "$loader $($properties['minecraft_version']) confirme dans le journal"
                Test-Artifact $project $properties
                $artifact = Join-Path $project.FullName "build/libs/cutcleanreborn-$($properties['version'])+$family.jar"
                if ($initialFingerprint -ne (Get-ProjectFingerprint $workspaceRoot $project.Name)) { throw 'Sources modifiees pendant la validation : relancer les tests' }
                Write-ValidationEvidence $workspaceRoot $project.Name $loader $properties['minecraft_version'] $artifact $total $native
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
