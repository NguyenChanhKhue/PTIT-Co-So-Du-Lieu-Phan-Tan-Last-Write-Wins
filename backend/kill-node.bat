@echo off
REM Kill a running node gracefully via Actuator shutdown
if "%1"=="" (
  echo Usage: kill-node.bat 1^|2^|3
  exit /b 1
)
set NODE=%1
if "%NODE%"=="1" set PORT=8081
if "%NODE%"=="2" set PORT=8082
if "%NODE%"=="3" set PORT=8083

echo Sending shutdown request to node %NODE% (port %PORT%)...
powershell -Command "try { Invoke-RestMethod -Uri 'http://localhost:%PORT%/actuator/shutdown' -Method Post -ContentType 'application/json' -TimeoutSec 5; Write-Host 'Shutdown request sent'; } catch { Write-Host ('Failed to send shutdown: ' + $_.Exception.Message); exit 1 }"

echo Done.
