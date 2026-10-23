[CmdletBinding()]
param(
    [string[]]$Project,
    [ValidateRange(30, 600)][int]$PhaseTimeoutSeconds = 180,
    [string]$MavenRepository
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$workspace = Split-Path -Parent $PSScriptRoot
$helper = Join-Path $PSScriptRoot 'persistence'
. (Join-Path $PSScriptRoot 'evidence.ps1')
$attempt = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
$reportRoot = Join-Path $PSScriptRoot "results/persistence/$attempt"
$null = New-Item -ItemType Directory -Path $reportRoot
$results = [Collections.Generic.List[object]]::new()

function Read-Properties([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') { $values[$Matches[1]] = $Matches[2] }
    }
    return $values
}
function Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Get-HelperFingerprint {
    $paths = [Collections.Generic.List[string]]::new()
    foreach ($relative in 'settings.gradle','build.gradle','server.properties','README.md') { $paths.Add((Join-Path $helper $relative)) }
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $helper 'src') -File -Recurse) { $paths.Add($file.FullName) }
    $paths.Add($PSCommandPath)
    $paths.Add((Join-Path $PSScriptRoot 'evidence.ps1'))
    $lines = foreach ($path in $paths | Sort-Object) { "$($path.Substring($workspace.Length+1)) $(Hash $path)" }
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))).ToLowerInvariant()
}
function Select-Java([int]$Major) {
    $candidates = [Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin/java.exe')) }
    foreach ($jdk in Get-ChildItem -LiteralPath 'C:/Program Files/Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue) { $candidates.Add((Join-Path $jdk.FullName 'bin/java.exe')) }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) { $candidates.Add($command.Source) }
    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (!(Test-Path -LiteralPath $candidate)) { continue }
        $versionText = (& $candidate -version 2>&1 | Out-String)
        if ($versionText -match '(?:openjdk|java) version "(\d+)' -and [int]$Matches[1] -eq $Major) { return $candidate }
    }
    throw "JDK$Major absent"
}
function Assert-Unchanged([string]$Name, [string]$Source, [string]$Artifact, [string]$ArtifactHash, [string]$Server, [string]$ServerHash, [string]$HelperHash, [string]$HelperSource) {
    if ($Source -ne (Get-ProjectFingerprint $workspace $Name)) { throw 'Production sources changed during cold-restart verification' }
    if ($ArtifactHash -ne (Hash $Artifact)) { throw 'Production JAR changed during cold-restart verification' }
    if ($ServerHash -ne (Hash $Server)) { throw 'Server binary changed during cold-restart verification' }
    if ($HelperHash -ne (Hash (Join-Path $helper 'build/libs/CCRPersistence.jar'))) { throw 'Persistence verifier JAR changed during verification' }
    if ($HelperSource -ne (Get-HelperFingerprint)) { throw 'Persistence verifier sources/runner changed during verification' }
}
function Invoke-Phase([string]$Java, [string[]]$Arguments, [string]$Directory, [int]$Phase) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Java
    $start.WorkingDirectory = $Directory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.RedirectStandardInput = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    $timedOut = $false
    try {
        if (!$process.Start()) { throw 'Could not start isolated Java process' }
        $nativePid = $process.Id
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $deadline = [DateTime]::UtcNow.AddSeconds($PhaseTimeoutSeconds)
        while (!$process.WaitForExit(1000)) {
            if ([DateTime]::UtcNow -gt $deadline) {
                $timedOut = $true
                # Address only the exact process we own, requesting a clean stop first.
                $process.StandardInput.WriteLine('stop')
                $process.StandardInput.Flush()
                if (!$process.WaitForExit(20000)) { $process.Kill($true); $process.WaitForExit() }
                break
            }
        }
        [IO.File]::WriteAllText((Join-Path $Directory "phase-$Phase-console.log"),$stdout.GetAwaiter().GetResult(),[Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $Directory "phase-$Phase-stderr.log"),$stderr.GetAwaiter().GetResult(),[Text.UTF8Encoding]::new($false))
        if ($timedOut) { throw "Phase $Phase timed out; owned process stopped, no successful receipt written" }
        if ($process.ExitCode -ne 0) { throw "Phase $Phase exited with code $($process.ExitCode)" }
        return $nativePid
    } finally {
        if ($process.Id -and !$process.HasExited) {
            try { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() } catch { }
            if (!$process.WaitForExit(20000)) { $process.Kill($true); $process.WaitForExit() }
        }
        $process.Dispose()
    }
}

