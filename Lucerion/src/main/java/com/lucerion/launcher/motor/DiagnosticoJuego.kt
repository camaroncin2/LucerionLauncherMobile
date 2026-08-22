package com.lucerion.launcher.motor

import com.tungsten.fclauncher.utils.FCLPath
import java.io.File

/**
 * Traduce el final de una partida a algo que el jugador entienda.
 *
 * Cuando el juego se cierra solo, la única pista vive en los registros
 * (crash-reports del juego y el log de la JVM). Antes había que sacarlos por
 * cable; ahora la app los lee, reconoce los casos frecuentes y explica qué
 * pasó y qué hacer.
 */
object DiagnosticoJuego {

    data class Resultado(val titulo: String, val detalle: String, val tecnico: String?)

    /** Firmas conocidas: fragmento del registro → explicación en español. */
    private val patrones = listOf(
        Triple(
            listOf("sable_rapier", "RapierPhysicsPipeline"),
            "Los mundos locales no funcionan en el móvil",
            "El mod de físicas Sable necesita un componente que solo existe en PC. " +
                "En el servidor de Cretania sí funciona, porque las físicas las calcula el servidor.",
        ),
        Triple(
            listOf("OutOfMemoryError", "Java heap space"),
            "El juego se quedó sin memoria",
            "Sube la memoria en Ajustes → Rendimiento (sin pasar el límite seguro) " +
                "o baja la distancia de renderizado dentro del juego.",
        ),
        Triple(
            listOf("Outdated server", "Outdated client", "Incompatible", "NeoForge Version"),
            "El servidor y tu juego no coinciden",
            "El servidor pide otra versión de NeoForge. Vuelve a entrar: el launcher " +
                "detecta el cambio y ofrece actualizar la instalación.",
        ),
        Triple(
            listOf("Connection refused", "Connection timed out", "UnknownHostException"),
            "No se pudo conectar con el servidor",
            "Revisa tu conexión a internet. Si tu conexión está bien, puede que el " +
                "servidor esté reiniciándose: prueba de nuevo en unos minutos.",
        ),
        Triple(
            listOf("SIGSEGV", "EXCEPTION_ACCESS_VIOLATION", "libEGL", "libvulkan"),
            "Falló el motor gráfico",
            "Suele arreglarse en Ajustes → Gráficos avanzados: prueba «Desactivar LRZ» " +
                "y, si sigue, «Renderizado directo a memoria».",
        ),
        Triple(
            listOf("Mixin", "mixins.json", "MixinApplyError"),
            "Un mod entró en conflicto al cargar",
            "Suele ser una descarga incompleta. Usa Ajustes → Partida → Reparar " +
                "instalación y vuelve a entrar.",
        ),
    )

    /**
     * Analiza la última partida. Devuelve null si terminó de forma normal
     * (salir por el menú) o si no hay registros que leer.
     */
    fun analizar(dirInstancia: File, codigoSalida: Int): Resultado? {
        if (codigoSalida == 0) return null

        val informe = File(dirInstancia, "crash-reports")
            .listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
        val registroJvm = File(FCLPath.LOG_DIR, "latest_game.log").takeIf { it.isFile }

        val texto = buildString {
            informe?.let { append(runCatching { it.readText() }.getOrDefault("")) }
            registroJvm?.let { append(runCatching { it.readText().takeLast(60_000) }.getOrDefault("")) }
        }
        if (texto.isBlank()) {
            return Resultado(
                "El juego se cerró de forma inesperada",
                "No quedaron registros de lo ocurrido. Si vuelve a pasar en el mismo " +
                    "momento (al entrar a un sitio, al usar algo), cuéntalo con ese detalle.",
                null,
            )
        }

        for ((firmas, titulo, detalle) in patrones) {
            if (firmas.any { texto.contains(it, ignoreCase = true) }) {
                return Resultado(titulo, detalle, primeraLineaUtil(texto))
            }
        }
        return Resultado(
            "El juego se cerró de forma inesperada",
            "No reconocimos la causa exacta. Abajo está la primera línea del informe " +
                "por si quieres reportarla.",
            primeraLineaUtil(texto),
        )
    }

    /** Primera línea con sustancia del informe (la descripción del fallo). */
    private fun primeraLineaUtil(texto: String): String? =
        texto.lineSequence()
            .map { it.trim() }
            .firstOrNull {
                (it.startsWith("Description:") || it.contains("Exception") || it.contains("Error")) &&
                    it.length in 12..300
            }
}
