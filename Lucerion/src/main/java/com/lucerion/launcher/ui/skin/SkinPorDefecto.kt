package com.lucerion.launcher.ui.skin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * Skin clásica de Minecraft dibujada a mano (64×64), para que la sección de
 * skin muestre siempre un personaje aunque el jugador no haya elegido nada:
 * sin skin propia, así es como te ven en el servidor.
 *
 * Se pinta por regiones de la red estándar de skins en vez de traer un PNG:
 * no depende de assets con licencia ajena y pesa lo que ocupa este archivo.
 */
object SkinPorDefecto {

    // Paleta del personaje clásico.
    private const val PIEL = 0xFFB58B67.toInt()
    private const val PIEL_OSCURA = 0xFF9C7350.toInt()
    private const val PELO = 0xFF3F2A18.toInt()
    private const val OJO_BLANCO = 0xFFEEEEEE.toInt()
    private const val OJO = 0xFF3B5DA7.toInt()
    private const val BOCA = 0xFF6B4A32.toInt()
    private const val CAMISA = 0xFF00AAAA.toInt()
    private const val CAMISA_OSCURA = 0xFF008B8B.toInt()
    private const val PANTALON = 0xFF3B4A99.toInt()
    private const val PANTALON_OSCURO = 0xFF2E3B7A.toInt()
    private const val ZAPATO = 0xFF4C4C4C.toInt()

    private val cache: Bitmap by lazy { generar() }

    fun bitmap(): Bitmap = cache

    private fun generar(): Bitmap {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bmp)
        val p = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            p.color = color
            lienzo.drawRect(Rect(x, y, x + w, y + h), p)
        }

        // ── Cabeza (red: derecha, frente, izquierda, detrás en fila) ─────────
        rect(0, 8, 32, 8, PIEL)          // los cuatro lados
        rect(8, 0, 8, 8, PELO)           // arriba: pelo
        rect(16, 0, 8, 8, PIEL_OSCURA)   // abajo (cuello)
        // Pelo cayendo por los lados y la nuca
        rect(0, 8, 32, 3, PELO)
        rect(24, 8, 8, 8, PELO)          // detrás: nuca
        // Cara (frente ocupa x 8..16)
        rect(9, 12, 2, 2, OJO_BLANCO)
        rect(13, 12, 2, 2, OJO_BLANCO)
        rect(10, 12, 1, 2, OJO)
        rect(13, 12, 1, 2, OJO)
        rect(11, 14, 2, 1, PIEL_OSCURA)  // nariz
        rect(10, 15, 4, 1, BOCA)         // boca

        // ── Torso ────────────────────────────────────────────────────────────
        rect(16, 16, 24, 4, CAMISA_OSCURA) // arriba y abajo del torso
        rect(16, 20, 32, 12, CAMISA)       // los cuatro lados
        rect(20, 20, 8, 12, CAMISA)        // frente
        rect(20, 26, 8, 6, CAMISA_OSCURA)  // sombra baja de la camisa

        // ── Brazo derecho ────────────────────────────────────────────────────
        rect(40, 16, 12, 4, CAMISA_OSCURA)
        rect(40, 20, 16, 12, PIEL)
        rect(44, 20, 4, 6, CAMISA)         // manga
        rect(40, 20, 4, 6, CAMISA_OSCURA)
        rect(48, 20, 4, 6, CAMISA_OSCURA)
        rect(52, 20, 4, 6, CAMISA_OSCURA)

        // ── Brazo izquierdo (espejo, en la zona 1.8+) ────────────────────────
        rect(32, 48, 12, 4, CAMISA_OSCURA)
        rect(32, 52, 16, 12, PIEL)
        rect(36, 52, 4, 6, CAMISA)
        rect(32, 52, 4, 6, CAMISA_OSCURA)
        rect(40, 52, 4, 6, CAMISA_OSCURA)
        rect(44, 52, 4, 6, CAMISA_OSCURA)

        // ── Pierna derecha ───────────────────────────────────────────────────
        rect(0, 16, 12, 4, PANTALON_OSCURO)
        rect(0, 20, 16, 12, PANTALON)
        rect(4, 26, 4, 6, PANTALON_OSCURO)
        rect(0, 30, 16, 2, ZAPATO)

        // ── Pierna izquierda ─────────────────────────────────────────────────
        rect(16, 48, 12, 4, PANTALON_OSCURO)
        rect(16, 52, 16, 12, PANTALON)
        rect(20, 58, 4, 6, PANTALON_OSCURO)
        rect(16, 62, 16, 2, ZAPATO)

        return bmp
    }
}
