param(
    [int[]]$PortsToStop = @(8080, 5173),
    [int]$StartupTimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
Set-Location $repoRoot

$logsDir = Join-Path $repoRoot 'logs'
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
$npmCommand = (Get-Command npm.cmd).Source

function Stop-PortListener {
    param([int]$Port)

    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

foreach ($port in $PortsToStop) {
    Stop-PortListener -Port $port
}

Start-Sleep -Seconds 2

$backendLog = Join-Path $logsDir 'backend.log'
$frontendLog = Join-Path $logsDir 'frontend.log'
$backendLauncher = Join-Path $logsDir 'run-backend.ps1'
$frontendLauncher = Join-Path $logsDir 'run-frontend.ps1'

Remove-Item $backendLog, $frontendLog, $backendLauncher, $frontendLauncher -Force -ErrorAction SilentlyContinue

@"
Set-Location '$repoRoot'
`$env:SPRING_PROFILES_ACTIVE = 'local'
`$env:ORACLE_URL = 'jdbc:oracle:thin:@localhost:1522/XEPDB1'
`$env:ORACLE_USER = 'BHTT'
`$env:ORACLE_PASSWORD = 'changeme'
`$env:KEYCLOAK_ADMIN_BASE_URL = 'http://localhost:8081'
`$env:KEYCLOAK_REALM = 'shoponline'
`$env:KEYCLOAK_USER_ADMIN_CLIENT_ID = 'shoponline-user-admin'
`$env:KEYCLOAK_USER_ADMIN_CLIENT_SECRET = 'changeme'
`$env:KEYCLOAK_ADMIN_READ_TIMEOUT = '30s'
.\\gradlew.bat bootRun *>> '$backendLog'
"@ | Set-Content -Path $backendLauncher -Encoding UTF8

@"
Set-Location '$repoRoot\frontend'
`$env:VITE_KEYCLOAK_URL = 'http://localhost:8081'
`$env:VITE_KEYCLOAK_REALM = 'shoponline'
`$env:VITE_KEYCLOAK_CLIENT_ID = 'shoponline-frontend'
& '$npmCommand' run dev -- --host 127.0.0.1 --port 5173 --clearScreen false *>> '$frontendLog'
"@ | Set-Content -Path $frontendLauncher -Encoding UTF8

Start-Process -FilePath powershell.exe -ArgumentList @(
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', "`"$backendLauncher`""
) -WorkingDirectory $repoRoot -WindowStyle Hidden | Out-Null

Start-Process -FilePath powershell.exe -ArgumentList @(
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', "`"$frontendLauncher`""
) -WorkingDirectory $repoRoot -WindowStyle Hidden | Out-Null

$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    $backendReady = [bool](Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
    $frontendReady = [bool](Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue)
    if ($backendReady -and $frontendReady) {
        break
    }
    Start-Sleep -Seconds 2
}

$backendReady = [bool](Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
$frontendReady = [bool](Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue)

Write-Host "Backend ready: $backendReady"
Write-Host "Frontend ready: $frontendReady"
Write-Host "Backend URL: http://127.0.0.1:8080/swagger-ui.html"
Write-Host "Frontend URL: http://127.0.0.1:5173/"
Write-Host "Logs: $logsDir"
