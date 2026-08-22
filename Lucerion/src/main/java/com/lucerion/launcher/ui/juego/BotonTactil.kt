package com.lucerion.launcher.ui.juego

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Botón táctil: distribución y glifos de los controles clásicos de Bedrock,
 * vestidos con la identidad de Lucerion — placa navy con marco de latón y
 * remaches, que se enciende en ámbar al presionar.
 *
 * Java Edition no incluye las texturas táctiles de Bedrock (pertenecen al
 * motor Bedrock, otro juego), así que los glifos se dibujan vectoriales
 * replicando su forma — nítidos a cualquier densidad de pantalla.
 */
class BotonTactil(
    context: Context,
    private val glifo: Glifo,
    private val conmutador: Boolean = false,
    /** Si se define, se dibuja este texto en vez del glifo (botones personalizados). */
    private val texto: String? = null,
    private val alPresionar: () -> Unit,
    private val alSoltar: () -> Unit = {},
    /**
     * Arrastre mientras se mantiene (dx, dy): permite p. ej. seguir moviendo
     * la cámara con el MISMO dedo que sostiene GOLPEAR, como en Bedrock.
     */
    private val alArrastrar: ((Float, Float) -> Unit)? = null,
) : View(context) {

    private var arrastreX = 0f
    private var arrastreY = 0f

    enum class Glifo {
        FLECHA_ARRIBA, FLECHA_ABAJO, FLECHA_IZQUIERDA, FLECHA_DERECHA,
        SALTO, AGACHARSE, CHAT, PAUSA, INVENTARIO, TECLADO, GOLPEAR, ENGRANAJE,
    }

    private var activo = false

    // Identidad Lucerion: placa de navy profundo con marco de laton y
    // remaches en las esquinas; al presionar, el laton se enciende como
    // metal al rojo. Misma paleta que el resto de la app.
    private val pintaFondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pintaBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pintaRemache = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pintaGlifo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ORO_CLARO
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private companion object {
        const val NAVY = 0xA60C1424.toInt()        // placa en reposo
        const val NAVY_ACTIVO = 0xD9241A0C.toInt() // placa presionada (calida)
        const val ORO = 0xD5A84B or (0xB2 shl 24)
        const val ORO_VIVO = 0xFFFFC646.toInt()
        const val ORO_CLARO = 0xF2E8C06A.toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(evento: MotionEvent): Boolean {
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                arrastreX = evento.rawX
                arrastreY = evento.rawY
                if (conmutador) {
                    activo = !activo
                    if (activo) alPresionar() else alSoltar()
                } else {
                    activo = true
                    alPresionar()
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (alArrastrar != null) {
                    alArrastrar.invoke(evento.rawX - arrastreX, evento.rawY - arrastreY)
                    arrastreX = evento.rawX
                    arrastreY = evento.rawY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!conmutador) {
                    activo = false
                    alSoltar()
                    invalidate()
                }
            }
        }
        return true
    }

    /**
     * Al reconstruirse el HUD (cambiar de palanca a cruceta, salir del editor)
     * la vista se quita de pantalla. Un conmutador encendido ignora UP y
     * CANCEL por diseño, así que la tecla se quedaba PULSADA en el juego para
     * siempre: el personaje seguía agachado con el botón nuevo apagado.
     */
    override fun onDetachedFromWindow() {
        if (conmutador && activo) {
            activo = false
            alSoltar()
        }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radio = w * 0.16f
        pintaFondo.color = if (activo) NAVY_ACTIVO else NAVY
        pintaBorde.color = if (activo) ORO_VIVO else ORO
        pintaGlifo.color = if (activo) ORO_VIVO else ORO_CLARO
        pintaBorde.strokeWidth = w * 0.045f
        val inset = pintaBorde.strokeWidth
        val marco = RectF(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(marco, radio, radio, pintaFondo)
        canvas.drawRoundRect(marco, radio, radio, pintaBorde)

        // Remaches: cuatro puntos de laton en las esquinas de la placa.
        pintaRemache.color = if (activo) ORO_VIVO else ORO
        val margen = w * 0.155f
        val rRemache = w * 0.030f
        for (rx in listOf(margen, w - margen)) {
            for (ry in listOf(margen, h - margen)) {
                canvas.drawCircle(rx, ry, rRemache, pintaRemache)
            }
        }

        if (texto != null) {
            pintaGlifo.style = Paint.Style.FILL
            pintaGlifo.textAlign = Paint.Align.CENTER
            pintaGlifo.textSize = h * if (texto.length <= 2) 0.42f else 0.26f
            pintaGlifo.isFakeBoldText = true
            val medio = (pintaGlifo.descent() + pintaGlifo.ascent()) / 2f
            canvas.drawText(texto, w / 2f, h / 2f - medio, pintaGlifo)
            return
        }

        when (glifo) {
            Glifo.FLECHA_ARRIBA -> flecha(canvas, 0f)
            Glifo.FLECHA_DERECHA -> flecha(canvas, 90f)
            Glifo.FLECHA_ABAJO -> flecha(canvas, 180f)
            Glifo.FLECHA_IZQUIERDA -> flecha(canvas, 270f)
            Glifo.SALTO -> salto(canvas)
            Glifo.AGACHARSE -> agacharse(canvas)
            Glifo.CHAT -> chat(canvas)
            Glifo.PAUSA -> pausa(canvas)
            Glifo.INVENTARIO -> inventario(canvas)
            Glifo.TECLADO -> teclado(canvas)
            Glifo.GOLPEAR -> golpear(canvas)
            Glifo.ENGRANAJE -> engranaje(canvas)
        }
    }

    /** Engranaje: el menu del juego — la marca de Lucerion hecha boton. */
    private fun engranaje(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val rDiente = w * 0.34f
        pintaGlifo.style = Paint.Style.FILL
        // Ocho dientes alrededor del cubo.
        for (i in 0 until 8) {
            val ang = Math.toRadians(i * 45.0)
            val dx = Math.cos(ang).toFloat()
            val dy = Math.sin(ang).toFloat()
            c.save()
            c.rotate(i * 45f, cx, cy)
            c.drawRoundRect(
                RectF(cx - w * 0.055f, cy - rDiente - w * 0.075f, cx + w * 0.055f, cy - rDiente + w * 0.03f),
                w * 0.02f, w * 0.02f, pintaGlifo,
            )
            c.restore()
        }
        pintaGlifo.style = Paint.Style.STROKE
        pintaGlifo.strokeWidth = w * 0.085f
        c.drawCircle(cx, cy, rDiente * 0.78f, pintaGlifo)
        pintaGlifo.strokeWidth = w * 0.055f
        c.drawCircle(cx, cy, rDiente * 0.30f, pintaGlifo)
    }

    /** Espada estilizada: golpear/romper (mantener = seguir rompiendo). */
    private fun golpear(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        pintaGlifo.style = Paint.Style.STROKE
        pintaGlifo.strokeWidth = w * 0.075f
        // Hoja en diagonal
        c.drawLine(w * 0.30f, h * 0.70f, w * 0.66f, h * 0.34f, pintaGlifo)
        pintaGlifo.strokeWidth = w * 0.055f
        // Guarda perpendicular
        c.drawLine(w * 0.54f, h * 0.30f, w * 0.72f, h * 0.48f, pintaGlifo)
        // Empunadura
        c.drawLine(w * 0.70f, h * 0.30f, w * 0.80f, h * 0.20f, pintaGlifo)
        // Punta
        val punta = Path().apply {
            moveTo(w * 0.24f, h * 0.64f)
            lineTo(w * 0.20f, h * 0.80f)
            lineTo(w * 0.36f, h * 0.76f)
        }
        pintaGlifo.style = Paint.Style.FILL
        c.drawPath(punta, pintaGlifo)
    }

    /** Flecha maciza tipo Bedrock (triángulo con cola), rotada según dirección. */
    private fun flecha(c: Canvas, angulo: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.save()
        c.rotate(angulo, w / 2f, h / 2f)
        val p = Path().apply {
            moveTo(w * 0.50f, h * 0.20f)
            lineTo(w * 0.80f, h * 0.55f)
            lineTo(w * 0.62f, h * 0.55f)
            lineTo(w * 0.62f, h * 0.80f)
            lineTo(w * 0.38f, h * 0.80f)
            lineTo(w * 0.38f, h * 0.55f)
            lineTo(w * 0.20f, h * 0.55f)
            close()
        }
        pintaGlifo.style = Paint.Style.FILL
        c.drawPath(p, pintaGlifo)
        c.restore()
    }

    /** Rombo vacío: el icono de saltar de Bedrock. */
    private fun salto(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = Path().apply {
            moveTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.78f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.78f)
            lineTo(w * 0.22f, h * 0.50f)
            close()
        }
        pintaGlifo.style = Paint.Style.STROKE
        pintaGlifo.strokeWidth = w * 0.055f
        c.drawPath(p, pintaGlifo)
    }

    /** Doble cheurón hacia abajo: agacharse (centro de la cruceta en Bedrock). */
    private fun agacharse(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        pintaGlifo.style = Paint.Style.STROKE
        pintaGlifo.strokeWidth = w * 0.065f
        for (y0 in listOf(0.28f, 0.52f)) {
            val p = Path().apply {
                moveTo(w * 0.30f, h * y0)
                lineTo(w * 0.50f, h * (y0 + 0.16f))
                lineTo(w * 0.70f, h * y0)
            }
            c.drawPath(p, pintaGlifo)
        }
    }

    /** Globo de diálogo: abrir el chat. */
    private fun chat(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        pintaGlifo.style = Paint.Style.STROKE
        pintaGlifo.strokeWidth = w * 0.05f
        val globo = RectF(w * 0.20f, h * 0.24f, w * 0.80f, h * 0.62f)
        c.drawRoundRect(globo, w * 0.10f, w * 0.10f, pintaGlifo)
        val cola = Path().apply {
            moveTo(w * 0.34f, h * 0.62f)
            lineTo(w * 0.34f, h * 0.78f)
            lineTo(w * 0.52f, h * 0.62f)
        }
        c.drawPath(cola, pintaGlifo)
    }

    /** Dos barras: pausa (ESC). */
    private fun pausa(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        pintaGlifo.style = Paint.Style.FILL
        c.drawRoundRect(RectF(w * 0.36f, h * 0.30f, w * 0.45f, h * 0.70f), w * 0.02f, w * 0.02f, pintaGlifo)
        c.drawRoundRect(RectF(w * 0.55f, h * 0.30f, w * 0.64f, h * 0.70f), w * 0.02f, w * 0.02f, pintaGlifo)
    }

    /** Tres puntos: inventario (como el botón de la hotbar de Bedrock). */
    private fun inventario(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        pintaGlifo.style = Paint.Style.FILL
        for (x in listOf(0.30f, 0.50f, 0.70f)) {
            c.drawCircle(w * x, h * 0.5f, w * 0.055f, pintaGlifo)
        }
    }

    /** Teclado: marco con teclas y barra espaciadora. */
    private fun teclado(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        pintaGlifo.style = Paint.Style.STROKE
        pintaGlifo.strokeWidth = w * 0.045f
        c.drawRoundRect(RectF(w * 0.18f, h * 0.30f, w * 0.82f, h * 0.70f), w * 0.06f, w * 0.06f, pintaGlifo)
        pintaGlifo.style = Paint.Style.FILL
        for (x in listOf(0.32f, 0.44f, 0.56f, 0.68f)) {
            c.drawCircle(w * x, h * 0.43f, w * 0.035f, pintaGlifo)
        }
        c.drawRoundRect(RectF(w * 0.34f, h * 0.55f, w * 0.66f, h * 0.61f), w * 0.02f, w * 0.02f, pintaGlifo)
    }
}
