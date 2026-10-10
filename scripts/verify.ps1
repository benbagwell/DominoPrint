param([switch]$SkipDistribution)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$repository = Join-Path $taskRoot '.m2/repository'
$arguments = @('-B', "-Dmaven.repo.local=$repository", '-f', (Join-Path $taskRoot 'pom.xml'))
if (-not $SkipDistribution) { $arguments += '-Pdistribution' }
$arguments += 'verify'
& mvn @arguments
if ($LASTEXITCODE -ne 0) { throw "Maven verification failed (exit $LASTEXITCODE)" }
