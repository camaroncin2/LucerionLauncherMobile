package com.lucerion.launcher.ui.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lucerion.launcher.R

/**
 * Skin clásica de Minecraft (la textura original de Steve, 64×32), para que
 * la sección de skin muestre siempre un personaje: sin skin propia, así es
 * exactamente como te ven en el servidor.
 */
object SkinPorDefecto {

    private var cache: Bitmap? = null

    fun bitmap(contexto: Context): Bitmap {
        cache?.let { return it }
        val opciones = BitmapFactory.Options().apply {
            inScaled = false // pixel art: sin reescalado por densidad de pantalla
        }
        val bmp = BitmapFactory.decodeResource(contexto.resources, R.raw.skin_steve, opciones)
        cache = bmp
        return bmp
    }
}
