# ═══════════════════════════════════════════════════════════════════════════
# Banco de pruebas de Lucerion Mobile.
#
# POR QUE EXISTE
#   Sin esto, comparar rendimiento es adivinar: "48 de media" contra "36 de
#   media" son numeros de escenas distintas, a temperaturas distintas y con
#   ajustes distintos. Este script mide siempre lo mismo, de la misma forma,
#   y deja un CSV para poder poner dos ejecuciones una al lado de la otra.
#
#   Lo importante NO es el pico: es como decae. El equipo recorta la velocidad
#   del procesador al calentarse, asi que la medida que vale es la del tercio
#   final comparada con la del primero.
#
# COMO SE USA
#   1. Arranca la partida y ponte en un sitio FIJO y reconocible (el spawn
#      sirve). No te muevas durante la medicion: mover cambia la carga.
#   2. .\scripts\banco-pruebas.ps1 -Etiqueta "base-100"
#   3. Cambia UNA sola cosa, reinicia la partida, y repite con otra etiqueta.
#
#   Empieza siempre en frio (teléfono reposado unos minutos). Medir sobre un
#   equipo ya caliente compara recortes termicos, no cambios de codigo.
# ═══════════════════════════════════════════════════════════════════════════

param(
    [Parameter(Mandatory = $true)][string]$Etiqueta,
    [int]$Minutos = 10,
    [int]$IntervaloSegundos = 15,
    [string]$Serie = ""
)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot

# ── adb ─────────────────────────────────────────────────────────────────────
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $candidatos = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "F:\Android\Sdk\platform-tools\adb.exe",
        "$env:ProgramFiles\Android\Android Studio\platform-tools\adb.exe"
    )
    $adb = $candidatos | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $adb) { Write-Host "No encontre adb." -ForegroundColor Red; exit 1 }

if (-not $Serie) {
    # @() a proposito: con un solo dispositivo PowerShell devuelve una CADENA,
    # no una lista, y $lineas[0] daria su primera letra en vez de la fila.
    $lineas = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" })
    if (-not $lineas) { Write-Host "No hay ningun dispositivo conectado." -ForegroundColor Red; exit 1 }
    if ($lineas.Count -gt 1) {
        Write-Host "Hay varios dispositivos; usa -Serie. Encontrados:" -ForegroundColor Yellow
        $lineas | ForEach-Object { Write-Host "  $_" }
        exit 1
    }
    $Serie = ($lineas[0] -split "\s+")[0]
}
Write-Host "Dispositivo: $Serie" -ForegroundColor Cyan

function Sh([string]$cmd) { (& $adb -s $Serie shell $cmd) -join "`n" }

# ── la partida tiene que estar viva ─────────────────────────────────────────
$pid_juego = (Sh "pidof com.lucerion.launcher:juego").Trim()
if (-not $pid_juego) {
    Write-Host "No hay ninguna partida corriendo." -ForegroundColor Red
    Write-Host "Entra a Cretania, ponte en un sitio fijo y vuelve a lanzar esto."
    exit 1
}
Write-Host "Partida encontrada (proceso $pid_juego)" -ForegroundColor Green

# ── capa del juego, para leer fotogramas REALES del compositor ──────────────
# El contador de la pantalla es del propio juego; estos tiempos son los que
# el sistema presento de verdad, que es lo que ve el jugador.
$capa = ""
$listado = Sh "dumpsys SurfaceFlinger --list"
foreach ($l in ($listado -split "`n")) {
    if ($l -match "JuegoActivity#\d+") { $capa = $Matches[0]; }
}
if ($capa) {
    Write-Host "Midiendo fotogramas del compositor: $capa" -ForegroundColor Green
} else {
    Write-Host "No encontre la capa del juego; los FPS quedaran vacios." -ForegroundColor Yellow
    Write-Host "(el resto de medidas si se registran)"
}

