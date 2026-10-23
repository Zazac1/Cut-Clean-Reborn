[CmdletBinding()]
param(
    [ValidateSet('Quick', 'Compile', 'Full')]
    [string]$Mode = 'Full',
    [string[]]$Version,
    [switch]$RerunTasks
)

& (Join-Path $PSScriptRoot 'validation\run-all-tests.ps1') @PSBoundParameters
exit $LASTEXITCODE
