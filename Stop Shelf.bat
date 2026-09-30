@echo off
echo Stopping Shelf (port 5050)...
set FOUND=0
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5050" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
    set FOUND=1
)
if %FOUND%==1 (
    echo Done.
) else (
    echo Shelf doesn't seem to be running.
)
pause
