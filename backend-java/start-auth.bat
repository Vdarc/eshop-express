@echo off
call "%~dp0env.bat"
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [auth-service] starting on 8081 ... log: logs\auth-service.log
"%JAVA%" -jar "auth-service\build\libs\auth-service-1.0.0-SNAPSHOT.jar" --server.port=8081 >> "logs\auth-service.log" 2>&1
echo.
echo [auth-service] exited. Inspect logs\auth-service.log for the reason.
pause
