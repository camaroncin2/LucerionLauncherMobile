package com.lucerion.launcher.motor

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Caja negra del proceso del juego.
 *
 * Cuando la ventana de la partida muere en duro, Android se lleva el proceso
 * y el rastro se pierde entre miles de líneas del registro del sistema. Aquí
 * queda escrito en un archivo propio: el motivo real, con su traza completa,
 * disponible aunque el registro del sistema ya haya rotado.
 */
object RegistroFallos {

    private fun archivo(contexto: Context) =
        File(contexto.getExternalFilesDir(null), "log/ultimo-fallo.txt")

    fun anotar(contexto: Context, fallo: Throwable) {
        runCatching {
            val texto = StringWriter().also { fallo.printStackTrace(PrintWriter(it)) }.toString()
            val f = archivo(contexto)
            f.parentFile?.mkdirs()
            f.writeText(texto)
        }
    }

    /**
     * Se engancha al hilo para que NINGÚN fallo del proceso del juego quede
     * sin registrar, ni siquiera los que nadie captura.
     */
    fun instalarRedDeSeguridad(contexto: Context) {
        val previo = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, fallo ->
            anotar(contexto, fallo)
            previo?.uncaughtException(hilo, fallo)
        }
    }
}
