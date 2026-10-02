param([string[]]$AppArgs=@())
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$localConfig=Join-Path $projectRoot 'src/main/resources/application-local.yml'
if (!(Test-Path -LiteralPath $localConfig)) { throw 'Create src/main/resources/application-local.yml with your local settings first.' }
Push-Location $projectRoot
try {
    if (!($AppArgs -match '^--spring.profiles.active=')) { $AppArgs=@('--spring.profiles.active=local')+$AppArgs }
    $gradleArgs=@('bootRun',('--args='+($AppArgs -join ' ')))
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE) { exit $LASTEXITCODE }
}
finally { Pop-Location }
