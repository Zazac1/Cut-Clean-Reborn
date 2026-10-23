[CmdletBinding()]
param(
    [ValidateSet('Quick', 'Compile', 'Full')]
    [string]$Mode = 'Full',
    [string[]]$Version,
    [ValidateSet('Paper', 'Purpur', 'Spigot', 'Bukkit', 'Fabric')]
    [string[]]$Platform = @('Paper', 'Purpur', 'Spigot', 'Bukkit', 'Fabric'),
    [switch]$RerunTasks
)

& (Join-Path $PSScriptRoot 'validation\run-all-tests.ps1') @PSBoundParameters
exit $LASTEXITCODE
