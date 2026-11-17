@echo off
setlocal
REM ============================================================
REM  Start Canal Server (canal.deployer)
REM
REM    - JDK 8 (Corretto 1.8.0_422)
REM    - instance product_sync  (subscribes product + product_image)
REM    - serverMode = tcp, listens on 11111
REM
REM  Why an explicit JDK 8 path instead of the bundled startup.bat:
REM    The stock startup.bat calls plain "java" from PATH, which on
REM    this machine resolves to JDK 21, and it passes -XX:PermSize
REM    (removed after JDK 8) -> JVM refuses to start:
REM        Unrecognized VM option 'PermSize=128m'
REM    This script pins JDK 8 and drops that obsolete flag.
REM
REM  Stop: close this window, or taskkill /PID <pid>
REM
REM  IMPORTANT: keep this file pure ASCII. Non-ASCII bytes in a
REM  .bat get re-decoded by the console code page and can be run
REM  as bogus commands, breaking the script.
REM ============================================================

REM Set these before running; keep real paths out of the repo.
if not defined CANAL_HOME set "CANAL_HOME=REPLACE_ME_CANAL_HOME"
if not defined JDK8 set "JDK8=REPLACE_ME_JDK8_HOME"

if not exist "%CANAL_HOME%\bin" (
    echo [ERROR] CANAL_HOME not found: %CANAL_HOME%
    echo.
    pause
    exit /b 1
)
if not exist "%JDK8%\bin\java.exe" (
    echo [ERROR] JDK 8 not found: %JDK8%
    echo         edit JDK8 at the top of this script.
    echo.
    pause
    exit /b 1
)

echo ============================================================
echo  starting Canal Server
echo    CANAL_HOME = %CANAL_HOME%
echo    JDK8       = %JDK8%
echo    instance   = product_sync   ^(product + product_image^)
echo    port       = 11111
echo ============================================================
echo.

cd /d "%CANAL_HOME%\bin"
"%JDK8%\bin\java.exe" -Xms128m -Xmx512m ^
    -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true ^
    -Dapplication.codeset=UTF-8 -Dfile.encoding=UTF-8 ^
    -classpath "%CANAL_HOME%\conf;%CANAL_HOME%\lib\*" ^
    com.alibaba.otter.canal.deployer.CanalLauncher

echo.
echo Canal exited.
echo.
pause
