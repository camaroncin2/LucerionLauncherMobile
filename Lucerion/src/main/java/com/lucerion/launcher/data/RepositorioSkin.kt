package com.lucerion.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Skin del jugador. El archivo elegido se copia a la carpeta de la app
 * (`skin.png`): así sobrevive a que el original se borre o cambie de sitio, y
 * el motor puede leerlo al lanzar el juego sin depender de permisos.
 */
object RepositorioSkin {

    /** Cuenta libre = skin local; el motor la sirve por su Yggdrasil interno. */
    fun archivoSkin(contexto: Context) = File(contexto.filesDir, "skin.png")

    fun tieneSkin(contexto: Context) = archivoSkin(contexto).isFile

    private fun prefs(contexto: Context) =
        contexto.getSharedPreferences("lucerion", Context.MODE_PRIVATE)

    /** Modelo del cuerpo: clásico (Steve, brazo de 4 px) o delgado (Alex, 3 px). */
    fun modeloDelgado(contexto: Context) = prefs(contexto).getBoolean("skin_delgada", false)

    fun guardarModeloDelgado(contexto: Context, delgado: Boolean) =
        prefs(contexto).edit().putBoolean("skin_delgada", delgado).apply()

    fun vista3D(contexto: Context) = prefs(contexto).getBoolean("skin_3d", true)

    fun guardarVista3D(contexto: Context, activa: Boolean) =
        prefs(contexto).edit().putBoolean("skin_3d", activa).apply()

    /**
     * Importa la imagen elegida. Devuelve null si todo fue bien, o el motivo
     * del rechazo: solo se aceptan PNG con las medidas de skin de Minecraft.
     */
    fun importar(contexto: Context, origen: Uri): String? {
        return try {
            val bytes = contexto.contentResolver.openInputStream(origen)?.use { it.readBytes() }
                ?: return "No se pudo leer la imagen elegida."
            if (bytes.size > 2_000_000) return "La imagen es demasiado grande para ser una skin."

            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return "El archivo no es una imagen válida."
            val medidaOk = (bmp.width == 64 && (bmp.height == 64 || bmp.height == 32)) ||
                (bmp.width == 128 && bmp.height == 128) // skins HD
            if (!medidaOk) {
                return "Una skin debe medir 64×64 (o 64×32). Esta mide ${bmp.width}×${bmp.height}."
            }
            archivoSkin(contexto).outputStream().use { it.write(bytes) }
            null
        } catch (e: Exception) {
            "No se pudo importar: ${e.message ?: "error desconocido"}"
        }
    }

    fun cargarBitmap(contexto: Context): Bitmap? {
        val f = archivoSkin(contexto)
        if (!f.isFile) return null
        return runCatching {
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply {
                inScaled = false // pixel art: sin reescalado por densidad
            })
        }.getOrNull()
    }

    fun quitar(contexto: Context) {
        archivoSkin(contexto).delete()
    }
}
