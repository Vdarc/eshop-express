@echo off
chcp 65001 >nul
setlocal
REM ============================================================
REM  Restart Elasticsearch on Windows (graceful stop -> start)
REM  Usage: double-click, or run from cmd.
REM  Why: heap.options in config\jvm.options.d only takes effect
REM       after a restart.
REM ============================================================

REM Point ES_HOME at your local Elasticsearch install.
if not defined ES_HOME set "ES_HOME=REPLACE_ME_ES_HOME"
set "ES_USER=elastic"
REM Set ES_PASS in your shell before running, or edit this line locally.
if not defined ES_PASS set "ES_PASS=REPLACE_ME"

echo ============================================================
echo  Elasticsearch restart  ^( %ES_HOME% ^)
echo ============================================================
echo.

echo [1/4] Locating process on port 9200 ...
set "ES_PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":9200" ^| findstr "LISTENING"') do (
    if not defined ES_PID set "ES_PID=%%a"
)

if not defined ES_PID (
    echo       no running instance found, will start fresh.
    goto start_es
)

echo       found PID=%ES_PID%, stopping gracefully ...
taskkill /PID %ES_PID% /T >nul 2>&1
timeout /t 20 /nobreak >nul

netstat -ano | findstr ":9200" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo       still alive, forcing stop ...
    taskkill /PID %ES_PID% /F /T >nul 2>&1
    timeout /t 10 /nobreak >nul
)
echo       stopped.

:start_es
echo.
echo [2/4] Starting Elasticsearch ...
cd /d "%ES_HOME%"
start "Elasticsearch" bin\elasticsearch.bat

echo.
echo [3/4] Waiting for readiness ^(max 180s^) ...
set /a WAITED=0
:wait_loop
timeout /t 5 /nobreak >nul
set /a WAITED+=5
curl -sk -u %ES_USER%:%ES_PASS% -m 5 https://localhost:9200 >nul 2>&1
if errorlevel 1 (
    if %WAITED% lss 180 goto wait_loop
    echo       TIMEOUT after %WAITED%s - check %ES_HOME%\logs\elasticsearch.log
    goto done
)
echo       ready after %WAITED%s.

echo.
echo [4/4] JVM heap (should now be 1.0 GB) ...
curl -sk -u %ES_USER%:%ES_PASS% -m 8 "https://localhost:9200/_nodes/stats/jvm?filter_path=nodes.*.name,nodes.*.jvm.mem.heap_max_in_bytes"

:done
echo.
pause
