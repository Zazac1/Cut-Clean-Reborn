Set-StrictMode -Version Latest

# This validates the coverage of recorded observations, not their truth. Only
# interactions actually performed in a matching client may produce this receipt.
function Get-RequiredClientScenarios([string]$Platform) {
    'menus_commands', 'timber', 'mob_loot', 'ore_loot', 'auto_enchants',
    'clean_stacks', 'stuff_rules', 'unique_mace', 'sugar_cane', 'persistence'
    if ($Platform -eq 'Fabric') { 'solo_world_lifecycle' }
}

function Get-ClientEvidenceField($Value, [string]$Name) {
    if ($null -eq $Value) { return $null }
    if ($Value -is [System.Collections.IDictionary]) { return $Value[$Name] }
    $property = $Value.PSObject.Properties[$Name]
    if ($null -ne $property) { return $property.Value }
    return $null
}

function Test-ClientEvidence($Receipt, [string]$ProjectName, [string]$Platform,
    [string]$Minecraft, [string]$ArtifactSha256, [string]$SourceFingerprint) {
    $required = @(Get-RequiredClientScenarios $Platform)
    $result = [ordered]@{
        status='INVALID'; passed=0; required=$required.Count; observed=0
        scenarios=@(); detail='Recu client incomplet ou incompatible'
    }
    if ((Get-ClientEvidenceField $Receipt 'schema') -ne 2) {
        $result.detail='Schema client 2 requis ; un ancien PASS global ne prouve pas la couverture'
        return [pscustomobject]$result
    }
    foreach ($field in @(
        @('project', $ProjectName), @('platform', $Platform), @('minecraft', $Minecraft)
    )) {
        if ((Get-ClientEvidenceField $Receipt $field[0]) -ne $field[1]) {
            $result.detail="Contexte client incompatible : $($field[0])"
            return [pscustomobject]$result
        }
    }
    foreach ($field in @(
        @('artifact_sha256', $ArtifactSha256), @('source_fingerprint', $SourceFingerprint)
    )) {
        $actual = [string](Get-ClientEvidenceField $Receipt $field[0])
        if ($actual -notmatch '^[a-fA-F0-9]{64}$' -or $actual -ne $field[1]) {
            $result.status='STALE'; $result.detail="Preuve client perimee : $($field[0])"
            return [pscustomobject]$result
        }
    }
    $client = Get-ClientEvidenceField $Receipt 'client'
    $mode = Get-ClientEvidenceField $client 'mode'
    if ((Get-ClientEvidenceField $client 'minecraft') -ne $Minecraft -or
        [string]::IsNullOrWhiteSpace([string](Get-ClientEvidenceField $client 'profile')) -or
        $mode -notin @('dedicated', 'singleplayer') -or
        ($Platform -ne 'Fabric' -and $mode -ne 'dedicated')) {
        $result.detail='Client, profil ou mode de jeu non renseigne/incompatible'
        return [pscustomobject]$result
    }
    $observedAt = [DateTimeOffset]::MinValue
    $dateValue = Get-ClientEvidenceField $Receipt 'observed_at'
    # Recent PowerShell converts ISO JSON dates to DateTime automatically. Do not
    # stringify them in one culture and then parse them in another.
    $validDate = $dateValue -is [DateTime] -or $dateValue -is [DateTimeOffset]
    if (-not $validDate) {
        $validDate = [DateTimeOffset]::TryParse([string]$dateValue,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::None, [ref]$observedAt)
    }
    if (-not $validDate) {
        $result.detail='Date des observations client absente ou invalide'
        return [pscustomobject]$result
    }
    $seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $observations = [System.Collections.Generic.List[object]]::new()
    foreach ($scenario in @(Get-ClientEvidenceField $Receipt 'scenarios')) {
        if ($null -eq $scenario) { continue }
        $id = [string](Get-ClientEvidenceField $scenario 'id')
        $status = [string](Get-ClientEvidenceField $scenario 'status')
        $detail = [string](Get-ClientEvidenceField $scenario 'observation')
        if ($required -cnotcontains $id -or -not $seen.Add($id) -or
            $status -cnotin @('PASS', 'PARTIAL', 'FAIL') -or [string]::IsNullOrWhiteSpace($detail) -or
            ($id -eq 'solo_world_lifecycle' -and $mode -ne 'singleplayer')) {
            $result.detail='Scenario inconnu, duplique, sans observation ou incompatible avec le mode'
            return [pscustomobject]$result
        }
        $observations.Add([pscustomobject]@{id=$id;status=$status;observation=$detail})
    }
    $result.observed=$observations.Count
    $result.scenarios=$observations.ToArray()
    $result.passed=@($observations | Where-Object status -CEQ 'PASS').Count
    if (@($observations | Where-Object status -CEQ 'FAIL').Count -gt 0) {
        $result.status='FAIL'
    } elseif ($result.passed -eq $required.Count) {
        $result.status='PASS'
    } else {
        $result.status='PARTIAL'
    }
    $result.detail="$($result.passed)/$($required.Count) scenarios complets ; $($result.observed) observations consignees"
    return [pscustomobject]$result
}