# ── sonda ───────────────────────────────────────────────────────────────────
# Va como ARCHIVO al telefono, no como linea de comandos.
#
# PowerShell termina las lineas con retorno de carro y salto (\r\n), y el
# interprete de Android trata el \r como parte del comando: un bucle se rompia
# con "syntax error: unexpected do" en cada muestra. Escribiendo el archivo con
# saltos de linea de Unix y empujandolo con adb push, el problema desaparece y
# el script se puede leer entero, que con un one-liner gigante no pasaba.
#
# Comilla simple en el here-string a proposito: asi PowerShell NO toca los $,
# que son variables del interprete del telefono, no suyas.
$sonda = @'
#!/system/bin/sh
# $1 = PID del proceso del juego
cmax=0; gpu=0; piel=0
for z in /sys/class/thermal/thermal_zone*; do
  n=$(cat $z/type 2>/dev/null); t=$(cat $z/temp 2>/dev/null)
  [ -z "$t" ] && continue
  case "$n" in
    cpu-*) [ "$t" -gt "$cmax" ] && cmax=$t ;;
    gpuss-0) gpu=$t ;;
    back_temp) piel=$t ;;
  esac
done
cd2=0; cdg=0
for c in /sys/class/thermal/cooling_device*; do
  ty=$(cat $c/type 2>/dev/null)
  case "$ty" in
    cpu-cluster2) cd2=$(cat $c/cur_state) ;;
    gpu) cdg=$(cat $c/cur_state) ;;
  esac
done
st=$(dumpsys thermalservice | grep -m1 'Thermal Status' | tr -dc '0-9')
bat=$(dumpsys battery | grep -m1 temperature | tr -dc '0-9')
ma=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
rss=$(grep VmRSS /proc/$1/status 2>/dev/null | tr -dc '0-9')
swp=$(grep VmSwap /proc/$1/status 2>/dev/null | tr -dc '0-9')
disp=$(grep MemAvailable /proc/meminfo | tr -dc '0-9')
cpu=$(top -n 1 -b -q -o PID,%CPU 2>/dev/null | awk -v p=$1 '$1==p {print $2; exit}')
prime=$(cat /sys/devices/system/cpu/cpu7/cpufreq/scaling_max_freq 2>/dev/null)
echo "$((cmax/1000));$((gpu/1000));$((piel/1000));$((bat/10));$st;$cd2;$cdg;$ma;$rss;$swp;$disp;$cpu;$((prime/1000))"
'@

$sondaLocal = Join-Path $env:TEMP "lucerion-sonda.sh"
# WriteAllText y no Out-File: hace falta control total sobre los fines de linea.
[System.IO.File]::WriteAllText($sondaLocal, ($sonda -replace "`r`n", "`n"))
$sondaRemota = "/data/local/tmp/lucerion-sonda.sh"
& $adb -s $Serie push $sondaLocal $sondaRemota | Out-Null
Sh "chmod 755 $sondaRemota" | Out-Null

# Comprobacion antes de perder diez minutos midiendo la nada.
$ensayo = (Sh "sh $sondaRemota $pid_juego").Trim()
if (($ensayo -split ";").Count -lt 13) {
    Write-Host "La sonda no responde bien. Devolvio:" -ForegroundColor Red
    Write-Host "  $ensayo"
    exit 1
}
Write-Host "Sonda verificada" -ForegroundColor Green

function LeerFps {
    if (-not $capa) { return "" }
    $salida = Sh "dumpsys SurfaceFlinger --latency '$capa'"
    $filas = @()
    foreach ($l in ($salida -split "`n")) {
        $p = $l.Trim() -split "\s+"
        if ($p.Count -eq 3) {
            $t = 0L
            if ([int64]::TryParse($p[1], [ref]$t) -and $t -gt 0 -and $t -lt 9223372036854775807) { $filas += $t }
        }
    }
    if ($filas.Count -lt 10) { return "" }
    $span = ($filas[-1] - $filas[0]) / 1e9
    if ($span -le 0) { return "" }
    return [math]::Round(($filas.Count - 1) / $span, 1)
}

# ── medicion ────────────────────────────────────────────────────────────────
$carpeta = Join-Path $raiz "banco"
if (-not (Test-Path $carpeta)) { New-Item -ItemType Directory -Path $carpeta | Out-Null }
$marca = Get-Date -Format "yyyyMMdd-HHmmss"
$csv = Join-Path $carpeta "$Etiqueta-$marca.csv"
"muestra;segundos;fps;cpu_c;gpu_c;piel_c;bat_c;estado_termico;recorte_cpu;recorte_gpu;mA;rss_mb;swap_mb;libre_mb;cpu_pct;prime_mhz" |
    Out-File -FilePath $csv -Encoding utf8

