package com.lucerion.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucerion.launcher.R
import com.lucerion.launcher.data.lucerion.SincronizadorModpack
import com.lucerion.launcher.data.lucerion.SincronizadorModpack.Estado
import com.lucerion.launcher.motor.InstaladorJuego
import com.lucerion.launcher.motor.Lanzador
import com.lucerion.launcher.motor.MotorLucerion
import com.lucerion.launcher.ui.theme.AmbarCta1
import com.lucerion.launcher.ui.theme.AmbarCta2
import com.lucerion.launcher.ui.theme.Bg
import com.lucerion.launcher.ui.theme.Bg2
import com.lucerion.launcher.ui.theme.Bg3
import com.lucerion.launcher.ui.theme.CreateNaranja
import com.lucerion.launcher.ui.theme.Oro
import com.lucerion.launcher.ui.theme.OroClaro
import com.lucerion.launcher.ui.theme.OroProfundo
import com.lucerion.launcher.ui.theme.TextoSuave
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Preparación del modpack: analizar → confirmar → descargar con progreso.
 *
 * El jugador ve números honestos (archivos y MB reales), sabe que puede
 * cortar sin perder lo bajado, y el error siempre trae el motivo y un
 * reintentar. Salir de la pantalla cancela la descarga de forma explícita.
 */
@Composable
fun SincronizarScreen(apodo: String, alVolver: () -> Unit) {
    val contexto = LocalContext.current
    val sincronizador = remember {
        SincronizadorModpack(File(contexto.getExternalFilesDir(null), "instancia-cretania"))
    }
    val estado by sincronizador.estado.collectAsState()
    val ambito = rememberCoroutineScope()
    val trabajo = remember { arrayOfNulls<Job>(1) }

    LaunchedEffect(Unit) { sincronizador.analizar() }

    val salir = {
        trabajo[0]?.cancel()
        alVolver()
    }
    BackHandler(onBack = salir)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg2, Bg)))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.sync_volver),
                style = MaterialTheme.typography.titleMedium,
                color = OroClaro,
                modifier = Modifier.clickable(onClick = salir),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.sync_titulo),
            style = MaterialTheme.typography.headlineMedium,
            color = OroClaro,
        )
        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val e = estado) {
                is Estado.Inactivo, is Estado.Analizando -> {
                    Text(
                        text = stringResource(R.string.sync_analizando),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    BarraProgreso(indeterminada = true, fraccion = 0f)
                }

                is Estado.ListoParaDescargar -> {
                    Text(
                        text = stringResource(
                            R.string.sync_resumen,
                            e.version, e.archivosFaltantes, e.archivosTotales, formatearBytes(e.bytesFaltantes),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = OroClaro,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.sync_nota),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    BotonDorado(texto = stringResource(R.string.sync_descargar)) {
                        trabajo[0] = ambito.launch { sincronizador.descargar() }
                    }
                }

                is Estado.Descargando -> {
                    val fraccion = if (e.bytesTotales > 0) e.bytesListos.toFloat() / e.bytesTotales else 0f
                    Text(
                        text = stringResource(R.string.sync_descargando, e.archivosListos, e.archivosTotales),
                        style = MaterialTheme.typography.titleMedium,
                        color = OroClaro,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.sync_bytes,
                            formatearBytes(e.bytesListos), formatearBytes(e.bytesTotales),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave,
                    )
                    Spacer(Modifier.height(16.dp))
                    BarraProgreso(indeterminada = false, fraccion = fraccion)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = e.archivoActual,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = stringResource(R.string.sync_cancelar),
                        style = MaterialTheme.typography.titleMedium,
                        color = CreateNaranja,
                        modifier = Modifier.clickable {
                            trabajo[0]?.cancel()
                            ambito.launch { sincronizador.analizar() }
                        },
                    )
                }

                is Estado.Completo -> {
                    Text(
                        text = stringResource(R.string.sync_completo, e.version, e.mods),
                        style = MaterialTheme.typography.titleMedium,
                        color = OroClaro,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    BarraProgreso(indeterminada = false, fraccion = 1f)
                    Spacer(Modifier.height(28.dp))
                    BloqueMotor(apodo, File(contexto.getExternalFilesDir(null), "instancia-cretania"))
                }

                is Estado.Fallo -> {
                    Text(
                        text = stringResource(R.string.sync_error_titulo),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = e.motivo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    BotonDorado(texto = stringResource(R.string.sync_reintentar)) {
                        ambito.launch { sincronizador.analizar() }
                    }
                }
            }
        }
    }
}

/**
 * Estado de los componentes del motor (runtimes del juego). Se instalan una
 * vez desde los assets del APK; despues esta seccion solo confirma que estan.
 */
