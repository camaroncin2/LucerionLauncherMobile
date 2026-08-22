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

    /**
     * La sesión de Microsoft vive en SU PROPIO archivo.
     *
     * La copia de seguridad de Android incluye o excluye archivos enteros, no
     * claves sueltas. Mientras el token compartía archivo con los ajustes, o
     * se respaldaba todo —y el token acababa en la nube— o no se respaldaba
     * nada. Separado, la configuración se puede restaurar al reinstalar y el
     * token se queda en el teléfono, que es donde tiene que estar.
     */
    private const val PREFS_SESION = "lucerion-cuenta"

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
        context.getSharedPreferences(PREFS_SESION, Context.MODE_PRIVATE)
            .edit()
            .putString(CLAVE_MICROSOFT, com.google.gson.Gson().toJson(datos))
            .apply()
    }

    /**
     * Traslada una sesión guardada por una versión anterior, que la dejaba
     * junto a los ajustes. Sin esto, quien ya había iniciado sesión aparecía
     * de golpe como desconectado tras actualizar.
     */
    private fun migrarSesionAntigua(context: Context) {
        val viejas = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val crudo = viejas.getString(CLAVE_MICROSOFT, null) ?: return
        context.getSharedPreferences(PREFS_SESION, Context.MODE_PRIVATE)
            .edit().putString(CLAVE_MICROSOFT, crudo).apply()
        viejas.edit().remove(CLAVE_MICROSOFT).apply()
    }

    @Suppress("UNCHECKED_CAST")
    fun leerCuentaMicrosoft(context: Context): Map<Any, Any>? {
        migrarSesionAntigua(context)
        val crudo = context.getSharedPreferences(PREFS_SESION, Context.MODE_PRIVATE)
            .getString(CLAVE_MICROSOFT, null) ?: return null
        return runCatching {
            com.google.gson.Gson().fromJson(crudo, Map::class.java) as Map<Any, Any>
        }.getOrNull()
    }

    fun olvidarCuentaMicrosoft(context: Context) {
        context.getSharedPreferences(PREFS_SESION, Context.MODE_PRIVATE)
            .edit().remove(CLAVE_MICROSOFT).apply()
        // Por si quedaba en el archivo antiguo de una version previa.
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
