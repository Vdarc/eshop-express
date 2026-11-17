@echo off
REM ==========================================================================
REM  Start the whole stack, one window per service.
REM  Each service has its own start-<name>.bat so it can be debugged alone;
REM  this file only checks ports and launches them in dependency order.
REM
REM  Order: embedding -> auth -> catalog -> translation/recommendation ->
REM         platform -> gateway -> frontend
REM  Re-running is safe: anything already listening is skipped.
REM ==========================================================================
setlocal
call "%~dp0env.bat"
set "HERE=%~dp0"
if not exist "%HERE%logs" mkdir "%HERE%logs"

echo ==========================================================
echo  TranslationSystem startup
echo ==========================================================
echo.
echo [0/8] infra
if exist "%HERE%preflight.py" "%PY%" "%HERE%preflight.py" %REDIS_HOST% %REDIS_PORT%
echo.

call :step 1 10683 "embedding"         start-embedding.bat
call :step 2 8081  "auth-service"      start-auth.bat
call :step 3 8082  "catalog-service"   start-catalog.bat
call :step 4 8083  "translation"       start-translation.bat
call :step 5 8084  "recommendation"    start-recommendation.bat
call :step 6 8085  "platform-service"  start-platform.bat
call :step 7 10600 "gateway-service"   start-gateway.bat
call :step 8 20682 "frontend-vite"    start-frontend.bat
echo.

echo Waiting for gateway :10600 ...
set /a _try=0
:wait
set /a _try+=1
call :up 10600
if not errorlevel 1 goto ready
if %_try% geq 60 goto slow
ping -n 2 127.0.0.1 >nul
goto wait

:slow
echo   gateway not up after 120s - check logs\gateway-service.log
goto done

:ready
echo.
echo ==========================================================
echo  Ready
echo    UI         http://localhost:20682
echo    Gateway    http://localhost:10600
echo  Logs         %HERE%logs
echo ==========================================================

:done
echo.
echo Press any key to close this window. Services keep running.
pause >nul
endlocal
exit /b 0

REM ---------------------------------------------------------------
REM :step N PORT LABEL SCRIPT
REM ---------------------------------------------------------------
:step
call :up %2
if not errorlevel 1 (
  echo [%1/8] %~3 port %2 already listening, skip
  exit /b 0
)
echo [%1/8] %~3 port %2 starting ...
start "%~3" /MIN "%HERE%%~4"
exit /b 0

REM ---------------------------------------------------------------
REM :up PORT -> errorlevel 0 when something is LISTENING on PORT
REM ---------------------------------------------------------------
:up
netstat -an | findstr /C:":%~1 " | findstr /C:"LISTENING" >nul 2>&1
if errorlevel 1 (exit /b 1)
exit /b 0
