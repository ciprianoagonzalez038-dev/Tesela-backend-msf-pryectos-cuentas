@echo off
setlocal
cd /d %~dp0

if not exist ".env" (
    echo.
    echo ERROR: falta el archivo .env con XRELLO_JWT_SECRET.
    echo Copia .env.example a .env y define un valor propio ^(igual en cuentas y tableros^).
    pause
    exit /b 1
)

echo Verificando que Docker Desktop este corriendo...
docker info >nul 2>&1
if errorlevel 1 (
    echo.
    echo ERROR: Docker Desktop no esta corriendo. Abrelo primero e intenta de nuevo.
    pause
    exit /b 1
)

echo.
echo Construyendo y levantando ms-xrello-cuentas (la primera vez puede tardar varios minutos)...
docker compose up -d --build
if errorlevel 1 (
    echo.
    echo ERROR: fallo el build o el arranque del contenedor. Revisa el mensaje de arriba.
    pause
    exit /b 1
)

echo.
echo Esperando a que ms-xrello-cuentas responda en http://localhost:8007 ...
powershell -NoProfile -Command "$ok=$false; for($i=0;$i -lt 60;$i++){ try { $r = Invoke-WebRequest -Uri 'http://localhost:8007/api-docs' -UseBasicParsing -TimeoutSec 3; if($r.StatusCode -eq 200){$ok=$true; break} } catch {}; Start-Sleep -Seconds 3 }; if(-not $ok){exit 1}"

if errorlevel 1 (
    echo.
    echo La aplicacion no respondio a tiempo. Revisa los logs con docker-logs.bat
    pause
    exit /b 1
)

echo.
echo ms-xrello-cuentas esta arriba:
echo   Swagger UI  -^> http://localhost:8007/swagger-ui.html
echo   API JSON    -^> http://localhost:8007/api-docs
echo.
echo Contenedor corriendo (puertos 8007 y 5010). Para detenerlo, ejecuta docker-down.bat
pause
