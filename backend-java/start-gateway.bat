@echo off
call "%~dp0env.bat"
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [gateway-service] starting on 10600 ... log: logs\gateway-service.log
"%JAVA%" -jar "gateway-service\build\libs\gateway-service-1.0.0-SNAPSHOT.jar" --server.port=10600 >> "logs\gateway-service.log" 2>&1
echo.
echo [gateway-service] exited. Inspect logs\gateway-service.log for the reason.
pause
