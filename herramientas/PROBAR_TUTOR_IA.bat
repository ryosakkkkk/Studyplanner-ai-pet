@echo off
REM Prueba el tutor con IA contra el servidor de la app y deja el resultado en prueba_tutor_ia.txt
echo Probando el tutor con IA (tarda cerca de un minuto)...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0probar_tutor_ia.ps1"
echo Listo. El resultado esta en prueba_tutor_ia.txt
timeout /t 3 >nul
