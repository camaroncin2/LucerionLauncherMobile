package com.lucerion.launcher.ui.juego

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Palanca (stick) de movimiento al estilo Bedrock moderno: base circular
 * translúcida con perilla que sigue el dedo. Traduce el ángulo a las cuatro
 * teclas de movimiento (con diagonales) y avisa solo cuando el estado cambia.
 */
class PalancaTactil(
    context: Context,
    private val alCambiar: (adelante: Boolean, atras: Boolean, izquierda: Boolean, derecha: Boolean) -> Unit,
    /** Sprint estilo Bedrock: doble-toque hacia adelante lo activa; soltar lo corta. */
    private val alCorrer: (Boolean) -> Unit = {},
) : View(context) {

    private var perillaX = 0f
    private var perillaY = 0f
    private var activa = false
    private var adelanteActivo = false
    private var finAdelante = 0L
    private var corriendo = false

    private fun actualizarSprint(adelante: Boolean) {
        val ahora = android.os.SystemClock.uptimeMillis()
        if (adelante && !adelanteActivo) {
            if (ahora - finAdelante < 280 && !corriendo) {
                corriendo = true
                alCorrer(true)
            }
        } else if (!adelante && adelanteActivo) {
            finAdelante = ahora
            if (corriendo) {
                corriendo = false
                alCorrer(false)
            }
        }
        adelanteActivo = adelante
    }

    // Anillo de laton grabado con perilla de bronce: la palanca es el control
    // que mas se mira, asi que lleva el detalle steampunk mas cuidado.
    private val pintaBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x990C1424.toInt()
    }
    private val pintaAro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xB2D5A84B.toInt()
    }
    private val pintaMarcas = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x59E8C06A
    }
    private val pintaPerilla = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE6D5A84B.toInt()
    }
    private val pintaPerillaBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF8C6B22.toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(evento: MotionEvent): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val radio = width / 2f
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                activa = true
                var dx = evento.x - cx
                var dy = evento.y - cy
                val distancia = hypot(dx, dy)
                val tope = radio * 0.62f
                if (distancia > tope) {
                    dx = dx / distancia * tope
                    dy = dy / distancia * tope
                }
                perillaX = dx
                perillaY = dy
                if (distancia < radio * 0.22f) {
                    // Zona muerta: perilla casi centrada, sin movimiento.
                    actualizarSprint(false)
                    alCambiar(false, false, false, false)
                } else {
                    val angulo = atan2(-dy, dx) // y de pantalla crece hacia abajo
                    val adelante = sin(angulo) > 0.38f
                    val atras = sin(angulo) < -0.38f
                    val derecha = cos(angulo) > 0.38f
                    val izquierda = cos(angulo) < -0.38f
                    actualizarSprint(adelante)
                    alCambiar(adelante, atras, izquierda, derecha)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activa = false
                perillaX = 0f
                perillaY = 0f
                actualizarSprint(false)
                alCambiar(false, false, false, false)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radio = width / 2f
        pintaAro.strokeWidth = radio * 0.055f
        canvas.drawCircle(cx, cy, radio * 0.94f, pintaBase)
        canvas.drawCircle(cx, cy, radio * 0.94f, pintaAro)

        // Marcas de brujula cada 45 grados, como un instrumento de laton.
        pintaMarcas.strokeWidth = radio * 0.035f
        for (i in 0 until 8) {
            val ang = Math.toRadians(i * 45.0)
            val dx = cos(ang).toFloat()
            val dy = sin(ang).toFloat()
            canvas.drawLine(
                cx + dx * radio * 0.72f, cy + dy * radio * 0.72f,
                cx + dx * radio * 0.86f, cy + dy * radio * 0.86f,
                pintaMarcas,
            )
        }

        pintaPerilla.color = if (activa) 0xFFFFC646.toInt() else 0xE6D5A84B.toInt()
        pintaPerillaBorde.strokeWidth = radio * 0.045f
        val px = cx + perillaX
        val py = cy + perillaY
        canvas.drawCircle(px, py, radio * 0.34f, pintaPerilla)
        canvas.drawCircle(px, py, radio * 0.34f, pintaPerillaBorde)
        // Tornillo central del pomo.
        canvas.drawCircle(px, py, radio * 0.09f, pintaPerillaBorde)
    }
}
