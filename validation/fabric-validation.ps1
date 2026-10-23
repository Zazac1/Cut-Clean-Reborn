function Test-FabricArtifact([System.IO.DirectoryInfo]$Project, [hashtable]$Properties, [string]$Family) {
    $name="cutcleanreborn-$($Properties['mod_version'])+$Family.jar"
    $artifact=Join-Path $Project.FullName "build/libs/$name"
    $archive=[System.IO.Compression.ZipFile]::OpenRead($artifact)
    try {
        foreach ($required in 'fabric.mod.json','config.yml','LICENSE','cutcleanreborn.mixins.json',
            'fr/isaac/cutcleanreborn/fabric/CutCleanReborn.class') {
            if ($null -eq $archive.GetEntry($required)) { throw "Fabric artifact missing $required" }
        }
        $reader=[IO.StreamReader]::new($archive.GetEntry('fabric.mod.json').Open())
        try { $metadata=$reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($metadata.id -ne 'cutcleanreborn' -or $metadata.version -ne $Properties['mod_version']) { throw 'Wrong Fabric mod identity/version' }
        if ($metadata.depends.minecraft -ne $Properties['minecraft_version']) { throw 'Wrong declared Minecraft version' }
        if ($metadata.environment -ne '*') { throw 'Fabric must support both dedicated and integrated servers' }
        if (@($archive.Entries | Where-Object FullName -Match 'NativeChecks|ColdRestartChecks|ColdPersistenceChecks|/qamixin/|ccrverification').Count -gt 0) { throw 'QA code present in released Fabric mod' }
        if (@($archive.Entries | Where-Object FullName -Match '^META-INF/jars/snakeyaml-[^/]+\.jar$').Count -ne 1) { throw 'Standalone YAML dependency missing' }
        Add-Check $Project.Name 'JAR' $true $name
    } finally { $archive.Dispose() }
    return $artifact
}

function Invoke-FabricProjectValidation([System.IO.DirectoryInfo]$Project, [string]$SelectedMode, [bool]$ForceTasks) {
    $properties=Read-Properties (Join-Path $Project.FullName 'gradle.properties')
    $family=if ($properties.ContainsKey('minecraft_family')) { $properties['minecraft_family'] } else { $properties['minecraft_version'] }
    if ($Project.Name -ne "Fabric_$family") { throw 'Fabric folder/version mismatch' }
    foreach ($key in 'minecraft_version','mod_version','fabric_api_version','loader_version') {
        if (-not $properties[$key]) { throw "Missing Fabric property $key" }
    }
    foreach ($file in 'build.gradle','settings.gradle','README.md','LICENSE','gradlew.bat','gradlew','gradle/wrapper/gradle-wrapper.jar') {
        if (-not (Test-Path -LiteralPath (Join-Path $Project.FullName $file))) { throw "Fabric project missing $file" }
    }
    $source=if ($Project.Name -eq 'Fabric_1.21.1') { $Project.FullName } else { Join-Path $workspaceRoot 'platform-fabric-modern' }
    foreach ($file in 'src/main/resources/fabric.mod.json','src/main/resources/config.yml',
        'src/main/java/fr/isaac/cutcleanreborn/fabric/CutCleanReborn.java',
        'src/qa/java/fr/isaac/cutcleanreborn/fabric/NativeChecks.java') {
        if (-not (Test-Path -LiteralPath (Join-Path $source $file))) { throw "Fabric native source missing $file" }
    }
    Add-Check $Project.Name 'Structure Fabric' $true $(if ($source -eq $Project.FullName) { 'Native autonomous Fabric source' } else { 'Shared native Fabric source with explicit version adapters' })
    if ($SelectedMode -eq 'Quick') { return }
    $tasks=if ($SelectedMode -eq 'Full') { @('build','productionSmokeTest','productionPersistenceTest') } else { @('compileJava','compileTestJava','compileQaJava') }
    if ($ForceTasks) { $tasks += '--rerun-tasks' }
    $tasks += @('--console=plain','--max-workers=2')
    $initial=Get-ProjectFingerprint $workspaceRoot $Project.Name
    Push-Location -LiteralPath $Project.FullName
    try {
        & .\gradlew.bat @tasks 2>&1 | Tee-Object -FilePath (Join-Path $resultsDirectory "$($Project.Name)-$SelectedMode.log")
        $code=$LASTEXITCODE
    } finally { Pop-Location }
    if ($code -ne 0) { throw "Fabric Gradle failed ($code)" }
    Add-Check $Project.Name 'Compilation Fabric' $true ($tasks -join ' ')
    if ($SelectedMode -ne 'Full') { return }
    $unitFiles=@(Get-ChildItem -LiteralPath (Join-Path $Project.FullName 'build/test-results/test') -Filter 'TEST-*.xml')
    if ($unitFiles.Count -eq 0) { throw 'No Fabric unit test result' }
    $total=0
    foreach ($file in $unitFiles) {
        [xml]$suite=Get-Content -LiteralPath $file.FullName
        if ([int]$suite.testsuite.failures -ne 0 -or [int]$suite.testsuite.errors -ne 0) { throw 'Fabric unit test failure' }
        $total += [int]$suite.testsuite.tests
    }
    if ($total -lt 1) { throw 'Empty Fabric unit suite' }
    Add-Check $Project.Name 'Tests unitaires' $true "$total tests"
    $server=Join-Path $Project.FullName 'build/smoke-server/production'
    $native=Get-Content -LiteralPath (Join-Path $server 'ccr-qa-result.json') -Raw | ConvertFrom-Json
    if ($native.counts.failed -ne 0 -or $native.counts.passed -lt 1 -or @($native.failed).Count -ne 0) { throw 'Fabric release-JAR native check failed' }
    $nativeText="PASS: $($native.counts.passed) native checks"
    $log=Get-Content -LiteralPath (Join-Path $server 'logs/latest.log') -Raw
    $identity='Loading Minecraft '+[regex]::Escape($properties['minecraft_version'])+' with Fabric Loader '+[regex]::Escape($properties['loader_version'])+'(?:\s|$)'
    if ($log -notmatch $identity) { throw 'Fabric/Minecraft runtime identity unproven' }
    Add-Check $Project.Name 'Plateforme native' $true "Fabric $($properties['minecraft_version']) / Loader $($properties['loader_version']), actual release JAR outside Loom"
    Add-Check $Project.Name 'Serveur natif' $true $nativeText
    $artifact=Test-FabricArtifact $Project $properties $family
    $installed=Join-Path $server "mods/$(Split-Path -Leaf $artifact)"
    if ((Get-FileHash -LiteralPath $artifact).Hash -ne (Get-FileHash -LiteralPath $installed).Hash) { throw 'The tested Fabric JAR differs from the release JAR' }
    $coldServer=Join-Path $Project.FullName 'build/smoke-server/persistence'
    $cold=Get-Content -LiteralPath (Join-Path $coldServer 'ccr-persistence-result.json') -Raw | ConvertFrom-Json
    if ($cold.platform -ne 'Fabric' -or $cold.minecraft -ne $properties['minecraft_version'] -or
        $cold.counts.failed -ne 0 -or $cold.counts.passed -lt 1 -or
        @($cold.processIds | Sort-Object -Unique).Count -ne 3 -or @($cold.stages).Count -ne 3) { throw 'Three successful, distinct cold Fabric JVMs unproven' }
    foreach ($stage in $cold.stages) {
        if ($stage.counts.failed -ne 0 -or $stage.counts.passed -lt 1 -or -not $stage.cleanShutdown) { throw 'A Fabric cold restart stage failed or did not shut down cleanly' }
    }
    $nonces=@($cold.stages | ForEach-Object worldNonce | Where-Object {$_} | Sort-Object -Unique)
    if ($nonces.Count -ne 1 -or @($cold.stages | Where-Object {-not $_.worldNonce}).Count -ne 0) { throw 'Cold Fabric stages do not prove the same persisted world' }
    foreach ($stageName in 'write','read','reread') {
        $phaseLog=Get-Content -LiteralPath (Join-Path $coldServer "phase-$stageName.log") -Raw
        if ($phaseLog -notmatch 'Stopping server' -or $phaseLog -notmatch 'Saving players' -or
            $phaseLog -notmatch 'Saving worlds') { throw "Incomplete cold Fabric shutdown: $stageName" }
        foreach ($dimension in 'overworld','the_nether','the_end') {
            if ($phaseLog -notmatch ("Saving chunks[^\r\n]*minecraft:"+[regex]::Escape($dimension))) { throw "Unsaved cold Fabric dimension: $stageName / $dimension" }
        }
    }
    if ((Get-FileHash -LiteralPath $artifact).Hash -ne (Get-FileHash -LiteralPath (Join-Path $coldServer "mods/$(Split-Path -Leaf $artifact)")).Hash) { throw 'Cold-restart Fabric JAR differs from the release JAR' }
    Add-Check $Project.Name 'Redemarrages a froid' $true "$($cold.counts.passed) checks, three distinct JVMs"
    if ($initial -ne (Get-ProjectFingerprint $workspaceRoot $Project.Name)) { throw 'Fabric source changed during validation: rerun required' }
    $coldEvidence=[pscustomobject]@{status='PASS';checks=$cold.counts.passed;process_ids=@($cold.processIds);world_nonce=$nonces[0];stages=3}
    Write-ValidationEvidence $workspaceRoot $Project.Name 'Fabric' $properties['minecraft_version'] $artifact $total $nativeText
    $receiptPath=Join-Path $workspaceRoot "validation/results/evidence/$($Project.Name).json"
    $receipt=Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
    $receipt | Add-Member -NotePropertyName cold_restart -NotePropertyValue $coldEvidence
    $receipt | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $receiptPath -Encoding UTF8
}
