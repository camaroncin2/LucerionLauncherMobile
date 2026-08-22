package com.lucerion.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lucerion.launcher.data.RepositorioCuenta
import com.lucerion.launcher.ui.screens.CuentaScreen
import com.lucerion.launcher.ui.screens.SincronizarScreen
import com.lucerion.launcher.ui.screens.HomeScreen
import com.lucerion.launcher.ui.screens.SplashScreen
import com.lucerion.launcher.ui.theme.LucerionTheme

/** Pantallas de la app. Navegación por estado simple; si el grafo crece se
 *  migrará a navigation-compose sin tocar las pantallas. */
private enum class Pantalla { Splash, Home, Cuenta, Sincronizar }

/**
 * Única Activity de la interfaz. El juego en sí corre en la Activity del motor.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LucerionTheme {
                var pantalla by remember { mutableStateOf(Pantalla.Splash) }
                var apodo by remember { mutableStateOf(RepositorioCuenta.leerApodo(this)) }

                when (pantalla) {
                    Pantalla.Splash -> SplashScreen(
                        alTerminar = { pantalla = Pantalla.Home },
                    )

                    Pantalla.Home -> HomeScreen(
                        versionApp = BuildConfig.VERSION_NAME,
                        apodo = apodo,
                        alAbrirCuenta = { pantalla = Pantalla.Cuenta },
                        // El camino feliz se protege solo: sin apodo, JUGAR te
                        // lleva primero a elegirlo.
                        alJugar = {
                            pantalla = if (apodo == null) Pantalla.Cuenta else Pantalla.Sincronizar
                        },
                    )

                    Pantalla.Sincronizar -> SincronizarScreen(
                        apodo = apodo ?: "Jugador",
                        alVolver = { pantalla = Pantalla.Home },
                    )

                    Pantalla.Cuenta -> CuentaScreen(
                        apodoInicial = apodo,
                        alGuardar = { nuevo ->
                            RepositorioCuenta.guardarApodo(this, nuevo)
                            apodo = nuevo
                            pantalla = Pantalla.Home
                        },
                        alVolver = { pantalla = Pantalla.Home },
                    )
                }
            }
        }
    }
}
