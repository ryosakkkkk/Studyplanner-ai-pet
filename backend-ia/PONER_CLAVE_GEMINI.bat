@echo off
REM Creado por Claude: guarda tu clave de Gemini como secreto del servidor de IA (no queda en la app ni en archivos).
REM Usalo despues de ACTIVAR_IA.bat, o cuando cambies la clave. Si Gemini se queda sin cupo, responde la IA gratuita de Cloudflare.
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "LOG=%~dp0clave_gemini_log.txt"
echo ==== %DATE% %TIME% ==== > "%LOG%"
where node >nul 2>nul || (echo Falta Node.js 22 o superior: https://nodejs.org & pause & exit /b 1)
echo.
echo Crea tu clave en https://aistudio.google.com (Get API key). Empieza con AQ. o con AIza.
echo Copiala y pegala aqui con clic derecho. No se mostrara en ningun registro.
echo.
set "GEMINI_KEY="
set /p "GEMINI_KEY=Clave de Gemini: "
if "!GEMINI_KEY!"=="" (echo No escribiste ninguna clave. & echo SIN_CLAVE >> "%LOG%" & pause & exit /b 1)
node -e "process.stdout.write(process.env.GEMINI_KEY.trim())" | call npx --yes wrangler@4 secret put GEMINI_API_KEY >> "%LOG%" 2>&1
if errorlevel 1 (
  echo No se pudo guardar la clave. Si nunca ejecutaste ACTIVAR_IA.bat, hazlo primero.
  echo SECRETO_FALLO >> "%LOG%"
  pause
  exit /b 1
)
set "GEMINI_KEY="
echo CLAVE_GUARDADA >> "%LOG%"
echo.
echo Listo: la mascota ya responde con Gemini. No hace falta volver a compilar la app.
pause
endlocal
