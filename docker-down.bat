@echo off
cd /d %~dp0
echo Deteniendo ms-xrello-cuentas...
docker compose down
echo.
echo Contenedor detenido y eliminado.
pause
