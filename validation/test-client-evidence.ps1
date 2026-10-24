Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'client-evidence.ps1')
$script:checks=0
$artifactHash='a' * 64
$sourceHash='b' * 64

function New-TestReceipt([string]$Platform='Paper') {
    @{
        schema=2;project="${Platform}_26.3";platform=$Platform;minecraft='26.3'
        artifact_sha256=$artifactHash;source_fingerprint=$sourceHash
        observed_at='2026-10-24T01:50:00+02:00'
        client=@{minecraft='26.3';profile='Synthetic regression fixture';mode='dedicated'}
        scenarios=@()
    }
}

function New-TestScenario([string]$Id, [string]$Status='PASS') {
    @{id=$Id;status=$Status;observation='Synthetic fixture, not a real-player observation'}
}

function Assert-Status($Receipt, [string]$Expected, [string]$Label, [string]$Platform='Paper') {
    $actual=Test-ClientEvidence $Receipt "${Platform}_26.3" $Platform '26.3' $artifactHash $sourceHash
    if ($actual.status -cne $Expected) { throw "$Label : expected $Expected, got $($actual.status): $($actual.detail)" }
    $script:checks++
    Write-Host "PASS $Label"
    return $actual
}

$null=Assert-Status $null 'INVALID' 'missing receipt'
$receipt=New-TestReceipt
$receipt.schema=1;$receipt.result='PASS';$receipt.scenarios=@('menus')
$null=Assert-Status $receipt 'INVALID' 'old single-string global PASS is not complete'
$receipt=New-TestReceipt
$null=Assert-Status $receipt 'PARTIAL' 'empty observations'
$receipt.scenarios=@(New-TestScenario 'menus_commands')
$result=Assert-Status $receipt 'PARTIAL' 'one passing family is not complete'
if ($result.passed -ne 1 -or $result.required -ne 10 -or $result.observed -ne 1) { throw 'Incorrect partial coverage counts' }
$receipt.scenarios=@(New-TestScenario 'timber' 'PARTIAL')
$result=Assert-Status $receipt 'PARTIAL' 'partial mining observation is not a family PASS'
if ($result.passed -ne 0 -or $result.observed -ne 1) { throw 'Partial observation counted as PASS' }

foreach ($platform in 'Paper','Purpur','Spigot','Bukkit') {
    $receipt=New-TestReceipt $platform
    $receipt.scenarios=@(Get-RequiredClientScenarios $platform | ForEach-Object {New-TestScenario $_})
    $result=Assert-Status $receipt 'PASS' "all required families $platform" $platform
    if ($result.passed -ne 10) { throw 'Incorrect complete coverage count' }
}
$receipt=New-TestReceipt
$receipt.scenarios=@(Get-RequiredClientScenarios 'Paper' | ForEach-Object {New-TestScenario $_})
$receipt.scenarios[0].status='FAIL'
$null=Assert-Status $receipt 'FAIL' 'observed failure overrides all other successes'
$receipt.scenarios[0].status='PARTIAL'
$null=Assert-Status $receipt 'PARTIAL' 'one partial family prevents complete result'

foreach ($field in 'project','platform','minecraft') {
    $receipt=New-TestReceipt;$receipt[$field]='wrong'
    $null=Assert-Status $receipt 'INVALID' "wrong context $field"
}
foreach ($field in 'artifact_sha256','source_fingerprint') {
    $receipt=New-TestReceipt;$receipt[$field]='c' * 64
    $null=Assert-Status $receipt 'STALE' "changed $field"
    $receipt[$field]='invalid'
    $null=Assert-Status $receipt 'STALE' "malformed $field"
}
foreach ($field in 'profile','minecraft','mode') {
    $receipt=New-TestReceipt;$receipt.client[$field]=''
    $null=Assert-Status $receipt 'INVALID' "missing client $field"
}
$receipt=New-TestReceipt;$receipt.client.mode='singleplayer'
$null=Assert-Status $receipt 'INVALID' 'plugin cannot be validated by a solo client'
$receipt=New-TestReceipt;$receipt.observed_at='not a date'
$null=Assert-Status $receipt 'INVALID' 'invalid observation date'
$receipt=New-TestReceipt;$receipt.Remove('observed_at')
$null=Assert-Status $receipt 'INVALID' 'missing observation date'

$receipt=New-TestReceipt;$receipt.scenarios=@(New-TestScenario 'unknown')
$null=Assert-Status $receipt 'INVALID' 'unknown family'
$receipt.scenarios=@((New-TestScenario 'timber'), (New-TestScenario 'timber'))
$null=Assert-Status $receipt 'INVALID' 'duplicate family cannot inflate coverage'
$receipt.scenarios=@(New-TestScenario 'timber' 'pass')
$null=Assert-Status $receipt 'INVALID' 'scenario status must be explicit'
$receipt.scenarios=@(New-TestScenario 'timber');$receipt.scenarios[0].observation=' '
$null=Assert-Status $receipt 'INVALID' 'missing actual observation'
$receipt.scenarios=@('timber')
$null=Assert-Status $receipt 'INVALID' 'plain scenario names are not observations'

$receipt=New-TestReceipt 'Fabric';$receipt.client.mode='singleplayer'
$receipt.scenarios=@(Get-RequiredClientScenarios 'Paper' | ForEach-Object {New-TestScenario $_})
$result=Assert-Status $receipt 'PARTIAL' 'Fabric additionally requires integrated world lifecycle' 'Fabric'
if ($result.required -ne 11 -or $result.passed -ne 10) { throw 'Fabric solo coverage was not required' }
$receipt.scenarios+=New-TestScenario 'solo_world_lifecycle'
$null=Assert-Status $receipt 'PASS' 'all Fabric families including solo lifecycle' 'Fabric'
$receipt.client.mode='dedicated'
$null=Assert-Status $receipt 'INVALID' 'dedicated Fabric cannot claim solo world lifecycle' 'Fabric'

# JSON objects must behave exactly like the hashtable fixtures above.
$receipt=New-TestReceipt
$receipt.scenarios=@(New-TestScenario 'ore_loot' 'PARTIAL')
$jsonReceipt=$receipt | ConvertTo-Json -Depth 6 | ConvertFrom-Json
$null=Assert-Status $jsonReceipt 'PARTIAL' 'actual deserialized JSON receipt'
$receipt=New-TestReceipt
$receipt.scenarios=@(Get-RequiredClientScenarios 'Paper' | ForEach-Object {New-TestScenario $_})
$jsonReceipt=$receipt | ConvertTo-Json -Depth 6 | ConvertFrom-Json
$null=Assert-Status $jsonReceipt 'PASS' 'complete deserialized JSON receipt'
Write-Host "$script:checks client evidence regression cases passed. No game or server was started."
