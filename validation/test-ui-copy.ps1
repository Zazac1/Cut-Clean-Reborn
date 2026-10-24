[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$sourceModules = @('Paper_1.21.1', 'Paper_1.21.11', 'Paper_26.1.x', 'Paper_26.2', 'Paper_26.3',
    'platform-bukkit', 'Fabric_1.21.1', 'platform-fabric-modern')
$problems = [System.Collections.Generic.List[string]]::new()
$literalPattern = '"(?:[^"\\]|\\.)*"'
$frenchCopy = '[éèêàçùîôû]|\b(Cliquer|Confirmer|Butin|Retour|Fermer|Valider|Annuler|Supprimer|Choisir|Quantité|Niveau|Règle|aucune|joueur|Armure|Remboursement)\b'
$internalCopy = '(?i)niveau de retour|pagination (does not|never)|Navigation & Saving|Changes are saved automatically|semantic (level|parent)|Escape history|Menus: Escape = parent'
$checked = 0
foreach ($module in $sourceModules) {
    $sourceRoot = Join-Path $workspace "$module/src/main/java"
    foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' -File) {
        $source = [IO.File]::ReadAllText($file.FullName)
        foreach ($literal in [regex]::Matches($source, $literalPattern)) {
            if ($literal.Value -match $frenchCopy -or $literal.Value -match $internalCopy) {
                $problems.Add("$module/$($file.Name): $($literal.Value)")
            }
        }
        $checked++
    }
}
foreach ($relative in @(
    'Fabric_1.21.1/src/main/java/fr/isaac/cutcleanreborn/fabric/Menus.java',
    'platform-fabric-modern/src/main/java/fr/isaac/cutcleanreborn/fabric/RuleMenus.java')) {
    $source = [IO.File]::ReadAllText((Join-Path $workspace $relative))
    if ($source -notmatch 'if \(pages > 1\)\s+getSlot\(footer \+ 4\)') {
        $problems.Add("${relative}: single-page menus must not display a redundant page counter")
    }
    if ($source -match 'Component\.literal\("CCR • " \+ title\)') {
        $problems.Add("${relative}: input titles must omit redundant branding to fit the anvil header")
    }
}
if ($problems.Count -gt 0) { throw ($problems -join [Environment]::NewLine) }
Write-Host "[PASS] English UI copy: $checked source files; no internal navigation copy; page counters only when needed."
