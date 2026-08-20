package com.lucerion.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lucerion.launcher.ui.screens.HomeScreen
import com.lucerion.launcher.ui.screens.SplashScreen
import com.lucerion.launcher.ui.theme.LucerionTheme

/**
 * Única Activity de la interfaz. Las pantallas son composables y la navegación
 * es un estado simple; cuando el flujo crezca (ajustes, cuenta, descargas) se
 * migrará a navigation-compose. El juego en sí corre en la Activity del motor.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LucerionTheme {
                var mostrarSplash by remember { mutableStateOf(true) }
                if (mostrarSplash) {
                    SplashScreen(alTerminar = { mostrarSplash = false })
                } else {
                    HomeScreen(versionApp = BuildConfig.VERSION_NAME)
                }
            }
        }
    }
}
