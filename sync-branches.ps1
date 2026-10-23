[CmdletBinding()]
param(
    [ValidateSet('Plan', 'Local', 'Publish')]
    [string]$Mode = 'Plan',
    [Alias('Project')]
    [string[]]$OnlyProject
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
. (Join-Path $PSScriptRoot 'branch-export.ps1')

function Invoke-Git([string[]]$Arguments) {
    $result = @(& git @Arguments)
    if ($LASTEXITCODE -ne 0) { throw "Git a echoue : $($Arguments -join ' ')" }
    return $result
}

function Find-Commit([string]$Ref) {
    $result = & git rev-parse --verify --quiet "$Ref^{commit}"
    if ($LASTEXITCODE -eq 1) { return $null }
    if ($LASTEXITCODE -ne 0) { throw "Reference invalide : $Ref" }
    return $result
}

function Test-Ancestor([string]$Older, [string]$Newer) {
    & git merge-base --is-ancestor $Older $Newer
    if ($LASTEXITCODE -eq 0) { return $true }
    if ($LASTEXITCODE -eq 1) { return $false }
    throw 'Impossible de comparer les historiques Git'
}

Push-Location -LiteralPath $PSScriptRoot
try {
    $current = (Invoke-Git @('symbolic-ref','--short','HEAD')) -join ''
    if ($current -ne 'main') { throw 'Executer cette commande depuis main, sans changer le contenu du checkout.' }
    $changes = @(Invoke-Git @('status','--porcelain=v1','--untracked-files=no'))
    if ($changes.Count -ne 0) { throw 'Enregistrer les modifications suivies et indexees avant de synchroniser les branches.' }
    $source = (Invoke-Git @('rev-parse','HEAD')) -join ''
    $manifestText=(Invoke-Git @('show', "${source}:branch-exports.json")) -join "`n"
    $manifest=$manifestText | ConvertFrom-Json
    if ($manifest.schema -ne 1) { throw 'Version de manifeste export inconnue' }
    $specs=@{}
    foreach ($property in $manifest.projects.PSObject.Properties) { $specs[$property.Name]=$property.Value }
    $projects=@($specs.Keys | Where-Object { $specs[$_].enabled } | Sort-Object)
    if ($OnlyProject) {
        foreach ($requested in $OnlyProject) { if ($requested -notin $projects) { throw "Projet non valide/exportable : $requested" } }
        $projects=@($projects | Where-Object { $_ -in $OnlyProject })
    }
    if ($projects.Count -eq 0) { throw 'Aucun projet valide/exportable enregistre dans main' }

    if ($Mode -eq 'Publish') {
        # Fetch only updates remote-tracking refs; never prune historical branches.
        Invoke-Git @('-c','credential.interactive=never','fetch','--no-tags','origin') | Out-Host
    }
    $remoteMain = Find-Commit 'refs/remotes/origin/main'
    if ($remoteMain -and -not (Test-Ancestor $remoteMain $source)) { throw 'origin/main contient des changements absents de main. Les examiner avant de publier.' }
    $checkedOut = @(Invoke-Git @('worktree','list','--porcelain') | Where-Object { $_.StartsWith('branch refs/heads/') } | ForEach-Object { $_.Substring('branch refs/heads/'.Length) })
    $plan = [System.Collections.Generic.List[object]]::new()

    foreach ($project in $projects) {
        $spec=$specs[$project]
        $branch=[string]$spec.branch
        $nameMatch=[regex]::Match($project, '^(Paper|Purpur|Spigot|Bukkit|Fabric)_(\d[\w.]*)$')
        if (-not $nameMatch.Success -or $branch -ne "$($nameMatch.Groups[1].Value.ToLowerInvariant())/$($nameMatch.Groups[2].Value)") { throw 'Nom de branche incoherent avec le projet' }
        Invoke-Git @('check-ref-format',"refs/heads/$branch") | Out-Null
        if ($branch -in $checkedOut) { throw "La branche $branch est ouverte dans un checkout ; terminer ce travail avant de la synchroniser." }
        Invoke-Git @('cat-file','-e',"${source}:${project}/build.gradle") | Out-Null
        foreach ($dependency in $spec.dependencies) { Invoke-Git @('cat-file','-e',"${source}:$($dependency.source)") | Out-Null }
        # Plan must remain genuinely read-only, including Git's object database.
        $composed=@($spec.dependencies).Count -gt 0
        $tree=if ($Mode -eq 'Plan' -and $composed) { '' } else { Get-StandaloneExportTree $source $project $spec }
        if ($tree) {
            $metadata=if ($project.StartsWith('Fabric_')) { 'fabric.mod.json' } else { 'plugin.yml' }
            $metadataPath=if ($spec.PSObject.Properties.Name -contains 'metadata_path') { [string]$spec.metadata_path } else { "src/main/resources/$metadata" }
            Invoke-Git @('cat-file','-e',"${tree}:$metadataPath") | Out-Null
        }
        $local = Find-Commit "refs/heads/$branch"
        $remote = Find-Commit "refs/remotes/origin/$branch"
        $parent = $local
        if ($remote) {
            if (-not $parent) { $parent = $remote }
            elseif (-not (Test-Ancestor $remote $parent)) { throw "La branche distante $branch contient des commits absents de la branche locale ; les examiner avant de synchroniser." }
        }
        $oldTree = if ($parent) { (Invoke-Git @('rev-parse',"${parent}^{tree}")) -join '' } else { '' }
        $action = if ($Mode -eq 'Plan' -and $composed) { 'export autonome avec sources partagees materialisees (comparaison en mode Local)' } elseif ($oldTree -eq $tree) { 'a jour' } elseif ($parent) { 'nouveau commit, historique conserve' } else { 'nouvelle branche autonome' }
        $plan.Add([pscustomobject]@{ Project=$project; Branch=$branch; Tree=$tree; Parent=$parent; Local=$local; Action=$action; NeedsCommit=($oldTree -ne $tree) })
        Write-Host "$project -> $branch : $action"
    }
    foreach ($pending in $specs.Keys | Where-Object { -not $specs[$_].enabled } | Sort-Object) { Write-Host "$pending : portage/validation en cours, non publie comme branche actuelle" }
    if ($Mode -eq 'Plan') { Write-Host 'Plan uniquement. Utiliser -Mode Local ou -Mode Publish pour appliquer.'; exit 0 }

    foreach ($entry in $plan) {
        $commit = $entry.Parent
        if ($entry.NeedsCommit) {
            $arguments = @('commit-tree',$entry.Tree,'-m',"Sync $($entry.Branch) from main ($($source.Substring(0,7)))")
            if ($entry.Parent) { $arguments += @('-p',$entry.Parent) }
            $commit = (Invoke-Git $arguments) -join ''
        }
        if ($commit -ne $entry.Local) {
            $expected = if ($entry.Local) { $entry.Local } else { '0000000000000000000000000000000000000000' }
            Invoke-Git @('update-ref',"refs/heads/$($entry.Branch)",$commit,$expected) | Out-Null
        }
        Write-Host "$($entry.Branch) : $commit"
    }
    if ($Mode -eq 'Publish') {
        # A concurrent remote update makes this normal push fail; no forced updates.
        $arguments = @('-c','credential.interactive=never','push','--atomic','origin',"${source}:refs/heads/main")
        foreach ($entry in $plan) { $arguments += "refs/heads/$($entry.Branch):refs/heads/$($entry.Branch)" }
        Invoke-Git $arguments | Out-Host
        foreach ($entry in $plan) {
            Invoke-Git @('branch',"--set-upstream-to=origin/$($entry.Branch)",$entry.Branch) | Out-Host
        }
        Write-Host 'main et branches validees publiees ensemble, sans suppression ni force-push.'
    }
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
} finally { Pop-Location }
exit 0
