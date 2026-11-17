@echo off
call "%~dp0env.bat"
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [platform-service] starting on 8085 ... log: logs\platform-service.log
"%JAVA%" -jar "platform-service\build\libs\platform-service-1.0.0-SNAPSHOT.jar" --server.port=8085 >> "logs\platform-service.log" 2>&1
echo.
echo [platform-service] exited. Inspect logs\platform-service.log for the reason.
pause
