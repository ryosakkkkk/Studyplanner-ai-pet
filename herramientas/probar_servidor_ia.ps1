# Mide cuanto tarda el servidor de IA de la app y que informa en /health. No escribe la direccion ni el codigo de acceso.
$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
$prop = Join-Path $env:USERPROFILE 'studyplanner-build\android-app\ia_local.properties'
$out = Join-Path $aqui 'prueba_servidor_ia.txt'
$cfg = @{}
if (Test-Path $prop) { Get-Content $prop | ForEach-Object { $i = $_.IndexOf('='); if ($i -gt 0) { $cfg[$_.Substring(0, $i).Trim()] = $_.Substring($i + 1).Trim() } } }
if (-not $cfg['endpoint'] -or -not $cfg['token']) { 'No esta la conexion de IA del proyecto (ia_local.properties).' | Set-Content -Encoding UTF8 $out; exit 1 }
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$base = $cfg['endpoint'].TrimEnd('/')
$lineas = @('Prueba del servidor de IA - ' + (Get-Date -Format 'yyyy-MM-dd HH:mm'))
function Limpia([string]$s) { return ($s -replace [regex]::Escape($cfg['token']), '***' -replace [regex]::Escape($cfg['endpoint']), '(servidor)') }
function Pide([string]$nombre, [string]$metodo, [string]$ruta, $cuerpo) {
  $t = Get-Date
  try {
    $p = @{ UseBasicParsing = $true; Method = $metodo; Uri = ($base + $ruta); TimeoutSec = 45; Headers = @{ Authorization = 'Bearer ' + $cfg['token'] } }
    if ($cuerpo) { $p['ContentType'] = 'application/json; charset=utf-8'; $p['Body'] = [Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Compress)) }
    $r = Invoke-WebRequest @p
    $txt = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    if ($txt.Length -gt 400) { $txt = $txt.Substring(0, 400) }
    return "[$nombre] HTTP $($r.StatusCode) en $([int]((Get-Date) - $t).TotalMilliseconds) ms : " + (Limpia $txt)
  } catch {
    $code = ''
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    return "[$nombre] FALLO HTTP $code en $([int]((Get-Date) - $t).TotalMilliseconds) ms : " + (Limpia $_.Exception.Message)
  }
}
$lineas += Pide 'health' 'Get' '/health' $null
$ahora = { (Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz') }
$lineas += Pide 'chat corto 1' 'Post' '/chat' @{ message = 'Hola. En una frase: que es un sustantivo?'; zoneId = 'America/Lima'; now = (& $ahora) }
Start-Sleep -Seconds 6
$lineas += Pide 'chat corto 2' 'Post' '/chat' @{ message = 'Cuanto es 12 por 12? Responde solo el numero.'; zoneId = 'America/Lima'; now = (& $ahora) }
Start-Sleep -Seconds 6
$lineas += Pide 'check' 'Post' '/check' @{}
$lineas | Set-Content -Encoding UTF8 $out
