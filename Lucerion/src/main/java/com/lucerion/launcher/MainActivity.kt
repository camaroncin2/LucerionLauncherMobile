package com.lucerion.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.lucerion.launcher.data.RepositorioCuenta
import com.lucerion.launcher.ui.nav.BarraInferior
import com.lucerion.launcher.ui.nav.BarraLateral
import com.lucerion.launcher.ui.nav.Seccion
import com.lucerion.launcher.ui.screens.AjustesScreen
import com.lucerion.launcher.ui.screens.CuentaScreen
import com.lucerion.launcher.ui.screens.HomeScreen
import com.lucerion.launcher.ui.screens.SincronizarScreen
import com.lucerion.launcher.ui.screens.SkinScreen
import com.lucerion.launcher.ui.screens.SplashScreen
import com.lucerion.launcher.ui.theme.Bg
import com.lucerion.launcher.ui.theme.Bg2
import com.lucerion.launcher.ui.theme.LucerionTheme

/**
 * Única Activity de la interfaz. El juego en sí corre en la Activity del motor.
 *
 * Navegación por secciones (barra inferior en vertical, lateral en apaisado).
 * «Preparar Cretania» no es una sección sino un flujo a pantalla completa: una
 * vez dentro, la barra estorbaría y el botón de volver ya cierra el paso.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LucerionTheme {
                var enSplash by remember { mutableStateOf(true) }
                var seccion by remember { mutableStateOf(Seccion.Inicio) }
                var sincronizando by remember { mutableStateOf(false) }
                var apodo by remember { mutableStateOf(RepositorioCuenta.leerApodo(this)) }
                // JUGAR sin apodo manda a Cuenta; al guardar hay que RETOMAR
                // el camino, no dejar al jugador tocando JUGAR dos veces.
                var jugarTrasElegirApodo by remember { mutableStateOf(false) }

                when {
                    enSplash -> SplashScreen(alTerminar = { enSplash = false })

                    sincronizando -> SincronizarScreen(
                        apodo = apodo ?: "Jugador",
                        alVolver = { sincronizando = false },
                    )

                    else -> Andamiaje(seccion = seccion, alElegir = { seccion = it }) {
                        when (seccion) {
                            Seccion.Inicio -> HomeScreen(
                                versionApp = BuildConfig.VERSION_NAME,
                                // El camino feliz se protege solo: sin apodo,
                                // JUGAR lleva primero a elegirlo.
                                alJugar = {
                                    if (apodo == null) {
                                        jugarTrasElegirApodo = true
                                        seccion = Seccion.Cuenta
                                    } else {
                                        sincronizando = true
                                    }
                                },
                            )

                            Seccion.Cuenta -> CuentaScreen(
                                apodoInicial = apodo,
                                // Si vino de JUGAR, la pantalla lo dice en vez
                                // de parecer un cambio de pestaña porque sí.
                                motivo = if (jugarTrasElegirApodo) {
                                    "Elige tu apodo y entras directo a Cretania."
                                } else {
                                    null
                                },
                                alGuardar = { nuevo ->
                                    RepositorioCuenta.guardarApodo(this, nuevo)
                                    apodo = nuevo
                                    if (jugarTrasElegirApodo) {
                                        jugarTrasElegirApodo = false
                                        sincronizando = true
                                    }
                                    seccion = Seccion.Inicio
                                },
                                alVolver = {
                                    jugarTrasElegirApodo = false
                                    seccion = Seccion.Inicio
                                },
                            )

                            Seccion.Skin -> SkinScreen(alVolver = { seccion = Seccion.Inicio })

                            Seccion.Ajustes -> AjustesScreen(alVolver = { seccion = Seccion.Inicio })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Coloca el contenido junto a su barra de navegación: abajo cuando el teléfono
 * está de pie (el pulgar llega), al costado cuando está apaisado (aprovecha el
 * ancho sobrante en vez de robar altura, que es lo escaso).
 */
@Composable
private fun Andamiaje(
    seccion: Seccion,
    alElegir: (Seccion) -> Unit,
    contenido: @Composable () -> Unit,
) {
    // Al girar el teléfono el contenido cambia de rama (Row ↔ Column). Sin
    // esto Compose lo DESECHA y lo vuelve a crear: se perdía lo que estabas
    // escribiendo, el apartado abierto de Ajustes y los avisos en pantalla.
    // movableContentOf lo mueve conservando su estado.
    val contenidoMovible = remember(contenido) { movableContentOf(contenido) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg2, Bg)))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                BarraLateral(seccion = seccion, alElegir = alElegir)
                Box(Modifier.weight(1f).fillMaxSize()) { contenidoMovible() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { contenidoMovible() }
                BarraInferior(
                    seccion = seccion,
                    alElegir = alElegir,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}
