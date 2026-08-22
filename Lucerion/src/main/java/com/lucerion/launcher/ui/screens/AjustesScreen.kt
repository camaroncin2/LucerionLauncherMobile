package com.lucerion.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lucerion.launcher.R
import com.lucerion.launcher.data.EspacioJuego
import com.lucerion.launcher.data.RepositorioAjustes
import com.lucerion.launcher.motor.InstaladorJuego
import com.lucerion.launcher.ui.theme.Bg
import com.lucerion.launcher.ui.theme.Bg2
import com.lucerion.launcher.ui.theme.Bg3
import com.lucerion.launcher.ui.theme.Oro
import com.lucerion.launcher.ui.theme.OroClaro
import com.lucerion.launcher.ui.theme.OroProfundo
import com.lucerion.launcher.ui.theme.TextoSuave
import java.io.File

/**
 * Pantalla de Ajustes — la razón de ser de este launcher: CADA opción dice
 * qué hace y cuándo conviene tocarla, en español llano. Nada de menús que
 * solo entiende quien ya sabe.
 */
@Composable
fun AjustesScreen(alVolver: () -> Unit, versionApp: String = "") {
    val contexto = LocalContext.current

    var usarCruceta by remember { mutableStateOf(RepositorioAjustes.usarCruceta(contexto)) }
    var escala by remember { mutableFloatStateOf(RepositorioAjustes.escalaControles(contexto)) }
    var memoriaMb by remember { mutableIntStateOf(RepositorioAjustes.memoriaMb(contexto)) }
    var vsync by remember { mutableStateOf(RepositorioAjustes.vsync(contexto)) }
    var presentacion by remember { mutableStateOf(RepositorioAjustes.presentacionVulkan(contexto)) }
    var sinLrz by remember { mutableStateOf(RepositorioAjustes.turnipSinLrz(contexto)) }
    var sysmem by remember { mutableStateOf(RepositorioAjustes.turnipSysmem(contexto)) }
    var reparacionPedida by remember { mutableStateOf(false) }
    var apartado by remember { mutableStateOf<String?>(null) }

    // El boton atras de Android debe subir un nivel, igual que el enlace de
    // la cabecera: sin esto cerraba la app desde un apartado.
    androidx.activity.compose.BackHandler {
        if (apartado == null) alVolver() else apartado = null
    }

    // Cada apartado empieza arriba: heredar el desplazamiento del anterior
    // dejaba al jugador a media pantalla.
    val estadoScroll = androidx.compose.foundation.rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(apartado) { estadoScroll.scrollTo(0) }

    // Datos reales del equipo para orientar la memoria: recomendada = 22 %
    // de la RAM; limite seguro = 30 %. Cifras medidas en dispositivo, no
    // estimadas: con el 40 % (lo que habia antes) el sistema entraba en
    // falta de memoria y mataba la partida al abrir otra app pesada.
    val ramTotalMb = remember {
        val am = contexto.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        (info.totalMem / 1048576L).toInt()
    }
    val memoriaRecomendadaMb = (ramTotalMb * 22 / 100).coerceIn(1536, 2560)
    val limiteSeguroMb = ramTotalMb * 30 / 100
    var memoriaConfirmada by remember { mutableIntStateOf(RepositorioAjustes.memoriaMb(contexto)) }

    // Espacio: medir recorre miles de archivos, asi que va fuera del hilo
    // principal y se recalcula cuando algo lo cambia.
    var bytesJuego by remember { mutableStateOf(0L) }
    var bytesInternos by remember { mutableStateOf(0L) }
    var calculandoEspacio by remember { mutableStateOf(true) }
    var recalcularEspacio by remember { mutableIntStateOf(0) }
    var confirmando by remember { mutableStateOf<String?>(null) }
    var avisoEspacio by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(recalcularEspacio) {
        calculandoEspacio = true
        val medido = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            EspacioJuego.bytesJuego(contexto) to EspacioJuego.bytesInternos(contexto)
        }
        bytesJuego = medido.first
        bytesInternos = medido.second
        calculandoEspacio = false
    }
    var pedirConfirmacionMemoria by remember { mutableStateOf(false) }

    // A 892 dp de ancho el texto salía a ~109 caracteres por línea: se lee
    // mal y el deslizador obligaba a barrer media pantalla.
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val apaisado = config.screenWidthDp > config.screenHeightDp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg2, Bg)))
            .verticalScroll(estadoScroll)
            .padding(horizontal = 24.dp, vertical = if (apaisado) 14.dp else 24.dp)
            .widthIn(max = 760.dp),
    ) {
        Text(
            text = if (apartado == null) "← Volver" else "← Ajustes",
            style = MaterialTheme.typography.titleMedium,
            color = OroClaro,
            modifier = Modifier
                .clickable { if (apartado == null) alVolver() else apartado = null }
                .padding(vertical = 14.dp, horizontal = 8.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            when (apartado) {
                null -> "Ajustes"
                "controles" -> "Controles"
                "rendimiento" -> "Rendimiento"
                "graficos" -> "Gráficos avanzados"
                "acerca" -> "Acerca de"
                else -> "Partida y espacio"
            },
            // headlineMedium y no displayLarge: displayLarge es el estilo del
            // logotipo (44 sp con mucho tracking) y partía "Gráficos avanzados".
            style = MaterialTheme.typography.headlineMedium,
            color = OroClaro,
        )
        Text(
            "Cada opción explica qué hace y cuándo usarla. Los cambios se aplican en la próxima partida.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSuave,
        )
        Spacer(Modifier.height(22.dp))

        if (apartado == null) {
            val entradas = listOf(
                Triple("Controles", "Palanca o cruceta, tamaño global y el editor visual para mover, agrandar y añadir botones.", "controles"),
                Triple("Rendimiento", "Memoria del juego, sincronía vertical y presentación de video.", "rendimiento"),
                Triple("Gráficos avanzados", "Interruptores experimentales del driver para cazar artefactos visuales.", "graficos"),
                Triple("Partida y espacio", "Cuánto ocupa Cretania, cómo liberar espacio y reparar la instalación.", "partida"),
                Triple("Acerca de", "Qué es Lucerion, versión instalada y aviso legal.", "acerca"),
            )
            if (apaisado) {
                // Dos columnas: apiladas, las dos últimas nacían fuera de la
                // pantalla y había que adivinar que estaban ahí.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (mitad in entradas.chunked(2)) {
                        Column(Modifier.weight(1f)) {
                            for ((titulo, detalle, destino) in mitad) {
                                TarjetaApartado(titulo, detalle) { apartado = destino }
                            }
                        }
                    }
                }
            } else {
                for ((titulo, detalle, destino) in entradas) {
                    TarjetaApartado(titulo, detalle) { apartado = destino }
                }
            }
        }

        if (apartado == "controles") {
        FilaInterruptor(
            titulo = "Usar cruceta en vez de palanca",
            explicacion = "La palanca permite diagonales y correr con doble toque; " +
                "la cruceta son cuatro flechas fijas con agacharse en el centro, útil si " +
                "prefieres precisión de botón. " +
                "Predeterminado: palanca.",
            valor = usarCruceta,
            alCambiar = {
                usarCruceta = it
                RepositorioAjustes.guardarUsarCruceta(contexto, it)
            },
        )
        FilaDeslizador(
            titulo = "Tamaño de los controles",
            explicacion = "Agranda o achica todos los botones del juego (palanca, saltar, golpear…). " +
                "Súbelo si tienes dedos grandes o pantalla amplia; bájalo si te tapan la visión.",
            valorTexto = "${(escala * 100).toInt()} %",
            valor = escala,
            rango = 0.8f..1.4f,
            alCambiar = { escala = it },
            alSoltar = { RepositorioAjustes.guardarEscalaControles(contexto, escala) },
        )

        Tarjeta {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Editor de controles en pantalla", style = MaterialTheme.typography.titleMedium, color = OroClaro)
                Text(
                    "Previsualiza el HUD tal como se ve en el juego: arrastra cada control " +
                        "para moverlo, ajusta su tamaño y su transparencia, y añade botones " +
                        "personalizados ligados a una tecla (p. ej. «B» para la mochila).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSuave,
                )
                BotonSecundario("ABRIR EL EDITOR") {
                    contexto.startActivity(
                        android.content.Intent(
                            contexto,
                            com.lucerion.launcher.ui.juego.EditorControlesActivity::class.java,
                        ),
                    )
                }
            }
        }
        }

        if (apartado == "rendimiento") {
        FilaDeslizador(
            titulo = "Memoria del juego",
            explicacion = "Cuánta RAM puede usar Minecraft. Tu equipo tiene " +
                "${"%.1f".format(ramTotalMb / 1024f)} GB: la recomendada es " +
                "${"%.1f".format(memoriaRecomendadaMb / 1024f)} GB, que es lo que usa " +
                "«Automática». Más no es mejor, y está medido: el juego no " +
                "aprovecha el exceso, pero tu equipo se queda sin memoria libre y " +
                "empieza a comprimir la del juego, lo que gasta procesador, " +
                "calienta y te baja los FPS. El máximo del deslizador " +
                "(${"%.1f".format(limiteSeguroMb / 1024f)} GB) ya es el techo seguro.",
            valorTexto = if (memoriaMb == 0) "Automática" else "%.1f GB".format(memoriaMb / 1024f),
            valor = if (memoriaMb == 0) 1.8f else memoriaMb / 1024f,
            // El deslizador no llega al terreno peligroso: por encima del limite
            // seguro el equipo empieza a comprimir memoria del juego y se pierde
            // mas rendimiento del que se gana. Antes se podia elegir y el aviso
            // llegaba tarde, cuando ya estabas jugando peor.
            rango = 1.4f..(limiteSeguroMb / 1024f),
            alCambiar = { memoriaMb = if (it < 1.6f) 0 else (it * 1024).toInt() },
            alSoltar = {
                if (memoriaMb > memoriaRecomendadaMb) {
                    pedirConfirmacionMemoria = true
                } else {
                    RepositorioAjustes.guardarMemoriaMb(contexto, memoriaMb)
                    memoriaConfirmada = memoriaMb
                }
            },
        )
        if (pedirConfirmacionMemoria) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    pedirConfirmacionMemoria = false
                    memoriaMb = memoriaConfirmada
                },
                title = { Text("Más de lo recomendado", color = OroClaro) },
                text = {
                    Text(
                        "Pediste %.1f GB y lo recomendado para tu equipo es %.1f GB. ".format(
                            memoriaMb / 1024f, memoriaRecomendadaMb / 1024f,
                        ) + "Reservar de más no acelera el juego —no usa ni la mitad de lo " +
                            "que ya tiene—, pero deja al sistema sin memoria libre: empieza " +
                            "a comprimir la del juego, eso gasta procesador, calienta y te " +
                            "baja los FPS. ¿Continuar igualmente?",
                        color = TextoSuave,
                    )
                },
                confirmButton = {
                    Text(
                        "SÍ, USAR ESA MEMORIA",
                        color = Oro,
                        modifier = Modifier
                            .clickable {
                                RepositorioAjustes.guardarMemoriaMb(contexto, memoriaMb)
                                memoriaConfirmada = memoriaMb
                                pedirConfirmacionMemoria = false
                            }
                            .padding(8.dp),
                    )
                },
                dismissButton = {
                    Text(
                        "VOLVER AL VALOR ANTERIOR",
                        color = TextoSuave,
                        modifier = Modifier
                            .clickable {
                                memoriaMb = memoriaConfirmada
                                pedirConfirmacionMemoria = false
                            }
                            .padding(8.dp),
                    )
                },
                containerColor = Bg3,
            )
        }
        FilaInterruptor(
            titulo = "Sincronía vertical (vsync)",
            explicacion = "Sincroniza cada cuadro con la pantalla. Reduce el parpadeo y las " +
                "franjas; desactivarla puede dar algún FPS extra a cambio de artefactos. " +
                "Predeterminado: activada.",
            valor = vsync,
            alCambiar = {
                vsync = it
                RepositorioAjustes.guardarVsync(contexto, it)
            },
        )
        FilaOpciones(
            titulo = "Presentación de video (Vulkan)",
            explicacion = "Cómo entrega los cuadros el driver gráfico. «fifo» = máxima " +
                "estabilidad (recomendado); «mailbox» = menos latencia, algo más de carga; " +
                "«immediate» = sin espera, puede rasgar la imagen.",
            opciones = listOf("fifo", "mailbox", "immediate"),
            seleccion = presentacion,
            alElegir = {
                presentacion = it
                RepositorioAjustes.guardarPresentacionVulkan(contexto, it)
            },
        )

        }

        if (apartado == "graficos") {
        Text(
            "Interruptores del driver Turnip para cazar artefactos visuales. Actívalos de a " +
                "UNO y prueba una partida: si el problema sigue, vuelve a desactivarlo.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSuave,
        )
        Spacer(Modifier.height(10.dp))
        FilaInterruptor(
            titulo = "Desactivar LRZ",
            explicacion = "Apaga una optimización de profundidad del driver que en algunos " +
                "juegos produce parpadeos o franjas. Cuesta algo de FPS. Pruébalo si las " +
                "franjas negras persisten con vsync activo.",
            valor = sinLrz,
            alCambiar = {
                sinLrz = it
                RepositorioAjustes.guardarTurnipSinLrz(contexto, it)
            },
        )
        FilaInterruptor(
            titulo = "Renderizado directo a memoria (sysmem)",
            explicacion = "Evita el renderizado por mosaicos del chip Adreno. Es el último " +
                "recurso contra artefactos persistentes: suele bajar el rendimiento notablemente.",
            valor = sysmem,
            alCambiar = {
                sysmem = it
                RepositorioAjustes.guardarTurnipSysmem(contexto, it)
            },
        )

        }

        if (apartado == "partida") {
        Tarjeta {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Espacio ocupado", style = MaterialTheme.typography.titleMedium, color = OroClaro)
                Text(
                    "Todo lo que descarga Lucerion vive dentro de la app, así que al " +
                        "desinstalarla Android lo borra solo y no queda nada suelto en el " +
                        "teléfono. Aquí puedes recuperar ese espacio sin desinstalar nada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSuave,
                )
                Text(
                    if (calculandoEspacio) "Midiendo…"
                    else "Juego descargado: ${EspacioJuego.enPalabras(bytesJuego)}  ·  " +
                        "Motor y ajustes: ${EspacioJuego.enPalabras(bytesInternos)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OroClaro,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Borrar el juego conserva tus ajustes, el diseño del HUD y tu cuenta; " +
                        "se vuelve a descargar en la próxima entrada. Restablecer todo deja " +
                        "la app como recién instalada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSuave,
                )
                if (avisoEspacio != null) {
                    Text(avisoEspacio!!, style = MaterialTheme.typography.bodyMedium, color = OroClaro)
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario("BORRAR EL JUEGO") { confirmando = "juego" }
                    BotonSecundario("RESTABLECER TODO") { confirmando = "todo" }
                }
            }
        }

        // Confirmacion explicita: las dos acciones tiran abajo mas de un giga
        // y no hay vuelta atras.
        if (confirmando != null) {
            val borrarTodo = confirmando == "todo"
            AlertDialog(
                onDismissRequest = { confirmando = null },
                title = {
                    Text(if (borrarTodo) "¿Restablecer todo?" else "¿Borrar el juego descargado?")
                },
                text = {
                    Text(
                        if (borrarTodo) {
                            "Se borra el juego descargado Y toda tu configuración: diseño del " +
                                "HUD, ajustes, apodo y sesión. La app queda como recién instalada."
                        } else {
                            "Se liberan ${EspacioJuego.enPalabras(bytesJuego)}. Tus ajustes, el " +
                                "diseño del HUD y tu cuenta se conservan; el juego se descarga " +
                                "de nuevo la próxima vez que entres."
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val hecho = if (borrarTodo) EspacioJuego.restablecerTodo(contexto)
                        else EspacioJuego.borrarJuego(contexto)
                        avisoEspacio = if (hecho) {
                            "Listo. Espacio liberado."
                        } else {
                            "Hay una partida abierta: ciérrala antes de borrar."
                        }
                        confirmando = null
                        recalcularEspacio++
                    }) { Text(if (borrarTodo) "RESTABLECER" else "BORRAR") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmando = null }) {
                        Text("CANCELAR")
                    }
                },
            )
        }

        Tarjeta {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reparar instalación del juego", style = MaterialTheme.typography.titleMedium, color = OroClaro)
                Text(
                    "Retira la versión instalada de Minecraft + NeoForge para reconstruirla " +
                        "limpia en la próxima entrada (los archivos grandes ya descargados se " +
                        "reutilizan; tus mundos y ajustes del juego no se tocan). Úsalo si el " +
                        "juego quedó en un estado raro tras una actualización o un cierre forzado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSuave,
                )
                Spacer(Modifier.height(2.dp))
                if (!reparacionPedida) {
                    BotonSecundario("REPARAR EN LA PRÓXIMA ENTRADA") {
                        reparacionPedida = InstaladorJuego.repararInstalacion(
                            File(contexto.getExternalFilesDir(null), "instancia-cretania"),
                        )
                    }
                } else {
                    Text(
                        "Listo: al tocar JUGAR CRETANIA se reinstalará limpia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OroClaro,
                    )
                }
            }
        }
        }

        if (apartado == "acerca") {
            Tarjeta {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.acerca_titulo),
                        style = MaterialTheme.typography.titleMedium,
                        color = OroClaro,
                    )
                    Text(
                        stringResource(R.string.acerca_que_es),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave,
                    )
                    Text(
                        stringResource(R.string.acerca_version, versionApp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave.copy(alpha = 0.7f),
                    )
                }
            }

            // El aviso de Mojang, con marco propio: es obligatorio y tiene que
            // poder leerse sin buscarlo.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.dp, Oro.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                color = Bg2,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.acerca_aviso_titulo),
                        style = MaterialTheme.typography.titleMedium,
                        color = OroClaro,
                    )
                    Text(
                        stringResource(R.string.aviso_mojang),
                        style = MaterialTheme.typography.titleMedium,
                        color = OroClaro.copy(alpha = 0.9f),
                    )
                    Text(
                        stringResource(R.string.acerca_marcas),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TarjetaApartado(titulo: String, explicacion: String, alPulsar: () -> Unit) {
    Tarjeta {
        Column(
            Modifier
                .clickable(onClick = alPulsar)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = OroClaro)
            Text(explicacion, style = MaterialTheme.typography.bodyMedium, color = TextoSuave)
        }
    }
}

