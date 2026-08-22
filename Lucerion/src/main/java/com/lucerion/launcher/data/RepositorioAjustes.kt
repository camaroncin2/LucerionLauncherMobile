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
    /**
     * Porcentaje del panel al que se dibuja el juego (100 = nativo).
     *
     * Es la palanca mas directa que existe en movil: a 85 % se dibuja el 72 %
     * de los pixeles y a 75 %, el 56 %. Menos pixeles es menos GPU, menos
     * vatios y menos calor — y en este equipo el calor es justo lo que hace
     * que el fabricante recorte el nucleo principal a la mitad. La imagen se
     * reescala al panel completo, asi que la nitidez baja pero nada se corta.
     */
    fun escalaRender(c: Context): Int =
        prefs(c).getInt("escala_render", 100).coerceIn(50, 100)

    fun guardarEscalaRender(c: Context, v: Int) =
        prefs(c).edit().putInt("escala_render", v.coerceIn(50, 100)).apply()

    private fun ramTotalMb(c: Context): Int {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return (info.totalMem / 1048576L).toInt()
    }

    /**
     * Memoria recomendada: 22 % de la RAM, entre 1.5 y 2.5 GB.
     *
     * Es lo que usa «Automática» y cubre el juego normal. No es un techo: en
     * zonas muy cargadas (un hub lleno de construcción y jugadores) el montón
     * puede quedarse corto y el recolector empieza a trillar, que se nota como
     * tirones de un segundo. Por eso se puede subir.
     */
    fun memoriaRecomendadaMb(c: Context): Int =
        (ramTotalMb(c) * 22 / 100).coerceIn(1536, 2560)

    /**
     * Tope del deslizador: 8 GB, y nunca mas que la RAM del equipo menos 2 GB.
     *
     * Los 2 GB de reserva no son por prudencia abstracta: es lo que el sistema
     * necesita para si mismo. Sin ese margen, en un equipo pequeno se podria
     * elegir un valor con el que Android mata la partida en cuanto abras
     * cualquier otra app —o directamente no arranca—.
     */
    fun memoriaMaximaMb(c: Context): Int =
        minOf(8192, ramTotalMb(c) - 2048).coerceIn(2048, 8192)

    /**
     * 0 = automática. Se vuelve a acotar AL LEER para que un valor guardado
     * por otra version, o en otro equipo con mas RAM, no quede fuera de rango.
     */
    fun memoriaMb(c: Context): Int {
        val guardada = prefs(c).getInt("memoria_mb", 0)
        if (guardada <= 0) return 0
        return guardada.coerceIn(1536, memoriaMaximaMb(c))
    }

    fun guardarMemoriaMb(c: Context, v: Int) =
        prefs(c).edit().putInt("memoria_mb", if (v == 0) 0 else v.coerceIn(1536, memoriaMaximaMb(c))).apply()

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
