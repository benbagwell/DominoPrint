param([switch]$RequireReady)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$separator = [IO.Path]::PathSeparator
$artifacts = @('domino-model', 'domino-formula', 'domino-render', 'domino-examples') | ForEach-Object { Join-Path $taskRoot "$_/target/$_-0.1.0-SNAPSHOT.jar" }
foreach ($artifact in $artifacts) { if (-not (Test-Path -LiteralPath $artifact)) { throw "Build first: mvn package. Missing $artifact" } }
$arguments = @('-cp', ($artifacts -join $separator), 'io.github.benbagwell.dominoprint.examples.OfflineExample', (Join-Path $taskRoot 'examples/output'))
if ($RequireReady) { $arguments += '--require-ready' }
& java @arguments
if ($LASTEXITCODE -ne 0) { throw "Example or release gate failed (exit $LASTEXITCODE)" }