// ── Piezas ──────────────────────────────────────────────────────────────────

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, OroProfundo.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
        color = Bg3,
        shape = RoundedCornerShape(12.dp),
    ) { contenido() }
}

@Composable
private fun FilaInterruptor(
    titulo: String,
    explicacion: String,
    valor: Boolean,
    alCambiar: (Boolean) -> Unit,
) {
    Tarjeta {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, color = OroClaro)
                Text(explicacion, style = MaterialTheme.typography.bodyMedium, color = TextoSuave)
            }
            Spacer(Modifier.width(14.dp))
            Switch(
                checked = valor,
                onCheckedChange = alCambiar,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Oro,
                    checkedThumbColor = Bg,
                ),
            )
        }
    }
}

@Composable
private fun FilaDeslizador(
    titulo: String,
    explicacion: String,
    valorTexto: String,
    valor: Float,
    rango: ClosedFloatingPointRange<Float>,
    alCambiar: (Float) -> Unit,
    alSoltar: () -> Unit,
) {
    Tarjeta {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, color = OroClaro, modifier = Modifier.weight(1f))
                Text(valorTexto, style = MaterialTheme.typography.titleMedium, color = Oro)
            }
            Text(explicacion, style = MaterialTheme.typography.bodyMedium, color = TextoSuave)
            Slider(
                value = valor,
                onValueChange = alCambiar,
                onValueChangeFinished = alSoltar,
                valueRange = rango,
                modifier = Modifier.widthIn(max = 420.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Oro,
                    activeTrackColor = Oro,
                    inactiveTrackColor = OroProfundo.copy(alpha = 0.35f),
                ),
            )
        }
    }
}

@Composable
private fun FilaOpciones(
    titulo: String,
    explicacion: String,
    opciones: List<String>,
    seleccion: String,
    alElegir: (String) -> Unit,
) {
    Tarjeta {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = OroClaro)
            Text(explicacion, style = MaterialTheme.typography.bodyMedium, color = TextoSuave)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (opcion in opciones) {
                    val activa = opcion == seleccion
                    Box(
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (activa) Oro else OroProfundo.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp),
                            )
                            .background(
                                if (activa) Oro.copy(alpha = 0.18f) else Bg2,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { alElegir(opcion) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            opcion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (activa) OroClaro else TextoSuave,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BotonSecundario(texto: String, alPulsar: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .border(1.dp, Oro, RoundedCornerShape(10.dp))
            .clickable(onClick = alPulsar)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = OroClaro)
    }
}
