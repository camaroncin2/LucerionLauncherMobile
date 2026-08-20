# Lucerion Launcher (Android)

Launcher de **Minecraft: Java Edition para Android** de Lucerion Studios,
hecho para jugar los modpacks del servidor **Cretania** (mc.cretania.net)
desde el teléfono, sin configurar nada a mano.

Es un fork de [Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher)
(FCL-Team), que a su vez se apoya en HMCL y PojavLauncher. Todo el mérito del
motor —JVM en Android, traducción OpenGL, controles táctiles— es de esos
proyectos. Lo que este fork aporta:

- **Interfaz íntegramente en español**, con cada opción explicada: qué hace,
  en qué beneficia y cuándo conviene activarla.
- **Sincronización con Lucerion**: el launcher lee el catálogo de modpacks del
  backend, crea la instancia, descarga los mods verificados y aplica la
  configuración probada (NeoForge + Zink/Turnip) automáticamente.
- **Estética Cretania**: tema steampunk con detalles dorados.
- Entrada directa al servidor con apodo, y cuenta Microsoft como opción.

## Licencia

GPL-3.0, igual que el proyecto original — ver [LICENSE](LICENSE). El código
fuente completo de este fork es público en este repositorio, como exige la
licencia. "Lucerion Launcher" es una distribución no oficial e independiente
de FCL; no está afiliada a FCL-Team ni a Mojang/Microsoft.

El README original de FCL está en [docs/README_FCL_upstream.md](docs/README_FCL_upstream.md).

## Arquitectura

La aplicación es propia; el motor es una dependencia.

| Módulo | Qué es | De quién |
|---|---|---|
| **`Lucerion/`** | **La app: todas las pantallas, flujos y estética. Kotlin + Compose, en español nativo** | **Lucerion Studios** |
| `FCLCore/` | Motor: versiones, loaders, cuentas, descargas (port de HMCL) | FCL-Team (dependencia) |
| `FCLauncher/` | Motor: JVM por JNI, renderers, superficie de juego | FCL-Team (dependencia) |
| `LWJGL/` | LWJGL portado a Android | FCL-Team (dependencia) |
| `FCL/` | La app original de FCL — solo referencia de integración, no se distribuye | FCL-Team |
| `docs/ESPECIFICACION.md` | Especificación de diseño | Lucerion Studios |

## Compilar

Proyecto Android estándar: abrir en Android Studio o `./gradlew assembleRelease`.
Requiere JDK 17+. El APK resultante es `arm64-v8a` principalmente.
