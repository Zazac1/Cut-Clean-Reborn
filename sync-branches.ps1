[CmdletBinding()]
param(
    [ValidateSet('Plan', 'Local', 'Publish')]
    [string]$Mode = 'Plan'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

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
    $projects = @(Invoke-Git @('ls-tree','-d','--name-only',$source) | Where-Object { $_ -match '^Paper_\d[\w.]*$' } | Sort-Object)
    if ($projects.Count -eq 0) { throw 'Aucun projet Paper enregistre dans main' }

    if ($Mode -eq 'Publish') {
        # Fetch only updates remote-tracking refs; never prune historical branches.
        Invoke-Git @('-c','credential.interactive=never','fetch','--no-tags','origin') | Out-Host
    }
    $remoteMain = Find-Commit 'refs/remotes/origin/main'
    if ($remoteMain -and -not (Test-Ancestor $remoteMain $source)) { throw 'origin/main contient des changements absents de main. Les examiner avant de publier.' }
    $checkedOut = @(Invoke-Git @('worktree','list','--porcelain') | Where-Object { $_.StartsWith('branch refs/heads/') } | ForEach-Object { $_.Substring('branch refs/heads/'.Length) })
    $plan = [System.Collections.Generic.List[object]]::new()

    foreach ($project in $projects) {
        $minecraft = $project.Substring('Paper_'.Length)
        $branch = "paper/$minecraft"
        Invoke-Git @('check-ref-format',"refs/heads/$branch") | Out-Null
        if ($branch -in $checkedOut) { throw "La branche $branch est ouverte dans un checkout ; terminer ce travail avant de la synchroniser." }
        $tree = (Invoke-Git @('rev-parse',"${source}:$project")) -join ''
        Invoke-Git @('cat-file','-e',"${tree}:build.gradle") | Out-Null
        Invoke-Git @('cat-file','-e',"${tree}:src/main/resources/plugin.yml") | Out-Null
        $local = Find-Commit "refs/heads/$branch"
        $remote = Find-Commit "refs/remotes/origin/$branch"
        $parent = $local
        if ($remote) {
            if (-not $parent) { $parent = $remote }
            elseif (-not (Test-Ancestor $remote $parent)) { throw "La branche distante $branch contient des commits absents de la branche locale ; les examiner avant de synchroniser." }
        }
        $oldTree = if ($parent) { (Invoke-Git @('rev-parse',"${parent}^{tree}")) -join '' } else { '' }
        $action = if ($oldTree -eq $tree) { 'a jour' } elseif ($parent) { 'nouveau commit, historique conserve' } else { 'nouvelle branche autonome' }
        $plan.Add([pscustomobject]@{ Project=$project; Branch=$branch; Tree=$tree; Parent=$parent; Local=$local; Action=$action; NeedsCommit=($oldTree -ne $tree) })
        Write-Host "$project -> $branch : $action"
    }
    Write-Host 'fabric/1.21.11 : historique conserve, aucun changement'
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
        Write-Host 'main et branches Paper publiees ensemble, sans suppression ni force-push.'
    }
} catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
} finally { Pop-Location }
exit 0
