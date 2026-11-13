@echo off
REM Creado por Claude: activa el servidor de IA de StudyPlanner en TU cuenta de Cloudflare (Gemini gratis si pones tu clave; si no, IA gratuita de Cloudflare).
REM 1) Abre el navegador para iniciar sesion en Cloudflare (tu decides y aceptas alli).
REM 2) Publica este servidor (carpeta backend-ia).
REM 3) Crea un codigo de acceso aleatorio y lo guarda como secreto del servidor.
REM 4) Escribe la URL y el codigo en android-app\ia_local.properties para la siguiente compilacion de la app.
REM No borra nada. El registro queda en backend-ia\activar_ia_log.txt (sin el codigo).
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "LOG=%~dp0activar_ia_log.txt"
echo ==== %DATE% %TIME% ==== > "%LOG%"
where node >nul 2>nul
if errorlevel 1 (
  echo Falta Node.js 22 o superior. Instalalo desde https://nodejs.org y vuelve a abrir este archivo.
  echo FALTA_NODE >> "%LOG%"
  pause
  exit /b 1
)
echo.
echo [1/4] Se abrira el navegador para entrar a Cloudflare. Si no tienes cuenta, crea una gratis y vuelve aqui.
call npx --yes wrangler@4 login
if errorlevel 1 (
  echo No se pudo iniciar sesion en Cloudflare.
  echo LOGIN_FALLO >> "%LOG%"
  pause
  exit /b 1
)
echo.
echo [2/4] Publicando el servidor de IA. Si pregunta por un subdominio workers.dev, escribe uno (por ejemplo tu nombre).
call npx --yes wrangler@4 deploy 2>&1 | powershell -NoProfile -Command "$input | Tee-Object -FilePath $env:TEMP\sp_deploy.txt"
type "%TEMP%\sp_deploy.txt" >> "%LOG%"
set "URL="
for /f "usebackq delims=" %%U in (`powershell -NoProfile -Command "(Select-String -Path $env:TEMP\sp_deploy.txt -Pattern 'https://[a-z0-9.-]+\.workers\.dev' | Select-Object -First 1).Matches.Value"`) do set "URL=%%U"
if "!URL!"=="" (
  echo No encontre la direccion del servidor en la salida. Revisa activar_ia_log.txt.
  echo SIN_URL >> "%LOG%"
  pause
  exit /b 1
)
echo URL=!URL! >> "%LOG%"
echo.
echo [3/4] Creando el codigo de acceso...
for /f "usebackq delims=" %%T in (`node -e "process.stdout.write(require('crypto').randomBytes(32).toString('base64url'))"`) do set "SP_TOKEN=%%T"
node -e "process.stdout.write(process.env.SP_TOKEN)" | call npx --yes wrangler@4 secret put APP_TOKEN >> "%LOG%" 2>&1
if errorlevel 1 (
  echo No se pudo guardar el codigo en Cloudflare. Revisa activar_ia_log.txt.
  echo SECRETO_FALLO >> "%LOG%"
  pause
  exit /b 1
)
echo.
echo [opcional] Pega tu clave de Gemini (Google AI Studio) con clic derecho y pulsa Enter.
echo Si no tienes, solo pulsa Enter: se usara la IA gratuita de Cloudflare.
set "GEMINI_KEY="
set /p "GEMINI_KEY=Clave de Gemini (o Enter): "
if not "!GEMINI_KEY!"=="" (
  node -e "process.stdout.write(process.env.GEMINI_KEY.trim())" | call npx --yes wrangler@4 secret put GEMINI_API_KEY >> "%LOG%" 2>&1
  if errorlevel 1 (echo No se pudo guardar la clave de Gemini; se seguira con la IA gratuita de Cloudflare. & echo GEMINI_FALLO >> "%LOG%") else (echo GEMINI_OK >> "%LOG%")
  set "GEMINI_KEY="
)
echo.
echo [4/4] Guardando la conexion para la app...
(
  echo endpoint=!URL!
  echo token=!SP_TOKEN!
) > "%~dp0..\android-app\ia_local.properties"
findstr /c:"ia_local.properties" "%~dp0..\.gitignore" >nul 2>nul || (echo android-app/ia_local.properties>> "%~dp0..\.gitignore")
echo Esperando 10 segundos a que el servidor quede listo...
timeout /t 10 >nul
curl -s -X POST "!URL!/chat" -H "Authorization: Bearer !SP_TOKEN!" -H "Content-Type: application/json" -d "{\"message\":\"Explicame en una frase que es la fotosintesis\",\"zoneId\":\"America/Lima\",\"now\":\"2026-11-03T15:00:00Z\"}" > "%TEMP%\sp_prueba.txt" 2>&1
echo PRUEBA: >> "%LOG%"
type "%TEMP%\sp_prueba.txt" >> "%LOG%"
echo.
echo Respuesta de prueba:
type "%TEMP%\sp_prueba.txt"
echo.
echo.
echo LISTO. Avisale a Claude para que compile la app con la IA conectada.
echo LISTO >> "%LOG%"
pause
endlocal
