@echo off
call "%~dp0env.bat"
cd /d "%~dp0"
if not exist "logs" mkdir "logs"

REM ES 8/9 use a self-signed CA. The truststore is NOT committed
REM (see .gitignore: *.jks). Only needed when ES_ENABLED=true.
REM Without it catalog-service still boots; search falls back to MySQL.
set "SSL_OPTS="
if exist "%~dp0certs\es-truststore.jks" (
    set "SSL_OPTS=-Djavax.net.ssl.trustStore=%~dp0certs\es-truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
)

echo [catalog-service] starting on 8082 ... log: logs\catalog-service.log
"%JAVA%" %SSL_OPTS% -jar "catalog-service\build\libs\catalog-service-1.0.0-SNAPSHOT.jar" --server.port=8082 >> "logs\catalog-service.log" 2>&1
echo.
echo [catalog-service] exited. Inspect logs\catalog-service.log for the reason.
pause
