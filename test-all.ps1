[CmdletBinding()]
param(
    [ValidateSet('Quick', 'Compile', 'Full')]
    [string]$Mode = 'Full',
    [string[]]$Version,
    [ValidateSet('Paper', 'Purpur', 'Spigot', 'Bukkit')]
    [string[]]$Platform = @('Paper', 'Purpur', 'Spigot', 'Bukkit'),
    [switch]$RerunTasks
)

& (Join-Path $PSScriptRoot 'validation\run-all-tests.ps1') @PSBoundParameters
exit $LASTEXITCODE