$selected = @(Get-ChildItem -LiteralPath $workspace -Directory | Where-Object Name -Match '^(Paper|Purpur|Spigot|Bukkit)_\d' | Sort-Object Name)
if ($Project) {
    foreach ($name in $Project) { if ($name -notin $selected.Name) { throw "Unknown server project $name" } }
    $selected = @($selected | Where-Object Name -In $Project)
}
if ($selected.Count -eq 0) { throw 'No server plugin selected' }
$helperSource = Get-HelperFingerprint
$wrapper = Join-Path $workspace 'Bukkit_1.21.1/gradlew.bat'
$buildArguments = @('--no-daemon','--console=plain')
if ($MavenRepository) { $buildArguments += "-Dmaven.repo.local=$((Resolve-Path -LiteralPath $MavenRepository).Path.Replace('\','/'))" }
Write-Host 'Building independent persistence verifier against Bukkit 1.21.1 API'
& $wrapper -p $helper @buildArguments jar *> (Join-Path $reportRoot 'helper-build.log')
if ($LASTEXITCODE -ne 0) { throw "Helper build failed; see $reportRoot/helper-build.log" }
$helperJar = Join-Path $helper 'build/libs/CCRPersistence.jar'
$helperHash = Hash $helperJar
if ($helperSource -ne (Get-HelperFingerprint)) { throw 'Verifier sources changed while compiling' }

