@echo off
setlocal
REM ============================================================
REM  Start the resident binlog -> Elasticsearch sync process.
REM
REM  No dependency check here - it goes straight to:
REM      python -m backend.canal.canal_sync run
REM
REM  Use start_all.bat instead if you want the 3 dependency
REM  services (MySQL / ES / Canal) checked before starting.
REM
REM  Prerequisites (start them yourself):
REM    1. Elasticsearch        9200
REM    2. Canal Server         11111   (start_canal.bat)
REM
REM  Stop: press Ctrl+C in this window.
REM
REM  IMPORTANT: keep this file pure ASCII. Non-ASCII bytes in a
REM  .bat get re-decoded by the console code page and can be run
REM  as bogus commands, breaking the script.
REM ============================================================

REM Project root derived from script location (deploy\canal\ -> repo root)
if not defined PROJ set "PROJ=%~dp0..\.."
set "PY=%PROJ%\.venv\Scripts\python.exe"

if not exist "%PY%" (
    echo [ERROR] python not found: %PY%
    echo.
    pause
    exit /b 1
)

cd /d "%PROJ%"

echo ============================================================
echo  binlog to ES - resident sync
echo    project : %PROJ%
echo    source  : ecommerce.product + product_image
echo    target  : ES index product
echo    stop    : Ctrl+C
echo ============================================================
echo.

"%PY%" -m backend.canal.canal_sync run

echo.
echo sync process exited.
echo.
pause
