@echo off
REM StudyPlanner AI Pet - compila la APK en esta PC (la version sale de app\build.gradle.kts).
REM La primera vez descarga las herramientas (JDK 17, Gradle 9.6.0 y el SDK de Android, unos 2 GB en total) desde sus
REM sitios oficiales y las deja en %USERPROFILE%\studyplanner-build. Las siguientes veces solo compila.
REM Deja en esta misma carpeta: la APK, construir_log.txt, errores_compilacion.txt y resumen_pruebas.txt.
REM Tambien copia la APK a la carpeta Descargas.
setlocal EnableDelayedExpansion
chcp 65001 >nul
set "AQUI=%~dp0"
set "BASE=%USERPROFILE%\studyplanner-build"
set "HERR=%BASE%\herramientas"
set "PROY=%BASE%\android-app"
set "LOG=%AQUI%construir_log.txt"
set "VER=nueva"
echo ==== %DATE% %TIME% ==== > "%LOG%"
if not exist "%HERR%" mkdir "%HERR%"

echo.
echo  StudyPlanner AI Pet - compilar la APK
echo  Carpeta de trabajo: %BASE%
echo.

REM ---------- 1. Proyecto ----------
set "HAYZIP="
for %%z in ("%AQUI%proyecto_*.zip") do set "HAYZIP=1"
if defined HAYZIP (
  echo [1/6] Copiando el proyecto...
  if not exist "%PROY%" mkdir "%PROY%"
  if not exist "%AQUI%aplicados" mkdir "%AQUI%aplicados"
  for %%z in ("%AQUI%proyecto_*.zip") do (
    tar -x -f "%%z" -C "%PROY%" >> "%LOG%" 2>&1
    set "RX=!ERRORLEVEL!"
    echo extraido %%~nxz=!RX! >> "%LOG%"
    if "!RX!"=="0" move /y "%%z" "%AQUI%aplicados\%%~nxz" >nul
  )
) else (
  echo [1/6] El proyecto ya estaba copiado.
)
if not exist "%PROY%\app\build.gradle.kts" (
  echo No encuentro el proyecto. Faltan los archivos proyecto_01.zip a proyecto_05.zip junto a este archivo.
  echo ERROR sin_proyecto >> "%LOG%"
  goto fin_error
)
set /a FALTAN=0
if exist "%PROY%\archivos_proyecto.txt" (
  for /f "usebackq delims=" %%f in ("%PROY%\archivos_proyecto.txt") do if not exist "%PROY%\%%f" (
    set /a FALTAN+=1
    echo FALTA %%f >> "%LOG%"
  )
) else (
  set /a FALTAN=1
  echo FALTA archivos_proyecto.txt >> "%LOG%"
)
echo archivos_que_faltan=!FALTAN! >> "%LOG%"
if not "!FALTAN!"=="0" (
  echo El proyecto quedo incompleto: faltan !FALTAN! archivos. Avisale a Claude.
  echo ERROR proyecto_incompleto >> "%LOG%"
  goto fin_error
)
if exist "%AQUI%parche.zip" (
  echo       Aplicando correcciones...
  tar -x -f "%AQUI%parche.zip" -C "%PROY%" >> "%LOG%" 2>&1
  echo parche_extraido=!ERRORLEVEL! >> "%LOG%"
  if not exist "%AQUI%aplicados" mkdir "%AQUI%aplicados"
  move /y "%AQUI%parche.zip" "%AQUI%aplicados\parche_%RANDOM%.zip" >nul
)
for /f "tokens=2 delims==" %%v in ('findstr /c:"versionName" "%PROY%\app\build.gradle.kts"') do set "VER=%%v"
set "VER=!VER: =!"
set "VER=!VER:"=!"
echo version=!VER! >> "%LOG%"
echo       Version: !VER!

