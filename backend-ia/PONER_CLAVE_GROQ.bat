@echo off
REM Creado por Claude: guarda tu clave de Groq como secreto del servidor de IA (no queda en la app ni en archivos).
REM Opcional. Groq es gratis y sin tarjeta: responde cuando Gemini se queda sin cupo; si Groq tambien falla, responde Cloudflare.
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "LOG=%~dp0clave_groq_log.txt"
echo ==== %DATE% %TIME% ==== > "%LOG%"
where node >nul 2>nul || (echo Falta Node.js 22 o superior: https://nodejs.org & pause & exit /b 1)
echo.
echo Crea tu clave gratis en https://console.groq.com/keys (Create API Key). Empieza con gsk_.
echo Copiala y pegala aqui con clic derecho. No se mostrara en ningun registro.
echo.
set "GROQ_KEY="
set /p "GROQ_KEY=Clave de Groq: "
if "!GROQ_KEY!"=="" (echo No escribiste ninguna clave. & echo SIN_CLAVE >> "%LOG%" & pause & exit /b 1)
node -e "process.stdout.write(process.env.GROQ_KEY.trim())" | call npx --yes wrangler@4 secret put GROQ_API_KEY >> "%LOG%" 2>&1
if errorlevel 1 (
  echo No se pudo guardar la clave. Si nunca ejecutaste ACTIVAR_IA.bat, hazlo primero.
  echo SECRETO_FALLO >> "%LOG%"
  pause
  exit /b 1
)
set "GROQ_KEY="
echo CLAVE_GUARDADA >> "%LOG%"
echo.
echo Listo: Groq queda como respaldo gratuito. No hace falta volver a compilar la app.
pause
endlocal
