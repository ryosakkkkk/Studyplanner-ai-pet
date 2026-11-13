# Prueba del tutor con IA: envia al servidor de la app las mismas peticiones que arma la pagina del camino
# y guarda las respuestas en prueba_tutor_ia.txt. No escribe la direccion ni el codigo de acceso en ningun archivo.
$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$prop = Join-Path $env:USERPROFILE 'studyplanner-build\android-app\ia_local.properties'
$out = Join-Path $aqui 'prueba_tutor_ia.txt'
$cfg = @{}
if (Test-Path $prop) { Get-Content $prop | ForEach-Object { $i = $_.IndexOf('='); if ($i -gt 0) { $cfg[$_.Substring(0, $i).Trim()] = $_.Substring($i + 1).Trim() } } }
if (-not $cfg['endpoint'] -or -not $cfg['token']) { 'No esta la conexion de IA del proyecto (ia_local.properties).' | Set-Content -Encoding UTF8 $out; exit 1 }
$pets = Get-Content -Raw -Encoding UTF8 (Join-Path $aqui 'prueba_tutor_peticiones.json') | ConvertFrom-Json
$lineas = @('Prueba del tutor con IA - ' + (Get-Date -Format 'yyyy-MM-dd HH:mm'))
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
foreach ($p in $pets) {
  $body = @{ message = [string]$p.mensaje; zoneId = 'America/Lima'; now = (Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz') } | ConvertTo-Json -Compress
  $t = Get-Date
  try {
    $r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri ($cfg['endpoint'].TrimEnd('/') + '/chat') -Headers @{ Authorization = 'Bearer ' + $cfg['token'] } -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 40
    $txt = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    $j = $txt | ConvertFrom-Json
    $ms = [int]((Get-Date) - $t).TotalMilliseconds
    $lineas += "[$($p.nombre)] HTTP $($r.StatusCode) en $ms ms | kind=$($j.kind) | peticion de $($p.mensaje.Length) caracteres | respuesta de $($j.text.Length)"
    if ($p.correcta) { $lineas += "  (respuesta correcta de la pregunta: $($p.correcta))" }
    $lineas += '  ' + $j.text
  } catch {
    $code = ''
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    $msg = ($_.Exception.Message -replace [regex]::Escape($cfg['token']), '***' -replace [regex]::Escape($cfg['endpoint']), '(servidor)')
    $lineas += "[$($p.nombre)] FALLO HTTP $code : $msg"
  }
  Start-Sleep -Seconds 6
}
$lineas | Set-Content -Encoding UTF8 $out
