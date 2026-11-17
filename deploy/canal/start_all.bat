@echo off
setlocal enabledelayedexpansion
REM ============================================================
REM  Start MySQL -> Elasticsearch sync, with dependency check.
REM
REM  Checks the 3 dependency services first, then starts the
REM  resident sync process. Aborts with a clear message if any
REM  dependency is not ready.
REM
REM  Dependencies:
REM    MySQL  3306   data source
REM    ES     9200   sync target
REM    Canal  11111  binlog middleware (run start_canal.bat)
REM
REM  IMPORTANT: keep this file pure ASCII.
REM  Non-ASCII bytes in a .bat are re-decoded by the console code
REM  page and can be executed as bogus commands, breaking the script.
REM ============================================================

REM Project root derived from script location (deploy\canal\ -> repo root)
if not defined PROJ set "PROJ=%~dp0..\.."
set "PY=%PROJ%\.venv\Scripts\python.exe"

echo ============================================================
echo  MySQL to Elasticsearch sync - startup check
echo ============================================================
echo.

REM Dump netstat once (faster than running it three times)
set "NSFILE=%TEMP%\canal_netstat.txt"
netstat -ano > "%NSFILE%" 2>nul

set "MISSING="

findstr /C:":3306 " "%NSFILE%" | findstr /C:"LISTENING" >nul 2>&1
if errorlevel 1 (
    echo   [X] MySQL  ^(3306^)   not running
    set "MISSING=!MISSING! MySQL"
) else (
    echo   [OK] MySQL  ^(3306^)
)

findstr /C:":9200 " "%NSFILE%" | findstr /C:"LISTENING" >nul 2>&1
if errorlevel 1 (
    echo   [X] ES     ^(9200^)   not running
    echo         start: <ES_HOME>\bin\elasticsearch.bat
    set "MISSING=!MISSING! ES"
) else (
    echo   [OK] ES     ^(9200^)
)

findstr /C:":11111 " "%NSFILE%" | findstr /C:"LISTENING" >nul 2>&1
if errorlevel 1 (
    echo   [X] Canal  ^(11111^)  not running
    echo         start: start_canal.bat ^(same folder^)
    set "MISSING=!MISSING! Canal"
) else (
    echo   [OK] Canal  ^(11111^)
)

del "%NSFILE%" >nul 2>&1
echo.

if defined MISSING (
    echo [ABORT] dependencies not ready:!MISSING!
    echo         start them first, then run this script again.
    echo.
    pause
    exit /b 1
)

if not exist "%PY%" (
    echo [ERROR] python not found: %PY%
    echo.
    pause
    exit /b 1
)

echo [CHECK] sync status ...
cd /d "%PROJ%"
"%PY%" -m backend.canal.canal_sync status
echo.

echo ============================================================
echo  starting resident sync   ^(Ctrl+C to stop^)
echo ============================================================
echo.

"%PY%" -m backend.canal.canal_sync run

echo.
echo sync process exited.
echo.
pause
