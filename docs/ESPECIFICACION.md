# Especificación de diseño — Lucerion Launcher

Qué tiene que ser este launcher y por qué. Todo cambio de UX del fork se
contrasta contra este documento.

## El problema que corrige (palabras del propietario)

FCL de serie está en inglés y "tenía muchas opciones las cuales o no tenían
explicación o directamente no se comprendían". El objetivo del fork es una app
**intuitiva y de fácil acceso**, bien distribuida, donde **todas las opciones
tengan una breve explicación de qué hacen, en qué benefician y en qué caso
deberían activarse**.

## Principios

1. **Español primero.** Todo el texto en español. Solo quedan sin traducir los
   nombres propios sin traducción (Zink, Turnip, NeoForge, Vulkan, shaders,
   OptiFine…). El español es el idioma por defecto de la app.
2. **Ninguna opción sin explicar.** Cada ajuste lleva una línea de "qué hace"
   y, cuando aplica, "cuándo activarlo". Si una opción no se puede explicar en
   dos frases, probablemente pertenece a la sección Avanzado.
3. **El camino feliz no requiere decisiones.** Un jugador nuevo abre la app,
   toca "Jugar Cretania", elige apodo, y entra. Todas las decisiones técnicas
   (instancia, loader, renderer, RAM, mods) salen de la configuración de
   referencia validada en la Fase 0 — no del usuario.
4. **Lo avanzado no desaparece: se aparta.** Los ajustes completos de FCL
   siguen existiendo en una sección "Avanzado" claramente separada, para
   quien sabe lo que toca.
5. **Estética Cretania.** Tema steampunk elegante: fondos navy oscuro, acentos
   dorados/latón para resaltar, detalles mecánicos discretos. La paleta de
   marca vive en el brief de diseño de Cretania (repo lucerion-private,
   `Brief-Diseno-CreateCretania-x-ElPercxy.md`): fondo #111C31 y familia,
   dorados como color de acción.

## Configuración de referencia (validada en dispositivo, 2026-08-19)

Motorola edge 40 pro (SD 8 Gen 2 / Adreno 740, 12 GB) → 41-46 FPS dentro del
servidor con 172 mods:

| Ajuste | Valor que el launcher aplica solo |
|---|---|
| Instancia | Minecraft 1.21.1 + NeoForge (versión del manifest) |
| Java | JRE 21 integrado |
| Renderer | Kopper Zink (OpenGL 4.6) |
| Driver Vulkan | Turnip |
| Memoria | Asignación automática |
| Mods | Del backend Lucerion, verificados por SHA-1 |

Datos operativos del backend:
- Manifest: `http://103.195.100.133:3100/manifest.json` (HTTP plano: requiere
  `usesCleartextTraffic` o network security config hasta que haya dominio TLS)
- Set de instalación: `/api/public/modpack/<id>/install-info` (kind=mod,
  campo `file` = ruta relativa, `sha1` para verificar)
- El servidor autentica con AuthMod: **cuenta offline con apodo es
  suficiente**; el login Microsoft se ofrece como opción, no como barrera.
- Poda móvil respecto al pack de escritorio (hasta que exista `cretania-mobile`
  en el panel): Ixeris, particlerain, minecraft-cursor, forgematica, EMF, ETF
  y el balm duplicado (21.0.56). La familia Sodium + Iris SE QUEDAN (iris
  requiere sodium en runtime; colorwheel requiere iris).

## Decisiones técnicas del fork

- `applicationId` = `com.lucerion.launcher` (instalable junto a FCL).
  El namespace de código sigue siendo `com.tungsten.fcl`: los símbolos JNI del
  módulo nativo se atan a los nombres de clase Java y renombrarlos rompería
  la capa nativa sin ganancia.
- El fork es GPL-3 y este repo es público: es la obligación de la licencia y
  la condición para poder distribuir el APK.
- Upstream FCL queda como remote `upstream` para traer correcciones del motor.

## Hoja de ruta

1. ~~Base FCL con historial + identidad Lucerion~~ (este commit)
2. Traducción es completa (~1233 cadenas) con explicaciones mejoradas, español
   por defecto
3. Tema steampunk dorado (colores por defecto, icono, splash)
4. Pantalla de inicio "Jugar Cretania" + módulo de sincronización Lucerion
5. QA en dispositivos + publicación del APK en www.cretania.net
