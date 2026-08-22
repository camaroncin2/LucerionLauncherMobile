# ═══════════════════════════════════════════════════════════════════════════
# Crea la clave de firma de Lucerion Mobile (una sola vez).
#
# IMPORTANTE — sobre la contraseña:
#   · La eliges TÚ y no queda escrita en ningún archivo del repositorio.
#   · El script la pide de forma oculta; no aparece en el historial de la
#     consola ni en los registros.
#   · GUÁRDALA en tu gestor de contraseñas junto con una copia del archivo
#     lucerion-release.jks. Si pierdes cualquiera de los dos, NO podrás
#     publicar actualizaciones que Android reconozca como la misma app:
#     los jugadores tendrían que desinstalar y reinstalar.
#
# Uso:  .\scripts\crear-clave-firma.ps1
# ═══════════════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"

$raiz = Split-Path -Parent $PSScriptRoot
$destino = Join-Path $raiz "lucerion-release.jks"

if (Test-Path $destino) {
    Write-Host "Ya existe una clave en $destino" -ForegroundColor Yellow
    Write-Host "Si la reemplazas, las actualizaciones firmadas con la nueva clave NO"
    Write-Host "se instalaran sobre las apps ya publicadas. Borra el archivo a mano"
    Write-Host "solo si sabes lo que haces."
    exit 1
}

# keytool viene con el JDK; Android Studio trae uno propio.
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    $candidatos = @(
        "$env:JAVA_HOME\bin\keytool.exe",
        "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe"
    )
    $keytool = $candidatos | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $keytool) {
    Write-Host "No encontre keytool. Instala un JDK o Android Studio y reintenta." -ForegroundColor Red
    exit 1
}

Write-Host "Elige la contrasena de la clave de firma (no se mostrara)." -ForegroundColor Cyan
$secreta = Read-Host -AsSecureString "Contrasena"
$confirmar = Read-Host -AsSecureString "Reptela"

$b1 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secreta)
$b2 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirmar)
try {
    $texto1 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b1)
    $texto2 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b2)
    if ($texto1 -ne $texto2) {
        Write-Host "Las contrasenas no coinciden." -ForegroundColor Red
        exit 1
    }
    if ($texto1.Length -lt 8) {
        Write-Host "Usa al menos 8 caracteres." -ForegroundColor Red
        exit 1
    }

    & $keytool -genkeypair -v `
        -keystore $destino `
        -alias lucerion `
        -keyalg RSA -keysize 4096 -validity 10000 `
        -storepass $texto1 -keypass $texto1 `
        -dname "CN=Lucerion Studios, OU=Cretania, O=Lucerion Studios, L=Buenos Aires, C=AR"
    if ($LASTEXITCODE -ne 0) { throw "keytool fallo" }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b1)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b2)
}

Write-Host ""
Write-Host "Clave creada en: $destino" -ForegroundColor Green
Write-Host ""
Write-Host "Para compilar el APK firmado:" -ForegroundColor Cyan
Write-Host "  .\scripts\compilar-apk.ps1"
Write-Host ""
Write-Host "Te pedira esta contrasena de forma oculta. No queda escrita en ningun"
Write-Host "archivo del repositorio."
Write-Host ""
Write-Host "Copia de seguridad: guarda lucerion-release.jks y su contrasena en tu"
Write-Host "gestor de contrasenas. Sin ellos no podras publicar actualizaciones." -ForegroundColor Yellow
