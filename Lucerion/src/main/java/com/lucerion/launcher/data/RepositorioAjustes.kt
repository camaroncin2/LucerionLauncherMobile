package com.lucerion.launcher.data

import android.content.Context

/**
 * Ajustes del launcher, persistidos en las preferencias "lucerion".
 * Cada campo tiene un valor por defecto pensado para el jugador que nunca
 * abre esta pantalla: la app debe funcionar bien sin tocar nada.
 */
object RepositorioAjustes {

    private fun prefs(contexto: Context) =
        contexto.getSharedPreferences("lucerion", Context.MODE_PRIVATE)

    // Controles
    fun usarCruceta(c: Context) = prefs(c).getBoolean("usar_cruceta", false)
    fun guardarUsarCruceta(c: Context, v: Boolean) = prefs(c).edit().putBoolean("usar_cruceta", v).apply()

    /** Escala de los controles del juego: 0.8 (pequeños) a 1.4 (grandes). */
    fun escalaControles(c: Context) = prefs(c).getFloat("escala_controles", 1.0f)
    fun guardarEscalaControles(c: Context, v: Float) =
        prefs(c).edit().putFloat("escala_controles", v.coerceIn(0.8f, 1.4f)).apply()

    // Rendimiento
    /** Memoria de la JVM en MB; 0 = automática (40 % de la RAM, entre 2 y 6 GB). */
    fun memoriaMb(c: Context) = prefs(c).getInt("memoria_mb", 0)
    fun guardarMemoriaMb(c: Context, v: Int) =
        prefs(c).edit().putInt("memoria_mb", if (v == 0) 0 else v.coerceIn(1536, 4096)).apply()

    fun vsync(c: Context) = prefs(c).getBoolean("vsync", true)
    fun guardarVsync(c: Context, v: Boolean) = prefs(c).edit().putBoolean("vsync", v).apply()

    /** Modo de presentación del swapchain Vulkan: fifo | mailbox | immediate. */
    fun presentacionVulkan(c: Context) = prefs(c).getString("presentacion_vk", "fifo") ?: "fifo"
    fun guardarPresentacionVulkan(c: Context, v: String) =
        prefs(c).edit().putString("presentacion_vk", v).apply()

    // Gráficos avanzados (experimentales, para artefactos visuales)
    fun turnipSinLrz(c: Context) = prefs(c).getBoolean("turnip_nolrz", false)
    fun guardarTurnipSinLrz(c: Context, v: Boolean) = prefs(c).edit().putBoolean("turnip_nolrz", v).apply()

    fun turnipSysmem(c: Context) = prefs(c).getBoolean("turnip_sysmem", false)
    fun guardarTurnipSysmem(c: Context, v: Boolean) = prefs(c).edit().putBoolean("turnip_sysmem", v).apply()

    /**
     * Variables de entorno del juego derivadas de los ajustes. Se escriben en
     * las prefs "launcher"/"env" (el ÚNICO canal que gana: addCustomEnv del
     * motor se aplica al final, después de que addCommonEnv pise todo).
     */
    fun construirEnv(c: Context): String = buildString {
        if (vsync(c)) append("FORCE_VSYNC=true\n")
        append("MESA_VK_WSI_PRESENT_MODE=${presentacionVulkan(c)}\n")
        val tu = mutableListOf<String>()
        if (turnipSinLrz(c)) tu += "nolrz"
        if (turnipSysmem(c)) tu += "sysmem"
        if (tu.isNotEmpty()) append("TU_DEBUG=${tu.joinToString(",")}\n")
    }
}