foreach ($module in $selected) {
    $name = $module.Name
    $record = [ordered]@{ project=$name; status='FAIL'; attempt=$attempt; source_fingerprint=$null; artifact_sha256=$null; server_sha256=$null; helper_sha256=$helperHash; helper_source_fingerprint=$helperSource; stages=@(); detail=''; real_player_tests=$false }
    try {
        $properties = Read-Properties (Join-Path $module.FullName 'gradle.properties')
        $platform = $name.Split('_')[0]
        $minecraft = $properties['minecraft_version']
        $family = if ($properties.ContainsKey('minecraft_family')) { $properties['minecraft_family'] } else { $minecraft }
        $major = if ($properties.ContainsKey('java_version')) { [int]$properties['java_version'] } elseif ($minecraft.StartsWith('1.')) { 21 } else { 25 }
        $java = Select-Java $major
        $server = Join-Path $module.FullName "run-server/$($platform.ToLowerInvariant())-$minecraft.jar"
        $eula = Join-Path $module.FullName 'run-server/eula.txt'
        if (!(Test-Path -LiteralPath $server) -or !(Test-Path -LiteralPath $eula) -or (Get-Content -LiteralPath $eula -Raw) -notmatch '(?im)^\s*eula\s*=\s*true\s*$') { throw 'Official server and previously accepted EULA required; harness does not accept EULA' }
        $source = Get-ProjectFingerprint $workspace $name
        Write-Host "$name : building current production JAR before three cold JVMs"
        & (Join-Path $module.FullName 'gradlew.bat') -p $module.FullName @buildArguments jar *> (Join-Path $reportRoot "$name-build.log")
        if ($LASTEXITCODE -ne 0) { throw 'Production JAR build failed' }
        if ($source -ne (Get-ProjectFingerprint $workspace $name)) { throw 'Production source changed while compiling' }
        $artifact = Join-Path $module.FullName "build/libs/cutcleanreborn-$($properties['version'])+$family.jar"
        $artifactHash = Hash $artifact
        $serverHash = Hash $server
        $record.source_fingerprint = $source
        $record.artifact_sha256 = $artifactHash
        $record.server_sha256 = $serverHash
        $record.minecraft = $minecraft
        $record.java = $java
        $directory = Join-Path $module.FullName "build/persistence-server/$attempt"
        $null = New-Item -ItemType Directory -Path $directory
        $null = New-Item -ItemType Directory -Path (Join-Path $directory 'plugins')
        Copy-Item -LiteralPath $artifact -Destination (Join-Path $directory 'plugins/CutCleanReborn.jar')
        Copy-Item -LiteralPath $helperJar -Destination (Join-Path $directory 'plugins/CCRPersistence.jar')
        Copy-Item -LiteralPath $eula -Destination (Join-Path $directory 'eula.txt')
        Copy-Item -LiteralPath (Join-Path $helper 'server.properties') -Destination (Join-Path $directory 'server.properties')
        $record.server_directory = $directory
        $nonce = [guid]::NewGuid().ToString()
        $record.nonce = $nonce
        $pids = [Collections.Generic.List[long]]::new()
        $boots = [Collections.Generic.List[string]]::new()
        $entry = if ($platform -in 'Bukkit','Spigot') { 'org.bukkit.craftbukkit.bootstrap.Main' } else { 'io.papermc.paperclip.Main' }
        foreach ($phase in 1..3) {
            Assert-Unchanged $name $source $artifact $artifactHash $server $serverHash $helperHash $helperSource
            if ($artifactHash -ne (Hash (Join-Path $directory 'plugins/CutCleanReborn.jar'))) { throw 'Copied plugin changed between restarts' }
            if ($helperHash -ne (Hash (Join-Path $directory 'plugins/CCRPersistence.jar'))) { throw 'Copied verifier changed between restarts' }
            $arguments = @('-Xms256M','-Xmx1G','-Dterminal.jline=false','-Dterminal.ansi=false',"-Dccr.persistence.phase=$phase","-Dccr.persistence.nonce=$nonce",'-cp',$server,$entry,'--nogui','--port','0')
            Write-Host "$name : phase $phase/3, actual new JVM"
            $nativePid = Invoke-Phase $java $arguments $directory $phase
            $resultFile = Join-Path $directory "persistence-phase-$phase.json"
            if (!(Test-Path -LiteralPath $resultFile)) { throw "Missing helper result for phase $phase" }
            $phaseResult = Get-Content -LiteralPath $resultFile -Raw | ConvertFrom-Json
            if ($phaseResult.status -ne 'PASS') { throw "Phase $phase helper failed: $($phaseResult.error)" }
            if ($phaseResult.phase -ne $phase -or $phaseResult.nonce -ne $nonce -or $phaseResult.pid -ne $nativePid -or $phaseResult.pid -in $pids -or $phaseResult.boot_id -in $boots) { throw 'Phase identity/PID/nonce does not prove three distinct JVMs' }
            $pids.Add([long]$phaseResult.pid)
            $boots.Add($phaseResult.boot_id)
            $logPath = Join-Path $directory 'logs/latest.log'
            $log = Get-Content -LiteralPath $logPath -Raw
            $stopOffset = $log.LastIndexOf('Stopping server',[StringComparison]::Ordinal)
            if ($stopOffset -lt 0) { throw 'Journal does not prove a requested orderly shutdown' }
            $shutdown = $log.Substring($stopOffset)
            if ($shutdown -notmatch 'Disabling CCRPersistence' -or $shutdown -notmatch 'Disabling CutCleanReborn' -or $shutdown -notmatch 'Saving players' -or $shutdown -notmatch 'Saving worlds') { throw 'Journal does not prove ordered plugin disable and player/world save' }
            $shutdownEvidence = 'Final dimension/region-I/O completion marker after requested shutdown, exit0'
            if ($platform -in 'Bukkit','Spigot' -and $minecraft -eq '26.3') {
                # Official BuildTools26.3 MinecraftServer removed the old final flush
                # log line. stopServer still flushes with saveAllChunks(false,true,false),
                # then closes every level before returning. Match all dimension saves
                # after shutdown and require exit0 (checked above), never an earlier save.
                foreach ($dimension in 'overworld','the_nether','the_end') {
                    if ($shutdown -notmatch ('Saving chunks for level [^\r\n]*/minecraft:' + $dimension + '(?:[\r\n]|$)')) { throw "Missing $dimension shutdown save for official26.3 runtime" }
                }
                $shutdownEvidence = 'BuildTools26.3: ordered stop/disable/player/world/three-dimension saves and exit0; upstream no longer emits final flush line'
            } elseif ($shutdown -notmatch '(All dimensions are saved|All RegionFile I/O tasks to complete)') { throw 'Journal does not prove final world-save completion' }
            if ($log -match '(?im)(Error occurred while (enabling|disabling) (CutCleanReborn|CCRPersistence)|PERSISTENCE FAIL|Failed to save|Exception closing the level|Failed to unlock level|Error while shutting down|Exception in server tick loop|Encountered an unexpected exception|The server has stopped responding|Could not pass event .* to (CutCleanReborn|CCRPersistence))') { throw 'Plugin/server failure found in phase journal' }
            $runtime = if ($platform -in 'Bukkit','Spigot') {
                $brand = if ($platform -eq 'Spigot') { '(CraftBukkit|Spigot)' } else { 'CraftBukkit' }
                $revision = if ($platform -eq 'Spigot') { 'Spigot' } else { 'Bukkit' }
                'This server is running ' + $brand + ' version \S*-' + $revision + '-[^\r\n]*MC: ' + [regex]::Escape($minecraft) + '(?:[ )]| Unobfuscated)'
            } else { 'This server is running ' + $platform + ' version ' + [regex]::Escape($minecraft) + '[- ]' }
            if ($log -notmatch $runtime) { throw 'Phase journal has incorrect native platform/version' }
            Copy-Item -LiteralPath $logPath -Destination (Join-Path $directory "phase-$phase-server.log")
            Assert-Unchanged $name $source $artifact $artifactHash $server $serverHash $helperHash $helperSource
            $record.stages += [ordered]@{ phase=$phase; pid=$phaseResult.pid; boot_id=$phaseResult.boot_id; status='PASS'; checks=@($phaseResult.checks).Count; assertions=$phaseResult.checks; orderly_shutdown=$true; shutdown_evidence=$shutdownEvidence; log_sha256=(Hash (Join-Path $directory "phase-$phase-server.log")); result_sha256=(Hash $resultFile) }
        }
        $record.status = 'PASS'
        $record.detail = 'Three distinct native JVMs, two cold restarts, actual world/container/chunk/config persistence; real item despawn releases mace'
        Write-Host "PASS $name : $((@($record.stages | ForEach-Object checks) | Measure-Object -Sum).Sum) storage checks across three JVMs"
    } catch {
        $record.detail = $_.Exception.Message
        Write-Host "FAIL $name : $($record.detail)"
    }
    $results.Add([pscustomobject]$record)
    $record | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $reportRoot "$name.json") -Encoding utf8
}
$summary = [ordered]@{ attempt=$attempt; finished_utc=[DateTime]::UtcNow.ToString('o'); scope='Native plugin storage, not visual/player validation'; results=@($results) }
$summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $reportRoot 'summary.json') -Encoding utf8
$lines = [Collections.Generic.List[string]]::new()
$lines.Add('# Persistence through real cold restarts')
$lines.Add('')
$lines.Add("Attempt: $attempt. Three JVMs per project; two actual restarts. Synthetic UUID only for preference setter. No network player, craft payment or visual test implied.")
$lines.Add('')
$lines.Add('| Project | Result | Completed JVMs | Checks | Detail |')
$lines.Add('| --- | --- | --- | --- | --- |')
foreach ($result in $results) {
    $count = (@($result.stages | ForEach-Object checks) | Measure-Object -Sum).Sum
    $lines.Add("| $($result.project) | $($result.status) | $(@($result.stages).Count)/3 | $count | $($result.detail.Replace('|','/')) |")
}
$lines.Add('')
$lines.Add('Machine-readable receipts, exact hashes and per-assertion evidence are beside this report; native server worlds/logs remain in each module build/persistence-server attempt. No existing world/configuration was reused.')
$lines | Set-Content -LiteralPath (Join-Path $reportRoot 'summary.md') -Encoding utf8
Copy-Item -LiteralPath (Join-Path $reportRoot 'summary.md') -Destination (Join-Path $PSScriptRoot 'results/persistence/latest.md')
Copy-Item -LiteralPath (Join-Path $reportRoot 'summary.json') -Destination (Join-Path $PSScriptRoot 'results/persistence/latest.json')
Write-Host "Persistence report: $reportRoot/summary.md"
if (@($results | Where-Object status -EQ 'FAIL').Count -gt 0) { exit 1 }
exit 0
