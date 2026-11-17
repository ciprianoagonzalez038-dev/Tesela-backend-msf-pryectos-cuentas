@echo off
cd /d %~dp0
echo Mostrando logs de ms-xrello-cuentas (Ctrl+C para salir, no detiene el contenedor)...
docker compose logs -f
pause
