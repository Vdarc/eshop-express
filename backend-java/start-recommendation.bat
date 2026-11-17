@echo off
call "%~dp0env.bat"
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [recommendation-service] starting on 8084 ... log: logs\recommendation-service.log
"%JAVA%" -jar "recommendation-service\build\libs\recommendation-service-1.0.0-SNAPSHOT.jar" --server.port=8084 >> "logs\recommendation-service.log" 2>&1
echo.
echo [recommendation-service] exited. Inspect logs\recommendation-service.log for the reason.
pause
