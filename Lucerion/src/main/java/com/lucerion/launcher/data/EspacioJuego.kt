package com.lucerion.launcher.data

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * Espacio que ocupa Cretania en el teléfono, y cómo recuperarlo.
 *
 * Todo lo que descarga el launcher vive en almacenamiento privado de la app,
 * así que desinstalar ya no deja residuos: Android borra esas carpetas solo.
 * Lo que faltaba era poder liberar el gigabyte y medio SIN desinstalar, que es
 * lo que uno quiere de verdad cuando se queda sin espacio — Android no permite
 * preguntar nada durante la desinstalación (no hay forma de ejecutar código en
 * ese momento), así que la decisión tiene que poder tomarse desde dentro.
 */
object EspacioJuego {

    /** Carpeta con la instancia, los registros y lo descargado por el motor. */
    fun dirDatos(contexto: Context): File? = contexto.getExternalFilesDir(null)

    /** Suma recursiva. Puede tardar en un árbol de miles de archivos: fuera del hilo principal. */
    fun bytesDe(raiz: File?): Long {
        if (raiz == null || !raiz.exists()) return 0L
        if (raiz.isFile) return raiz.length()
        // Iterativo a propósito: la instancia anida bastante y una versión
        // recursiva podía desbordar la pila en árboles profundos.
        var total = 0L
        val pendientes = ArrayDeque<File>().apply { add(raiz) }
        while (pendientes.isNotEmpty()) {
            val actual = pendientes.removeFirst()
            val hijos = actual.listFiles() ?: continue
            for (h in hijos) if (h.isDirectory) pendientes.add(h) else total += h.length()
        }
        return total
    }

    fun bytesJuego(contexto: Context): Long = bytesDe(dirDatos(contexto))

    fun bytesInternos(contexto: Context): Long =
        bytesDe(contexto.filesDir) + bytesDe(contexto.cacheDir)

    /**
     * ¿Hay una partida viva? La comprobación no puede ser la variable del
     * juego: corre en OTRO proceso y el launcher no la ve. Se mira la lista de
     * procesos propios, que es lo único compartido entre los dos.
     */
    fun partidaEnCurso(contexto: Context): Boolean {
        val am = contexto.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        return runCatching {
            am.runningAppProcesses.orEmpty().any { it.processName.endsWith(":juego") }
        }.getOrDefault(false)
    }

    /**
     * Borra lo descargado y conserva los ajustes. La próxima entrada lo baja
     * otra vez. Devuelve false si hay una partida corriendo: borrarle los
     * archivos por debajo la haría caer de la peor manera.
     */
    fun borrarJuego(contexto: Context): Boolean {
        if (partidaEnCurso(contexto)) return false
        dirDatos(contexto)?.listFiles()?.forEach { it.deleteRecursively() }
        contexto.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        return true
    }

    /** Lo anterior y además la configuración: deja la app como recién instalada. */
    fun restablecerTodo(contexto: Context): Boolean {
        if (!borrarJuego(contexto)) return false
        for (archivo in listOf("lucerion", "lucerion-cuenta")) {
            contexto.getSharedPreferences(archivo, Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        contexto.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        return true
    }

    /** Tamaño en palabras del jugador, no en bytes crudos. */
    fun enPalabras(bytes: Long): String = when {
        bytes <= 0L -> "nada todavía"
        bytes < 1024L * 1024 -> "%.0f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.0f MB".format(bytes / 1048576.0)
        else -> "%.2f GB".format(bytes / 1073741824.0)
    }
}
