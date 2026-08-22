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
 * Botón táctil al estilo de los controles clásicos de Bedrock: cuadrado
 * translúcido de esquinas suaves con glifo blanco que se ilumina al presionar.
 *
 * Java Edition no incluye las texturas táctiles de Bedrock (pertenecen al
 * motor Bedrock, otro juego), así que los glifos se dibujan vectoriales
 * replicando su forma — nítidos a cualquier densidad de pantalla.
 */
class BotonTactil(
    context: Context,
    private val glifo: Glifo,
    private val conmutador: Boolean = false,
    private val alPresionar: () -> Unit,
    private val alSoltar: () -> Unit = {},
) : View(context) {

    enum class Glifo {
        FLECHA_ARRIBA, FLECHA_ABAJO, FLECHA_IZQUIERDA, FLECHA_DERECHA,
        SALTO, AGACHARSE, CHAT, PAUSA, INVENTARIO, TECLADO,
    }

    private var activo = false

    private val pintaFondo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pintaBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66FFFFFF
    }
    private val pintaGlifo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF2FFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(evento: MotionEvent): Boolean {
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (conmutador) {
                    activo = !activo
                    if (activo) alPresionar() else alSoltar()
                } else {
                    activo = true
                    alPresionar()
                }
                invalidate()
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

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radio = w * 0.16f
        pintaFondo.color = if (activo) 0x8CE8E8E8.toInt() else 0x59101010
        pintaBorde.strokeWidth = w * 0.028f
        val inset = pintaBorde.strokeWidth
        val marco = RectF(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(marco, radio, radio, pintaFondo)
        canvas.drawRoundRect(marco, radio, radio, pintaBorde)

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
        }
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