REM ---------- 2. JDK 17 ----------
if not exist "%HERR%\jdk\bin\java.exe" (
  echo [2/6] Descargando Java ^(JDK 17, unos 190 MB^)...
  curl -L --fail --retry 3 -o "%HERR%\jdk.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
  if errorlevel 1 ( echo ERROR descarga_jdk >> "%LOG%" & echo No se pudo descargar Java. Revisa tu conexion a Internet. & goto fin_error )
  tar -x -f "%HERR%\jdk.zip" -C "%HERR%" >> "%LOG%" 2>&1
  for /d %%d in ("%HERR%\jdk-17*") do if not exist "%HERR%\jdk" ren "%%d" jdk
  del /q "%HERR%\jdk.zip" >nul 2>&1
) else (
  echo [2/6] Java ya esta.
)
if not exist "%HERR%\jdk\bin\java.exe" ( echo ERROR jdk_no_quedo >> "%LOG%" & echo Java no quedo instalado. & goto fin_error )
set "JAVA_HOME=%HERR%\jdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM ---------- 3. Gradle 9.6.0 ----------
if not exist "%HERR%\gradle-9.6.0\bin\gradle.bat" (
  echo [3/6] Descargando Gradle 9.6.0 ^(unos 140 MB^)...
  curl -L --fail --retry 3 -o "%HERR%\gradle.zip" "https://services.gradle.org/distributions/gradle-9.6.0-bin.zip"
  if errorlevel 1 ( echo ERROR descarga_gradle >> "%LOG%" & echo No se pudo descargar Gradle. & goto fin_error )
  tar -x -f "%HERR%\gradle.zip" -C "%HERR%" >> "%LOG%" 2>&1
  del /q "%HERR%\gradle.zip" >nul 2>&1
) else (
  echo [3/6] Gradle ya esta.
)
if not exist "%HERR%\gradle-9.6.0\bin\gradle.bat" ( echo ERROR gradle_no_quedo >> "%LOG%" & echo Gradle no quedo instalado. & goto fin_error )
set "GRADLE=%HERR%\gradle-9.6.0\bin\gradle.bat"
set "GRADLE_USER_HOME=%BASE%\gradle-home"

REM ---------- 4. SDK de Android ----------
set "ANDROID_HOME=%HERR%\sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "SDKM=%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat"
if not exist "%SDKM%" (
  echo [4/6] Descargando las herramientas de Android ^(unos 160 MB^)...
  curl -L --fail --retry 3 -o "%HERR%\cmdline.zip" "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip"
  if errorlevel 1 ( echo ERROR descarga_sdk >> "%LOG%" & echo No se pudieron descargar las herramientas de Android. & goto fin_error )
  set "SUMA="
  for /f "skip=1 tokens=* delims=" %%h in ('certutil -hashfile "%HERR%\cmdline.zip" SHA256') do if not defined SUMA set "SUMA=%%h"
  set "SUMA=!SUMA: =!"
  echo suma_cmdline=!SUMA! >> "%LOG%"
  if /i not "!SUMA!"=="90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a" (
    echo ERROR suma_sdk_distinta >> "%LOG%"
    echo La descarga de las herramientas de Android no coincide con la oficial. No se usa.
    del /q "%HERR%\cmdline.zip" >nul 2>&1
    goto fin_error
  )
  if not exist "%ANDROID_HOME%\cmdline-tools" mkdir "%ANDROID_HOME%\cmdline-tools"
  tar -x -f "%HERR%\cmdline.zip" -C "%ANDROID_HOME%\cmdline-tools" >> "%LOG%" 2>&1
  if exist "%ANDROID_HOME%\cmdline-tools\cmdline-tools" ren "%ANDROID_HOME%\cmdline-tools\cmdline-tools" latest
  del /q "%HERR%\cmdline.zip" >nul 2>&1
) else (
  echo [4/6] Las herramientas de Android ya estan.
)
if not exist "%SDKM%" ( echo ERROR sdk_no_quedo >> "%LOG%" & echo Las herramientas de Android no quedaron instaladas. & goto fin_error )
if not exist "%ANDROID_HOME%\licenses\android-sdk-license" (
  echo.
  echo  Android pide aceptar sus licencias para poder compilar.
  echo  Se van a mostrar una por una. Para aceptar, escribe  y  y presiona Enter cada vez.
  echo.
  pause
  call "%SDKM%" --licenses
)
if not exist "%ANDROID_HOME%\licenses\android-sdk-license" ( echo ERROR licencias_no_aceptadas >> "%LOG%" & echo Sin aceptar las licencias no se puede compilar. & goto fin_error )
if not exist "%ANDROID_HOME%\platforms\android-36" (
  echo       Descargando la plataforma Android 36...
  call "%SDKM%" "platforms;android-36" "platform-tools" >> "%LOG%" 2>&1
  echo sdk_plataforma=!ERRORLEVEL! >> "%LOG%"
)
set "SDKP=%ANDROID_HOME:\=/%"
REM En un archivo .properties los dos puntos de la unidad van con barra invertida (C\:/...).
set "SDKP=%SDKP::=\:%"
> "%PROY%\local.properties" echo sdk.dir=%SDKP%

