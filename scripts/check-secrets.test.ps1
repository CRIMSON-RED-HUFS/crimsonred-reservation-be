$ErrorActionPreference = 'Stop'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('crimsonred-secret-check-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
$safe = Join-Path $fixtureRoot 'safe.txt'
$private = Join-Path $fixtureRoot '.env'
$localProperties = Join-Path $fixtureRoot 'application-local.properties'
$debugDump = Join-Path $fixtureRoot 'session.jfr'
$buildDirectory = Join-Path $fixtureRoot 'build'
$buildFile = Join-Path $buildDirectory 'application.txt'
$awsDirectory = Join-Path $fixtureRoot '.aws'
$awsExample = Join-Path $awsDirectory '.env.example'
$checker = Join-Path $PSScriptRoot 'check-secrets.ps1'
try {
    Set-Content -LiteralPath $safe -Value 'safe configuration'
    & $checker -Paths $safe
    Set-Content -LiteralPath $safe -Value ('AKIA' + ('A' * 16))
    Set-Content -LiteralPath $private -Value 'private configuration'
    Set-Content -LiteralPath $localProperties -Value 'spring.datasource.password=private configuration'
    Set-Content -LiteralPath $debugDump -Value 'private diagnostic data'
    New-Item -ItemType Directory -Path $buildDirectory,$awsDirectory | Out-Null
    Set-Content -LiteralPath $buildFile -Value 'generated application data'
    Set-Content -LiteralPath $awsExample -Value 'private cloud configuration'
    foreach ($path in @($safe, $private, $localProperties, $debugDump, $buildFile, $awsExample)) {
        $blocked = $false
        try { & $checker -Paths $path }
        catch { $blocked = $true }
        if (!$blocked) { throw 'Expected credential/configuration was not rejected.' }
    }
    Remove-Item -LiteralPath $private,$localProperties,$debugDump,$buildFile,$awsExample
    Push-Location $fixtureRoot
    try {
        & git init --quiet
        if ($LASTEXITCODE) { throw 'Cannot initialize test repository.' }
        & git add -- safe.txt
        if ($LASTEXITCODE) { throw 'Cannot stage test fixture.' }
        Set-Content -LiteralPath $safe -Value 'safe working file, unsafe index'
        $blocked = $false
        try { & $checker } catch { $blocked = $true }
        if (!$blocked) { throw 'Staged credential was not rejected.' }
        & git add -- safe.txt
        if ($LASTEXITCODE) { throw 'Cannot replace staged test fixture.' }
        & $checker
        Set-Content -LiteralPath '.gitignore' -Value 'build/'
        Set-Content -LiteralPath $buildFile -Value 'generated application data'
        & git add -f -- build/application.txt
        if ($LASTEXITCODE) { throw 'Cannot force-stage generated test fixture.' }
        $blocked = $false
        try { & $checker } catch { $blocked = $true }
        if (!$blocked) { throw 'Force-staged build artifact was not rejected.' }
        & git rm --cached --quiet -- build/application.txt
        if ($LASTEXITCODE) { throw 'Cannot unstage generated test fixture.' }
        & $checker
    }
    finally { Pop-Location }
    Write-Output 'Secret scanner checks passed.'
}
finally {
    $resolvedFixture = [System.IO.Path]::GetFullPath($fixtureRoot)
    if (!$resolvedFixture.StartsWith([System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()),
            [System.StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path $resolvedFixture -Leaf) -notlike 'crimsonred-secret-check-*') {
        throw 'Refusing to remove an unexpected fixture directory.'
    }
    Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
}
