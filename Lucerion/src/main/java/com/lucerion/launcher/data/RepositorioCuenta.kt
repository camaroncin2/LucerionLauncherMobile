package com.lucerion.launcher.data

import android.content.Context

/**
 * Persistencia de la cuenta local.
 *
 * El servidor autentica con AuthMod, así que el apodo es lo único
 * imprescindible para jugar; la cuenta Microsoft será opcional y vivirá
 * aparte (la gestiona el motor). SharedPreferences alcanza de sobra para
 * esto y mantiene el arranque instantáneo.
 */
object RepositorioCuenta {

    private const val PREFS = "lucerion"
    private const val CLAVE_APODO = "apodo"

    fun leerApodo(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CLAVE_APODO, null)
            ?.takeIf { it.isNotBlank() }

    fun guardarApodo(context: Context, apodo: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CLAVE_APODO, apodo.trim())
            .apply()
    }

    /**
     * Reglas de nombre de Minecraft: 3-16 caracteres, letras sin acentos,
     * números y guion bajo. Devuelve el problema en palabras del jugador,
     * o null si el apodo es válido.
     */
    fun validarApodo(apodo: String): String? {
        val limpio = apodo.trim()
        return when {
            limpio.length < 3 -> "Muy corto: necesita al menos 3 caracteres."
            limpio.length > 16 -> "Muy largo: el máximo son 16 caracteres."
            !limpio.all { it.isLetterOrDigit() && it.code < 128 || it == '_' } ->
                "Solo letras sin acentos (a-z, A-Z), números y guion bajo (_). Sin espacios."
            else -> null
        }
    }
}
