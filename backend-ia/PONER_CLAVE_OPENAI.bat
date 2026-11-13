@echo off
REM Creado por Claude: guarda tu clave de OpenAI como secreto del servidor de IA (no queda en la app ni en archivos).
REM Usalo despues de ACTIVAR_IA.bat, o cuando cambies la clave. Con la clave, la mascota responde con GPT-5 mini.
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "LOG=%~dp0clave_openai_log.txt"
echo ==== %DATE% %TIME% ==== > "%LOG%"
where node >nul 2>nul || (echo Falta Node.js 22 o superior: https://nodejs.org & pause & exit /b 1)
echo.
echo Crea tu clave en https://platform.openai.com/api-keys (empieza con sk-).
echo Copiala y pegala aqui con clic derecho. No se mostrara en ningun registro.
echo.
set "OPENAI_KEY="
set /p "OPENAI_KEY=Clave de OpenAI: "
if "!OPENAI_KEY!"=="" (echo No escribiste ninguna clave. & echo SIN_CLAVE >> "%LOG%" & pause & exit /b 1)
if not "!OPENAI_KEY:~0,3!"=="sk-" (echo La clave debe empezar con sk- & echo FORMATO_INVALIDO >> "%LOG%" & pause & exit /b 1)
node -e "process.stdout.write(process.env.OPENAI_KEY)" | call npx --yes wrangler@4 secret put OPENAI_API_KEY >> "%LOG%" 2>&1
if errorlevel 1 (
  echo No se pudo guardar la clave. Si nunca ejecutaste ACTIVAR_IA.bat, hazlo primero.
  echo SECRETO_FALLO >> "%LOG%"
  pause
  exit /b 1
)
set "OPENAI_KEY="
echo CLAVE_GUARDADA >> "%LOG%"
echo.
echo Listo: la mascota ya responde con GPT-5 mini. No hace falta volver a compilar la app.
pause
endlocal
