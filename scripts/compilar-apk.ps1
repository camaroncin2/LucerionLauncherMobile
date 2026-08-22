# ═══════════════════════════════════════════════════════════════════════════
# Compila el APK de Lucerion Mobile listo para subir a www.cretania.net.
#
# Antes de ejecutarlo, define la contrasena de la clave en ESTA sesion:
#   $env:LUCERION_KEYSTORE_PASSWORD = Read-Host -AsSecureString | ConvertFrom-SecureString -AsPlainText
#
# Uso:  .\scripts\compilar-apk.ps1
# ═══════════════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

$clave = Join-Path $raiz "lucerion-release.jks"
$firmado = $true

if (-not (Test-Path $clave)) {
    Write-Host "No hay clave de firma (lucerion-release.jks)." -ForegroundColor Yellow
    Write-Host "Crea una con: .\scripts\crear-clave-firma.ps1"
    Write-Host "Continuo generando un APK SIN FIRMAR (no instalable)." -ForegroundColor Yellow
    $firmado = $false
} elseif (-not $env:LUCERION_KEYSTORE_PASSWORD) {
    Write-Host "Falta la contrasena de la clave en esta sesion." -ForegroundColor Red
    Write-Host 'Define:  $env:LUCERION_KEYSTORE_PASSWORD = Read-Host -AsSecureString | ConvertFrom-SecureString -AsPlainText'
    exit 1
}

Write-Host "Compilando APK de release..." -ForegroundColor Cyan
& .\gradlew.bat :Lucerion:assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "La compilacion fallo." -ForegroundColor Red
    exit 1
}

$salida = Join-Path $raiz "Lucerion\build\outputs\apk\release"
$apk = Get-ChildItem $salida -Filter "*.apk" | Select-Object -First 1
if (-not $apk) {
    Write-Host "No se genero ningun APK en $salida" -ForegroundColor Red
    exit 1
}

# Nombre con version, para publicar sin confusiones.
$gradle = Get-Content (Join-Path $raiz "Lucerion\build.gradle.kts") -Raw
$version = if ($gradle -match 'versionName\s*=\s*"([^"]+)"') { $Matches[1] } else { "0.0.0" }
$destino = Join-Path $raiz "LucerionMobile-$version.apk"
Copy-Item $apk.FullName $destino -Force

$mb = [math]::Round((Get-Item $destino).Length / 1MB, 1)
Write-Host ""
if ($firmado) {
    Write-Host "APK FIRMADO listo: $destino ($mb MB)" -ForegroundColor Green
    Write-Host "Ya puedes subirlo a www.cretania.net."
} else {
    Write-Host "APK SIN FIRMAR: $destino ($mb MB)" -ForegroundColor Yellow
    Write-Host "No se puede instalar hasta firmarlo."
}
Write-Host ""
Write-Host "Nota para los jugadores: al ser una descarga directa (no Google Play),"
Write-Host "Android pedira permitir la instalacion desde el navegador la primera vez."