@Composable
private fun BloqueMotor(apodo: String, dirInstancia: File) {
    val actividad = LocalContext.current as android.app.Activity
    val estadoMotor by MotorLucerion.estado.collectAsState()
    val ambito = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (estadoMotor is MotorLucerion.Estado.SinPreparar) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                MotorLucerion.estaListo(actividad)
            }
        }
    }

    Text(
        text = stringResource(R.string.motor_titulo),
        style = MaterialTheme.typography.titleMedium,
        color = OroClaro,
    )
    Spacer(Modifier.height(8.dp))
    when (val m = estadoMotor) {
        is MotorLucerion.Estado.SinPreparar -> {
            Text(
                text = stringResource(R.string.motor_explicacion),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSuave,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            BotonDorado(texto = stringResource(R.string.motor_instalar)) {
                ambito.launch { MotorLucerion.preparar(actividad) }
            }
        }

        is MotorLucerion.Estado.Preparando -> {
            Text(
                text = stringResource(R.string.motor_preparando, m.paso),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSuave,
            )
            Spacer(Modifier.height(12.dp))
            BarraProgreso(indeterminada = true, fraccion = 0f)
        }

        is MotorLucerion.Estado.Listo -> {
            Text(
                text = stringResource(R.string.motor_listo),
                style = MaterialTheme.typography.titleMedium,
                color = OroClaro,
            )
            Spacer(Modifier.height(20.dp))
            BloqueJuego(apodo, dirInstancia)
        }

        is MotorLucerion.Estado.Fallo -> {
            Text(
                text = stringResource(R.string.motor_fallo, m.motivo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            BotonDorado(texto = stringResource(R.string.sync_reintentar)) {
                ambito.launch { MotorLucerion.preparar(actividad) }
            }
        }
    }
}

/**
 * Instalacion del juego base (capa B) y lanzamiento (capa C).
 * El boton final ES el objetivo del proyecto: entrar a Cretania.
 */
@Composable
private fun BloqueJuego(apodo: String, dirInstancia: File) {
    val actividad = LocalContext.current as android.app.Activity
    val estadoJuego by InstaladorJuego.estado.collectAsState()
    val ambito = rememberCoroutineScope()
    var lanzando by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            InstaladorJuego.estaInstalado(dirInstancia)
        }
    }

    Text(
        text = stringResource(R.string.juego_titulo),
        style = MaterialTheme.typography.titleMedium,
        color = OroClaro,
    )
    Spacer(Modifier.height(8.dp))
    when (val j = estadoJuego) {
        is InstaladorJuego.Estado.SinInstalar -> {
            Text(
                text = stringResource(R.string.juego_explicacion),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSuave,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            BotonDorado(texto = stringResource(R.string.juego_instalar)) {
                ambito.launch {
                    InstaladorJuego.instalar(dirInstancia, File(actividad.cacheDir, "motor-cache"))
                }
            }
        }

        is InstaladorJuego.Estado.Instalando -> {
            Text(
                text = stringResource(R.string.juego_instalando, j.detalle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSuave,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            BarraProgreso(indeterminada = true, fraccion = 0f)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.juego_instalando_nota),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSuave.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }

        is InstaladorJuego.Estado.Instalado -> {
            Text(
                text = stringResource(R.string.juego_listo),
                style = MaterialTheme.typography.titleMedium,
                color = OroClaro,
            )
            Spacer(Modifier.height(16.dp))
            if (lanzando) {
                Text(
                    text = stringResource(R.string.juego_lanzando),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSuave,
                )
                Spacer(Modifier.height(12.dp))
                BarraProgreso(indeterminada = true, fraccion = 0f)
            } else {
                BotonDorado(texto = stringResource(R.string.juego_entrar)) {
                    lanzando = true
                    ambito.launch {
                        try {
                            Lanzador.lanzar(actividad, dirInstancia, apodo)
                        } finally {
                            lanzando = false
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.juego_entrar_detalle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSuave,
                    textAlign = TextAlign.Center,
                )
            }
        }

        is InstaladorJuego.Estado.Fallo -> {
            Text(
                text = stringResource(R.string.juego_fallo, j.motivo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            BotonDorado(texto = stringResource(R.string.sync_reintentar)) {
                ambito.launch {
                    InstaladorJuego.instalar(dirInstancia, File(actividad.cacheDir, "motor-cache"))
                }
            }
        }
    }
}

/** Barra de progreso de estilo propio: canal navy con relleno dorado. */
@Composable
private fun BarraProgreso(indeterminada: Boolean, fraccion: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .border(1.dp, OroProfundo, RoundedCornerShape(7.dp))
            .padding(2.dp)
            .background(Bg3, RoundedCornerShape(5.dp)),
    ) {
        val ancho = if (indeterminada) 0.25f else fraccion.coerceIn(0f, 1f)
        if (ancho > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ancho)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(AmbarCta2, AmbarCta1, Oro)),
                        RoundedCornerShape(5.dp),
                    ),
            )
        }
    }
}

@Composable
private fun BotonDorado(texto: String, alPulsar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(2.dp, OroProfundo, RoundedCornerShape(12.dp))
            .padding(3.dp)
            .background(Brush.verticalGradient(listOf(AmbarCta1, AmbarCta2)), RoundedCornerShape(9.dp))
            .clickable(onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = texto, style = MaterialTheme.typography.labelLarge, color = Bg)
    }
}

private fun formatearBytes(bytes: Long): String {
    val mb = bytes / 1048576.0
    return when {
        mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024)
        mb >= 1 -> String.format(Locale.US, "%.0f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
