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

    // ── Cuenta de Microsoft ─────────────────────────────────────────────────
    // Se guarda el mapa que produce el motor (incluye el token de refresco,
    // no la contraseña: Lucerion nunca la ve ni la puede ver).

    private const val CLAVE_MICROSOFT = "cuenta_microsoft"

    fun guardarCuentaMicrosoft(context: Context, datos: Map<Any, Any>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CLAVE_MICROSOFT, com.google.gson.Gson().toJson(datos))
            .apply()
    }

    @Suppress("UNCHECKED_CAST")
    fun leerCuentaMicrosoft(context: Context): Map<Any, Any>? {
        val crudo = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CLAVE_MICROSOFT, null) ?: return null
        return runCatching {
            com.google.gson.Gson().fromJson(crudo, Map::class.java) as Map<Any, Any>
        }.getOrNull()
    }

    fun olvidarCuentaMicrosoft(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(CLAVE_MICROSOFT).apply()
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
