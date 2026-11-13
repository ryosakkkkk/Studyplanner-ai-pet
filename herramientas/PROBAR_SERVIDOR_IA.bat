@echo off
REM Mide cuanto tarda el servidor de IA de la app; deja el resultado en prueba_servidor_ia.txt
echo Probando el servidor de IA (tarda cerca de un minuto)...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0probar_servidor_ia.ps1"
echo Listo. El resultado esta en prueba_servidor_ia.txt
timeout /t 3 >nul
