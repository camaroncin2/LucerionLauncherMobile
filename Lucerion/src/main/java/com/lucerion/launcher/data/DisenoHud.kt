package com.lucerion.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.tungsten.fclauncher.keycodes.FCLKeycodes

/**
 * Distribución de los controles en pantalla (HUD del juego), editable desde
 * el previsualizador de Ajustes. Posiciones como fracción del panel (0..1,
 * centro del control) para que sirvan en cualquier resolución; tamaños en dp
 * sin escalar (el ajuste "Tamaño de los controles" multiplica encima).
 */
data class ControlHud(
    val id: String,          // predefinidos: movimiento/salto/golpear/agacharse/chat/pausa/inventario/teclado
    val tipo: String,        // "predefinido" | "tecla"
    var x: Float,
    var y: Float,
    var tam: Int,
    val etiqueta: String? = null, // solo tipo "tecla"
    val tecla: Int = 0,           // FCLKeycodes, solo tipo "tecla"
)

data class DisenoHud(val controles: MutableList<ControlHud>)

object RepositorioDiseno {

    private const val CLAVE = "diseno_hud"
    private val gson = Gson()

    fun porDefecto(): DisenoHud = DisenoHud(
        mutableListOf(
            ControlHud("movimiento", "predefinido", 0.135f, 0.70f, 170),
            ControlHud("salto", "predefinido", 0.945f, 0.76f, 84),
            ControlHud("golpear", "predefinido", 0.860f, 0.76f, 84),
            ControlHud("agacharse", "predefinido", 0.945f, 0.50f, 58),
            ControlHud("chat", "predefinido", 0.500f, 0.075f, 46),
            ControlHud("pausa", "predefinido", 0.845f, 0.075f, 46),
            ControlHud("inventario", "predefinido", 0.900f, 0.075f, 46),
            ControlHud("teclado", "predefinido", 0.955f, 0.075f, 46),
        ),
    )

    fun cargar(contexto: Context): DisenoHud {
        val crudo = contexto.getSharedPreferences("lucerion", Context.MODE_PRIVATE)
            .getString(CLAVE, null) ?: return porDefecto()
        return runCatching { gson.fromJson(crudo, DisenoHud::class.java) }
            .getOrNull()
            ?.takeIf { it.controles.isNotEmpty() }
            ?: porDefecto()
    }

    fun guardar(contexto: Context, diseno: DisenoHud) {
        contexto.getSharedPreferences("lucerion", Context.MODE_PRIVATE)
            .edit().putString(CLAVE, gson.toJson(diseno)).apply()
    }

    fun restablecer(contexto: Context) {
        contexto.getSharedPreferences("lucerion", Context.MODE_PRIVATE)
            .edit().remove(CLAVE).apply()
    }
}

/** Teclas ofrecidas al crear un botón personalizado (nombre visible → FCLKeycode). */
object CatalogoTeclas {
    val disponibles: List<Pair<String, Int>> = listOf(
        "A" to FCLKeycodes.KEY_A, "B" to FCLKeycodes.KEY_B, "C" to FCLKeycodes.KEY_C,
        "D" to FCLKeycodes.KEY_D, "E" to FCLKeycodes.KEY_E, "F" to FCLKeycodes.KEY_F,
        "G" to FCLKeycodes.KEY_G, "H" to FCLKeycodes.KEY_H, "I" to FCLKeycodes.KEY_I,
        "J" to FCLKeycodes.KEY_J, "K" to FCLKeycodes.KEY_K, "L" to FCLKeycodes.KEY_L,
        "M" to FCLKeycodes.KEY_M, "N" to FCLKeycodes.KEY_N, "O" to FCLKeycodes.KEY_O,
        "P" to FCLKeycodes.KEY_P, "Q" to FCLKeycodes.KEY_Q, "R" to FCLKeycodes.KEY_R,
        "S" to FCLKeycodes.KEY_S, "T" to FCLKeycodes.KEY_T, "U" to FCLKeycodes.KEY_U,
        "V" to FCLKeycodes.KEY_V, "W" to FCLKeycodes.KEY_W, "X" to FCLKeycodes.KEY_X,
        "Y" to FCLKeycodes.KEY_Y, "Z" to FCLKeycodes.KEY_Z,
        "1" to FCLKeycodes.KEY_1, "2" to FCLKeycodes.KEY_2, "3" to FCLKeycodes.KEY_3,
        "4" to FCLKeycodes.KEY_4, "5" to FCLKeycodes.KEY_5, "6" to FCLKeycodes.KEY_6,
        "7" to FCLKeycodes.KEY_7, "8" to FCLKeycodes.KEY_8, "9" to FCLKeycodes.KEY_9,
        "0" to FCLKeycodes.KEY_0,
        "TAB" to FCLKeycodes.KEY_TAB,
        "F3" to FCLKeycodes.KEY_F3,
        "F5" to FCLKeycodes.KEY_F5,
    )
}
