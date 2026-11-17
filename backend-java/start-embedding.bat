@echo off
call "%~dp0env.bat"
cd /d "%~dp0"
if not exist "logs" mkdir "logs"
echo [embedding] starting on 10683 ... log: logs\embedding.log
REM --app-dir keeps the backend package importable without changing cwd
"%PY%" -m uvicorn --app-dir "%TS%" backend.ai_service:app --host 127.0.0.1 --port 10683 >> "logs\embedding.log" 2>&1
echo.
echo [embedding] exited. Inspect logs\embedding.log for the reason.
pause
