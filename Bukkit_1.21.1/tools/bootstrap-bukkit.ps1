#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$ProjectDirectory = (Split-Path -Parent $PSScriptRoot),
    [string]$JavaExecutable,
    [string]$BuildToolsJar,
    [switch]$BuildPlugin,
    [switch]$PrepareServer
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath $ProjectDirectory).Path
$propertiesFile = Join-Path $projectRoot 'gradle.properties'
if (!(Test-Path -LiteralPath $propertiesFile -PathType Leaf)) { throw 'Choose a standalone Bukkit/Spigot module with -ProjectDirectory.' }
$properties = @{}
foreach ($line in [IO.File]::ReadAllLines($propertiesFile)) {
    if ($line -match '^\s*([^#!][^=]*)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2].Trim() }
}
$minecraftVersion = $properties['minecraft_version']
$apiVersion = $properties['bukkit_version']
$serverPlatform = $properties['server_platform']
$javaMajor = [int]$properties['java_version']
if ($minecraftVersion -notmatch '^\d+(\.\d+){1,2}$' -or $serverPlatform -notin @('bukkit','spigot') -or $javaMajor -notin @(21,25)) {
    throw 'Unsupported module properties. Expected an explicit Minecraft revision, Bukkit/Spigot and Java21/25.'
}
if ($apiVersion -notmatch ('^' + [regex]::Escape($minecraftVersion) + '-R\d+\.\d+-SNAPSHOT$')) { throw 'The declared Bukkit API must match Minecraft.' }
if ($projectRoot -match '[\s!"''`*?\[\]]') { throw 'BuildTools requires a checkout path without spaces, quotes, wildcards or exclamation marks.' }

$windowsHost = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
$javaName = if ($windowsHost) { 'java.exe' } else { 'java' }
$javacName = if ($windowsHost) { 'javac.exe' } else { 'javac' }
$candidates = [Collections.Generic.List[string]]::new()
if ($JavaExecutable) { $candidates.Add((Resolve-Path -LiteralPath $JavaExecutable).Path) }
else {
    if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME ('bin/' + $javaName))) }
    $onPath = Get-Command $javaName -ErrorAction SilentlyContinue
    if ($onPath) { $candidates.Add($onPath.Source) }
    if ($windowsHost -and (Test-Path -LiteralPath 'C:\Program Files\Eclipse Adoptium')) {
        Get-ChildItem -LiteralPath 'C:\Program Files\Eclipse Adoptium' -Directory | Where-Object Name -Like ('jdk-' + $javaMajor + '.*') | Sort-Object Name -Descending | ForEach-Object { $candidates.Add((Join-Path $_.FullName ('bin/' + $javaName))) }
    }
}
$selectedJava = $null
foreach ($candidate in ($candidates | Select-Object -Unique)) {
    if (!(Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
    $javac = Join-Path (Split-Path -Parent $candidate) $javacName
    if (!(Test-Path -LiteralPath $javac -PathType Leaf)) { continue }
    $versionText = (& $candidate -version 2>&1 | Out-String)
    if ($LASTEXITCODE -eq 0 -and $versionText -match ('version "' + $javaMajor + '[.\-"]')) { $selectedJava = $candidate; break }
}
if (!$selectedJava) { throw "Install a Java $javaMajor JDK, or pass -JavaExecutable with its bin/java path." }
if (!$windowsHost -and !(Get-Command git -ErrorAction SilentlyContinue)) { throw 'Install Git before running official BuildTools.' }

# Logs/dependencies stay under build/. Upstream source uses a short, dedicated temp cache:
# BuildTools invokes cmd.exe on Windows and cannot execute its PortableGit extractor beyond MAX_PATH.
$workRoot = Join-Path $projectRoot 'build/official-buildtools'
$cacheIdentity = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($projectRoot + '|' + $minecraftVersion))).Substring(0,12).ToLowerInvariant()
$sourceRoot = Join-Path ([IO.Path]::GetTempPath()) ('ccr-bukkit-' + $cacheIdentity)
if ($sourceRoot -match '\s') { throw 'BuildTools needs a TEMP directory without spaces. Choose a suitable process-local TEMP directory before bootstrapping.' }
$mavenRepository = Join-Path $projectRoot 'build/official-maven'
$gradleUserDirectory = Join-Path $projectRoot 'build/official-gradle'
foreach ($directory in @($workRoot,$sourceRoot,$mavenRepository,$gradleUserDirectory)) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
$lock = [IO.File]::Open((Join-Path $workRoot 'bootstrap.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$previousMavenOptions = [Environment]::GetEnvironmentVariable('MAVEN_OPTS','Process')
$buildToolsHash = '81ed06f5ce5fb705b558bf9b3cc2c5c53cdd9742406c5338c452faa13a1e1c24'
$buildToolsUrl = 'https://hub.spigotmc.org/jenkins/job/BuildTools/201/artifact/target/BuildTools.jar'
try {
    if ($BuildToolsJar) { $officialTool = (Resolve-Path -LiteralPath $BuildToolsJar).Path }
    else {
        $officialTool = Join-Path $workRoot 'BuildTools-201.jar'
        if (!(Test-Path -LiteralPath $officialTool)) {
            $partialTool = $officialTool + '.download-' + [guid]::NewGuid().ToString('N')
            Invoke-WebRequest -Uri $buildToolsUrl -OutFile $partialTool
            if ((Get-FileHash -LiteralPath $partialTool -Algorithm SHA256).Hash.ToLowerInvariant() -ne $buildToolsHash) { throw 'Official BuildTools checksum mismatch; nothing was executed.' }
            Move-Item -LiteralPath $partialTool -Destination $officialTool
        }
    }
    if ((Get-FileHash -LiteralPath $officialTool -Algorithm SHA256).Hash.ToLowerInvariant() -ne $buildToolsHash) { throw 'BuildTools checksum mismatch. Use the pinned official build201; do not bypass this check.' }

    $repositoryArgument = '-Dmaven.repo.local=' + $mavenRepository.Replace('\','/')
    [Environment]::SetEnvironmentVariable('MAVEN_OPTS',($previousMavenOptions + ' -Xmx2G ' + $repositoryArgument).Trim(),'Process')
    Write-Host "Building official Bukkit API $apiVersion with BuildTools201 / Java$javaMajor. First run can take 10-20 minutes."
    Write-Host 'Generated upstream binaries remain private in the dedicated temp cache. No server is started and no EULA is accepted.'
    Push-Location $sourceRoot
    try {
        & $selectedJava -Xms256M -Xmx2G -jar $officialTool --nogui --rev $minecraftVersion --compile SPIGOT,CRAFTBUKKIT *> (Join-Path $workRoot 'buildtools-output.log')
        if ($LASTEXITCODE -ne 0) { throw ('BuildTools failed. See ' + (Join-Path $workRoot 'buildtools-output.log')) }
    } finally { Pop-Location }
    $apiDirectory = Join-Path $mavenRepository ('org/bukkit/bukkit/' + $apiVersion)
    $apiJar = Join-Path $apiDirectory ('bukkit-' + $apiVersion + '.jar')
    $apiPom = Join-Path $apiDirectory ('bukkit-' + $apiVersion + '.pom')
    if (!(Test-Path -LiteralPath $apiJar -PathType Leaf) -or !(Test-Path -LiteralPath $apiPom -PathType Leaf)) {
        throw "BuildTools did not install the declared API $apiVersion in the private repository. Inspect the official Bukkit pom and update the module only after checking compatibility."
    }

    if ($PrepareServer) {
        $serverKind = if ($serverPlatform -eq 'bukkit') { 'craftbukkit' } else { 'spigot' }
        $builtServer = Join-Path $sourceRoot ($serverKind + '-' + $minecraftVersion + '.jar')
        if (!(Test-Path -LiteralPath $builtServer -PathType Leaf)) { throw 'The selected official server was not built.' }
        $runDirectory = Join-Path $projectRoot 'run-server'
        New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
        $targetServer = Join-Path $runDirectory ($serverPlatform + '-' + $minecraftVersion + '.jar')
        if (Test-Path -LiteralPath $targetServer) {
            if ((Get-FileHash -LiteralPath $targetServer).Hash -ne (Get-FileHash -LiteralPath $builtServer).Hash) { throw 'An existing server JAR differs. It was preserved; choose a fresh checkout for the bootstrap test.' }
        } else { Copy-Item -LiteralPath $builtServer -Destination $targetServer }
    }

    if ($BuildPlugin) {
        $wrapper = Join-Path $projectRoot $(if ($windowsHost) { 'gradlew.bat' } else { 'gradlew' })
        Push-Location $projectRoot
        try {
            & $wrapper $repositoryArgument --gradle-user-home $gradleUserDirectory --no-daemon --console=plain --init-script (Join-Path $PSScriptRoot 'verify-private-maven.init.gradle') build smokePluginJar verifyPrivateBukkitRepository *> (Join-Path $workRoot 'plugin-build.log')
            if ($LASTEXITCODE -ne 0) { throw ('Plugin build failed. See ' + (Join-Path $workRoot 'plugin-build.log')) }
        } finally { Pop-Location }
    }
    $summary = [ordered]@{
        minecraft = $minecraftVersion; platform = $serverPlatform; bukkitApi = $apiVersion; java = $selectedJava
        buildToolsUrl = $buildToolsUrl; buildToolsSha256 = $buildToolsHash
        mavenRepository = $mavenRepository; bukkitApiSha256 = (Get-FileHash -LiteralPath $apiJar -Algorithm SHA256).Hash.ToLowerInvariant()
        gradleUserHome = $gradleUserDirectory; pluginBuilt = [bool]$BuildPlugin; serverPrepared = [bool]$PrepareServer
        sourceCache = $sourceRoot
        serverStarted = $false; eulaAccepted = $false; finishedUtc = [DateTime]::UtcNow.ToString('o')
    }
    [IO.File]::WriteAllText((Join-Path $workRoot 'bootstrap-result.json'),($summary | ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
    Write-Host "PASS: official API is installed in $mavenRepository"
    Write-Host "For later Gradle commands, pass `"$repositoryArgument`" to keep using this private repository."
    Write-Host 'Server test tasks additionally require an eula.txt accepted by the user after reading the Minecraft EULA.'
    [PSCustomObject]$summary
} finally {
    [Environment]::SetEnvironmentVariable('MAVEN_OPTS',$previousMavenOptions,'Process')
    $lock.Dispose()
}
