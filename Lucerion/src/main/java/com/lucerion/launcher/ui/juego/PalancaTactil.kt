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
) : View(context) {

    private var perillaX = 0f
    private var perillaY = 0f
    private var activa = false

    private val pintaBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x40101010
    }
    private val pintaAro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66FFFFFF
    }
    private val pintaPerilla = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xB3E8E8E8.toInt()
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
                    alCambiar(false, false, false, false)
                } else {
                    val angulo = atan2(-dy, dx) // y de pantalla crece hacia abajo
                    val adelante = sin(angulo) > 0.38f
                    val atras = sin(angulo) < -0.38f
                    val derecha = cos(angulo) > 0.38f
                    val izquierda = cos(angulo) < -0.38f
                    alCambiar(adelante, atras, izquierda, derecha)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activa = false
                perillaX = 0f
                perillaY = 0f
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
        pintaAro.strokeWidth = radio * 0.045f
        canvas.drawCircle(cx, cy, radio * 0.94f, pintaBase)
        canvas.drawCircle(cx, cy, radio * 0.94f, pintaAro)
        pintaPerilla.color = if (activa) 0xE6FFFFFF.toInt() else 0xB3E8E8E8.toInt()
        canvas.drawCircle(cx + perillaX, cy + perillaY, radio * 0.34f, pintaPerilla)
    }
}