REM ---------- 5. Compilar ----------
set "APK=%PROY%\app\build\outputs\apk\debug\app-debug.apk"
if exist "%APK%" del /q "%APK%"
echo [5/6] Compilando la app. La primera vez descarga librerias y tarda entre 10 y 30 minutos...
pushd "%PROY%"
call "%GRADLE%" --no-daemon --console=plain :app:assembleDebug >> "%LOG%" 2>&1
set "RC=!ERRORLEVEL!"
popd
echo gradle_codigo=!RC! >> "%LOG%"
findstr /b /c:"e: " /c:"* What went wrong" /c:"BUILD " /c:"FAILURE" /c:"Execution failed" /c:"Could not" "%LOG%" > "%AQUI%errores_compilacion.txt"
if not exist "%APK%" (
  echo APK_NUEVA=no >> "%LOG%"
  echo.
  echo  La compilacion no termino bien. El detalle quedo en errores_compilacion.txt.
  echo  Avisale a Claude para que lo revise.
  goto fin_error
)
echo APK_NUEVA=si >> "%LOG%"
copy /y "%APK%" "%AQUI%StudyPlanner-AI-Pet-%VER%-debug.apk" >nul
if exist "%USERPROFILE%\Downloads" copy /y "%APK%" "%USERPROFILE%\Downloads\StudyPlanner-AI-Pet-%VER%-debug.apk" >nul
echo       APK lista: StudyPlanner-AI-Pet-%VER%-debug.apk ^(tambien en Descargas^)

REM ---------- 6. Pruebas y lint ----------
echo [6/6] Ejecutando las pruebas y el lint ^(unos minutos^)...
pushd "%PROY%"
call "%GRADLE%" --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug > "%AQUI%pruebas_lint_log.txt" 2>&1
echo pruebas_lint_codigo=!ERRORLEVEL! >> "%LOG%"
popd
if exist "%PROY%\app\build\reports\lint-results-debug.txt" copy /y "%PROY%\app\build\reports\lint-results-debug.txt" "%AQUI%lint_%VER%.txt" >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t=0;$f=0;$e=0;$s=0;$n=0;$m=@(); Get-ChildItem '%PROY%\app\build\test-results\testDebugUnitTest' -Filter *.xml -ErrorAction SilentlyContinue | ForEach-Object { [xml]$x=Get-Content $_.FullName -Encoding UTF8; $n++; $t+=[int]$x.testsuite.tests; $f+=[int]$x.testsuite.failures; $e+=[int]$x.testsuite.errors; $s+=[int]$x.testsuite.skipped; foreach($c in $x.testsuite.testcase){ if($c.failure -or $c.error){ $m+=($x.testsuite.name+'.'+$c.name) } } }; 'clases_de_prueba='+$n; 'pruebas='+$t; 'fallas='+$f; 'errores='+$e; 'omitidas='+$s; $m | ForEach-Object { 'FALLA: '+$_ }" > "%AQUI%resumen_pruebas.txt" 2>&1
findstr /c:"BUILD " /c:"Lint found" /c:"FAILED" /c:"errors, " "%AQUI%pruebas_lint_log.txt" >> "%AQUI%resumen_pruebas.txt"
echo FIN codigo=!RC! >> "%LOG%"
echo.
echo  LISTO. La APK esta en Descargas y en esta carpeta: StudyPlanner-AI-Pet-%VER%-debug.apk
echo  El resultado de las pruebas esta en resumen_pruebas.txt
echo.
pause
endlocal
exit /b 0

:fin_error
echo FIN con_error >> "%LOG%"
echo.
pause
endlocal
exit /b 1
