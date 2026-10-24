[CmdletBinding()]
param([switch]$RequireComplete)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
. (Join-Path $workspace 'validation/evidence.ps1')
. (Join-Path $workspace 'validation/client-evidence.ps1')
$output = Join-Path $workspace 'dist'
$null = New-Item -ItemType Directory -Path $output -Force
$rows = [System.Collections.Generic.List[object]]::new()
$pluginCold=@()
$coldSummary=Join-Path $workspace 'validation/results/persistence/latest.json'
if (Test-Path -LiteralPath $coldSummary) {
    $pluginCold=@((Get-Content -LiteralPath $coldSummary -Raw | ConvertFrom-Json).results)
}

foreach ($platform in 'Paper','Purpur','Spigot','Bukkit','Fabric') {
    foreach ($family in '26.3','26.2','26.1.x','1.21.11','1.21.1') {
        $project = "${platform}_$family"
        $module = Join-Path $workspace $project
        $row = [ordered]@{
            project=$project; platform=$platform; family=$family; minecraft=''
            state='A faire'; build='Non verifiee'; startup='Non verifie'; unit_tests=0
            native_checks='Non verifiees'; cold_restart='A faire'; real_player='A faire'
            real_player_scenarios=@(); artifact=''; sha256=''; detail=''
        }
        try {
            if (-not (Test-Path -LiteralPath (Join-Path $module 'gradle.properties'))) {
                $row.detail='Module non cree'; $rows.Add([pscustomobject]$row); continue
            }
            $row.state='En cours'
            $properties=@{}
            foreach ($line in Get-Content -LiteralPath (Join-Path $module 'gradle.properties')) {
                if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') { $properties[$Matches[1]]=$Matches[2] }
            }
            $row.minecraft=$properties['minecraft_version']
            if ($platform -eq 'Fabric') { $properties['version']=$properties['mod_version'] }
            $receiptPath=Join-Path $workspace "validation/results/evidence/$project.json"
            if (-not (Test-Path -LiteralPath $receiptPath)) { throw 'Validation native complete et signee par empreinte encore absente' }
            $receipt=Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
            if ($receipt.schema -ne 1 -or $receipt.project -ne $project -or $receipt.platform -ne $platform -or $receipt.minecraft -ne $row.minecraft) { throw 'Preuve incompatible avec ce module' }
            $expectedName="cutcleanreborn-$($properties['version'])+$family.jar"
            if ($receipt.artifact -ne $expectedName) { throw 'Nom du JAR valide inattendu' }
            $artifact=Join-Path $module "build/libs/$expectedName"
            $hash=(Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($receipt.artifact_sha256 -ne $hash) { throw 'Le JAR a change depuis les tests : relancer la validation' }
            if ($receipt.source_fingerprint -ne (Get-ProjectFingerprint $workspace $project)) { throw 'Les sources ont change depuis les tests : relancer la validation' }
            if ($receipt.unit_tests -lt 1 -or $receipt.native_result -notmatch '^PASS: [1-9]\d* native checks$') { throw 'Tests unitaires ou natifs incomplets' }
            $row.build='PASS'; $row.startup='PASS'; $row.unit_tests=$receipt.unit_tests
            $row.native_checks=$receipt.native_result; $row.state='Valide automatiquement'
            $row.detail="Validation $($receipt.verified_at) ; interactions natives simulees, pas un joueur reel"
            if ($platform -eq 'Fabric') {
                if ($receipt.PSObject.Properties.Name -contains 'cold_restart' -and $null -ne $receipt.cold_restart) {
                    $cold=$receipt.cold_restart
                    if ($cold.status -eq 'PASS' -and $cold.checks -gt 0 -and $cold.stages -eq 3 -and
                        @($cold.process_ids | Sort-Object -Unique).Count -eq 3 -and $cold.world_nonce) {
                        $row.cold_restart="PASS: $($cold.checks) checks / 3 JVM"
                    }
                }
            } else {
                $cold=@($pluginCold | Where-Object {$_.project -eq $project -and $_.status -eq 'PASS' -and
                    $_.artifact_sha256 -eq $hash -and $_.source_fingerprint -eq $receipt.source_fingerprint})
                if ($cold.Count -eq 1 -and @($cold[0].stages).Count -eq 3 -and
                    @($cold[0].stages.pid | Sort-Object -Unique).Count -eq 3 -and
                    @($cold[0].stages | Where-Object {-not $_.orderly_shutdown -or $_.status -ne 'PASS'}).Count -eq 0) {
                    $storageChecks=($cold[0].stages | Measure-Object -Property checks -Sum).Sum
                    $row.cold_restart="PASS: $storageChecks checks / 3 JVM"
                }
            }
            $manualPath=Join-Path $workspace "validation/manual/$project.json"
            if (Test-Path -LiteralPath $manualPath) {
                try {
                    $manual=Get-Content -LiteralPath $manualPath -Raw | ConvertFrom-Json
                    $clientProof=Test-ClientEvidence $manual $project $platform $row.minecraft $hash $receipt.source_fingerprint
                    $row.real_player_scenarios=$clientProof.scenarios
                    $row.real_player="$($clientProof.status): $($clientProof.passed)/$($clientProof.required) scenarios"
                    $row.detail += '; en jeu : ' + $clientProof.detail
                    if ($clientProof.status -eq 'PASS') { $row.state='Valide avec joueur' }
                } catch {
                    # A malformed manual receipt must not hide a native-verified JAR.
                    $row.real_player='INVALID'; $row.detail += '; recu client illisible'
                }
            }
            $relative="$platform/$family/$expectedName"
            $destination=Join-Path $output $relative
            $null=New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force
            # Preserve older builds instead of silently overwriting a different binary.
            if ((Test-Path -LiteralPath $destination) -and (Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant() -ne $hash) {
                $oldHash=(Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant()
                $archive=Join-Path $output "archive/$platform/$family/$oldHash"
                $null=New-Item -ItemType Directory -Path $archive -Force
                Move-Item -LiteralPath $destination -Destination (Join-Path $archive $expectedName)
            }
            Copy-Item -LiteralPath $artifact -Destination $destination -Force
            $row.artifact=$relative; $row.sha256=$hash
        } catch { $row.detail=$_.Exception.Message }
        $rows.Add([pscustomobject]$row)
    }
}

$summary=[pscustomobject]@{ generated_at=[DateTimeOffset]::Now.ToString('o'); rows=$rows.ToArray() }
$summary | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $output 'compatibility.json') -Encoding UTF8
$report=[System.Collections.Generic.List[string]]::new()
$report.Add('# CutCleanReborn — livrables et compatibilite')
$report.Add('')
$report.Add('Seuls les JAR dont les empreintes correspondent aux tests natifs sont copies. PASS serveur ne signifie pas PASS avec joueur. Les anciennes copies eventuelles ne font pas foi : consulter ce manifeste.')
$report.Add('')
$report.Add('| Plateforme | Famille / revision testee | Etat | Build | Demarrage | Tests natifs | Redemarrages a froid | Joueur reel | JAR |')
$report.Add('| --- | --- | --- | --- | --- | --- | --- | --- | --- |')
foreach ($row in $rows) {
    $link=if ($row.artifact) { "[$(Split-Path -Leaf $row.artifact)]($($row.artifact))" } else { 'Pas de livrable valide' }
    $report.Add("| $($row.platform) | $($row.family) / $($row.minecraft) | $($row.state) | $($row.build) | $($row.startup) | $($row.native_checks) | $($row.cold_restart) | $($row.real_player) | $link |")
}
$report.Add('')
$report.Add('## Details')
$report.Add('')
foreach ($row in $rows) { $report.Add("- $($row.project) : $($row.detail)") }
$report.Add('')
$report.Add('## Observations avec joueur')
$report.Add('')
$report.Add('Un scenario partiel ne valide pas toute sa famille. Les observations ci-dessous proviennent uniquement des recus client ; leur validation ne peut pas etre deduite des tests natifs.')
foreach ($row in $rows) {
    foreach ($scenario in $row.real_player_scenarios) {
        $report.Add("- $($row.project) / $($scenario.id) [$($scenario.status)] : $($scenario.observation)")
    }
}
$report.Add('')
$report.Add('Aucune publication Modrinth. Les binaires des serveurs Minecraft et les profils de test personnels ne sont pas redistribues.')
$report | Set-Content -LiteralPath (Join-Path $output 'README.md') -Encoding UTF8
$complete=@($rows | Where-Object { $_.state -eq 'Valide avec joueur' -and $_.cold_restart.StartsWith('PASS:') }).Count
Write-Host "Livrables : $output ; $(@($rows | Where-Object artifact -NE '').Count)/25 JAR verifies, $complete/25 tests joueur complets."
if ($RequireComplete -and $complete -ne 25) { exit 1 }
exit 0
