# Publicar Lucerion Mobile

Distribución manual desde **www.cretania.net**, igual que el launcher de
escritorio: sin tiendas, sin cuentas de desarrollador, sin actualizaciones
automáticas.

## Una sola vez: crear la clave de firma

```powershell
.\scripts\crear-clave-firma.ps1
```

El script pide una contraseña **que eliges tú** (oculta, no queda en el
historial de la consola) y genera `lucerion-release.jks` en la raíz del
repositorio. Ese archivo está en `.gitignore`: **nunca** se sube (el repo es
público por la licencia GPL-3).

> **Guarda dos cosas en tu gestor de contraseñas: el archivo `.jks` y su
> contraseña.** Android identifica una app por su firma: si pierdes la clave,
> las futuras actualizaciones no se instalarán sobre la app existente y los
> jugadores tendrían que desinstalar y volver a instalar.

## Cada vez: compilar el APK

En la sesión de consola, define la contraseña (así no vive en ningún archivo):

```powershell
$env:LUCERION_KEYSTORE_PASSWORD = Read-Host -AsSecureString | ConvertFrom-SecureString -AsPlainText
```

Y compila:

```powershell
.\scripts\compilar-apk.ps1
```

Resultado: `LucerionMobile-<versión>.apk` en la raíz, listo para subir. Pesa
~185 MB porque incluye el entorno Java 21 y los componentes gráficos: la app
funciona sin descargas extra más allá del modpack.

## Subir una versión nueva

1. Sube `versionCode` (entero, +1) y `versionName` en
   `Lucerion/build.gradle.kts`. Android **exige** que `versionCode` crezca:
   sin eso, el APK nuevo no se instala sobre el viejo.
2. Compila con el script.
3. Sube el APK a www.cretania.net junto a las versiones de Windows y Linux.

## Qué contarle al jugador

- Es una descarga directa, no viene de Google Play: al abrir el APK, Android
  pedirá permitir la instalación desde el navegador. Es un permiso por app y
  se puede revocar después.
- La primera entrada descarga el modpack y el juego (~1 GB): conviene wifi.
- Necesita ~3 GB de RAM libres para jugar con soltura; en Ajustes →
  Rendimiento la app indica la memoria recomendada de su equipo y no conviene
  subirla: medido en un Edge 40 Pro (11.5 GB), reservar el 40 % daba 5.8 GB
  residentes y Android cerraba la partida al abrir cualquier otra app pesada,
  mientras que con el 22 % rinde igual (43 FPS, media 44) y sobrevive.
- La partida corre en su propia ventana, separada del launcher: aparecen dos
  entradas en la lista de recientes. Cerrar el launcher no la afecta; al
  minimizarla se queda viva y va devolviendo memoria al sistema.

## Alternativa descartada (por ahora)

Compilación automática en GitHub Actions: implicaría guardar la clave de firma
como secreto del repositorio. Se puede añadir más adelante sin cambiar nada de
lo anterior — la configuración de firma ya lee variables de entorno.
