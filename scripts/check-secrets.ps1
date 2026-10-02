param([string[]]$Paths)
$ErrorActionPreference = 'Stop'
$patterns = @(
    '-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----',
    '(?:AKIA|ASIA)[0-9A-Z]{16}',
    'gh[pousr]_[A-Za-z0-9_]{30,}',
    'github_pat_[A-Za-z0-9_]{30,}',
    'https://(?:discord(?:app)?\.com)/api/webhooks/[0-9]+/[A-Za-z0-9_-]+'
) -join '|'
$failed = $false
if (!$Paths) {
    $Paths = @(& git -c core.quotepath=false ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE) { throw 'Cannot enumerate commit candidate files.' }
    # Inspect the index too: staged content can differ from the working file.
    $staged = @(& git -c core.quotepath=false grep --cached -I -l -P -e $patterns)
    if ($LASTEXITCODE -gt 1) { throw 'Cannot inspect staged content.' }
    foreach ($path in $staged) {
        Write-Output "Potential staged credential in: $path"
        $failed = $true
    }
}
foreach ($path in ($Paths | Sort-Object -Unique)) {
    $commitPath = $path.Replace('\', '/')
    $privateConfig = $commitPath -match '(^|/)(?:\.env(?:\..*)?|application-local\.(?:ya?ml|properties)|\.aws(?:/|$))' -and
        $commitPath -notmatch '(^|/)\.env\.example$'
    $privateDirectory = $commitPath -match '(^|/)(?:\.tools|\.aws|\.gradle|\.idea|\.vscode|build|target|backups)(/|$)'
    $privateFile = $commitPath -match '\.(?:pem|key|p12|pfx|jks|keystore|log|jfr|hprof|dmp|bak|orig|rej)$'
    $hasSecret = (Test-Path -LiteralPath $path -PathType Leaf) -and
        [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $path).Path) -cmatch $patterns
    if ($privateConfig -or $privateDirectory -or $privateFile -or $hasSecret) {
        # Report only the filename; never print a matched credential.
        Write-Output "Potential credential in: $path"
        $failed = $true
    }
}
if ($failed) { throw 'Secret check failed. Remove credentials before committing.' }
Write-Output 'Secret check passed.'
exit 0