$total = [math]::Max(1, [int](($Minutos * 60) / $IntervaloSegundos))
Write-Host ""
Write-Host "Midiendo $Minutos min ($total muestras cada $IntervaloSegundos s). No toques el telefono." -ForegroundColor Cyan
Write-Host ""

$inicio = Get-Date
$muestras = @()
for ($i = 1; $i -le $total; $i++) {
    $fps = LeerFps
    $crudo = (Sh "sh $sondaRemota $pid_juego").Trim()
    $c = $crudo -split ";"
    if ($c.Count -lt 13) { Write-Host "[$i] sonda incompleta, salto" -ForegroundColor Yellow; Start-Sleep -Seconds $IntervaloSegundos; continue }

    $seg = [int]((Get-Date) - $inicio).TotalSeconds
    $rssMb = 0; if ($c[8]) { $rssMb = [int]([int64]$c[8] / 1024) }
    $swpMb = 0; if ($c[9]) { $swpMb = [int]([int64]$c[9] / 1024) }
    $libMb = 0; if ($c[10]) { $libMb = [int]([int64]$c[10] / 1024) }
    $mA = 0; if ($c[7]) { $mA = [int]([int64]$c[7] / 1000) }

    "$i;$seg;$fps;$($c[0]);$($c[1]);$($c[2]);$($c[3]);$($c[4]);$($c[5]);$($c[6]);$mA;$rssMb;$swpMb;$libMb;$($c[11]);$($c[12])" |
        Out-File -FilePath $csv -Append -Encoding utf8

    $muestras += [pscustomobject]@{
        fps = $(if ($fps -ne "") { [double]$fps } else { $null })
        cpu = [int]$c[0]; recorte = [int]$c[5]; rss = $rssMb; mA = $mA
    }

    $txtFps = $(if ($fps -ne "") { "$fps fps" } else { "  -   " })
    Write-Host ("[{0,3}/{1}] {2,8}  cpu {3,2}C  recorte {4,2}/20  rss {5,4} MB  {6,5} mA" -f `
        $i, $total, $txtFps, $c[0], $c[5], $rssMb, $mA)

    if ($i -lt $total) { Start-Sleep -Seconds $IntervaloSegundos }
}

# ── resumen: lo que importa es el decaimiento ───────────────────────────────
function Med($valores) {
    $v = @($valores | Where-Object { $_ -ne $null } | Sort-Object)
    if ($v.Count -eq 0) { return $null }
    return $v[[int]($v.Count / 2)]
}

$n = $muestras.Count
if ($n -lt 3) { Write-Host "Muy pocas muestras para resumir." -ForegroundColor Yellow; exit 0 }
$tercio = [math]::Max(1, [int]($n / 3))
$primero = $muestras[0..($tercio - 1)]
$ultimo = $muestras[($n - $tercio)..($n - 1)]

Write-Host ""
Write-Host "──────────────────────────────────────────────" -ForegroundColor Cyan
Write-Host " $Etiqueta" -ForegroundColor Cyan
Write-Host "──────────────────────────────────────────────" -ForegroundColor Cyan
$fA = Med ($primero.fps); $fB = Med ($ultimo.fps)
if ($fA -ne $null -and $fB -ne $null) {
    $caida = [math]::Round((1 - ($fB / $fA)) * 100, 1)
    Write-Host (" FPS   primer tercio {0,6}   ultimo {1,6}   caida {2} %" -f $fA, $fB, $caida)
} else {
    Write-Host " FPS   sin datos del compositor"
}
Write-Host (" CPU   primer tercio {0,5} C   ultimo {1,5} C" -f (Med ($primero.cpu)), (Med ($ultimo.cpu)))
Write-Host (" Recorte del nucleo principal  {0,2}/20  ->  {1,2}/20" -f (Med ($primero.recorte)), (Med ($ultimo.recorte)))
Write-Host (" Memoria residente  {0,5} MB  ->  {1,5} MB" -f (Med ($primero.rss)), (Med ($ultimo.rss)))
Write-Host (" Consumo medio      {0,5} mA  ->  {1,5} mA" -f (Med ($primero.mA)), (Med ($ultimo.mA)))
Write-Host ""
Write-Host "Datos completos: $csv" -ForegroundColor Green
Write-Host ""
Write-Host "Para comparar, repite cambiando UNA sola cosa y con el equipo igual de frio." -ForegroundColor Yellow
