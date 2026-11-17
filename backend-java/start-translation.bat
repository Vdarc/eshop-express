@echo off
call "%~dp0env.bat"
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [translation-service] starting on 8083 ... log: logs\translation-service.log
"%JAVA%" -jar "translation-service\build\libs\translation-service-1.0.0-SNAPSHOT.jar" --server.port=8083 >> "logs\translation-service.log" 2>&1
echo.
echo [translation-service] exited. Inspect logs\translation-service.log for the reason.
pause
