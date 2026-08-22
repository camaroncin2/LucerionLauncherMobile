package com.lucerion.launcher.ui.skin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Previsualizador 3D de la skin: el modelo de jugador de Minecraft (cabeza,
 * torso, brazos y piernas, con su capa exterior) armado con cajas y la
 * textura de 64×64 mapeada cara por cara.
 *
 * Cada cara es un cuadrilátero: se proyectan sus cuatro esquinas y se estira
 * el trozo de textura correspondiente con una transformación de cuatro puntos.
 * Las caras se pintan de atrás hacia adelante (algoritmo del pintor), que para
 * un modelo de cajas convexas basta y sobra.
 *
 * Se arrastra para girar (horizontal) e inclinar (vertical).
 */
class VistaSkin3D(context: Context) : View(context) {

    private var textura: Bitmap? = null
    private var giro = -0.5f      // radianes, eje vertical
    private var inclinacion = 0f  // radianes, eje horizontal
    private var brazoDelgado = false
    /** Con el giro bloqueado la vista queda de frente y no responde al dedo. */
    private var interactivo = true

    private val pintura = Paint(Paint.FILTER_BITMAP_FLAG.inv()).apply {
        isAntiAlias = false
        isFilterBitmap = false // pixel art: nada de suavizado
    }
    private val matriz = Matrix()

    fun ponerTextura(bitmap: Bitmap?) {
        textura = bitmap
        invalidate()
    }

    fun ponerModeloDelgado(delgado: Boolean) {
        brazoDelgado = delgado
        invalidate()
    }

    /**
     * Modo plano: el mismo personaje, pero de frente y quieto — es la vista
     * "2D" de la app. Al reactivarlo vuelve la pose de tres cuartos.
     */
    fun ponerInteractivo(activo: Boolean) {
        if (interactivo == activo) return
        interactivo = activo
        giro = if (activo) -0.5f else 0f
        inclinacion = 0f
        invalidate()
    }

    /** Vuelve a la pose de presentación (tres cuartos). */
    fun reiniciarGiro() {
        giro = -0.5f
        inclinacion = 0f
        invalidate()
    }

    // ── Modelo ───────────────────────────────────────────────────────────────

    /**
     * Caja del modelo: centro y tamaño en unidades de píxel de Minecraft, más
     * el origen de su "red" de textura (el despliegue estándar de las skins).
     */
    private data class Caja(
        val cx: Float, val cy: Float, val cz: Float,
        val ancho: Float, val alto: Float, val fondo: Float,
        val uvX: Int, val uvY: Int,
        val capaExterior: Boolean = false,
    )

    private fun modelo(): List<Caja> {
        val anchoBrazo = if (brazoDelgado) 3f else 4f
        val xBrazo = 4f + anchoBrazo / 2f
        // Formato clásico (64×32): no existen las regiones del lado izquierdo
        // ni las capas exteriores salvo el sombrero — el juego refleja el lado
        // derecho. Aquí se hace lo mismo para que se vea igual que en partida.
        val clasica = (textura?.height ?: 64) == 32

        val cajas = mutableListOf(
            // Cabeza y su sombrero
            Caja(0f, -12f, 0f, 8f, 8f, 8f, 0, 0),
            Caja(0f, -12f, 0f, 8.9f, 8.9f, 8.9f, 32, 0, capaExterior = true),
            // Torso
            Caja(0f, -2f, 0f, 8f, 12f, 4f, 16, 16),
            // Brazo derecho (a la izquierda en pantalla)
            Caja(-xBrazo, -2f, 0f, anchoBrazo, 12f, 4f, 40, 16),
            // Pierna derecha
            Caja(-2f, 10f, 0f, 4f, 12f, 4f, 0, 16),
        )

        if (clasica) {
            // Miembros izquierdos: misma textura que los derechos.
            cajas += Caja(xBrazo, -2f, 0f, anchoBrazo, 12f, 4f, 40, 16)
            cajas += Caja(2f, 10f, 0f, 4f, 12f, 4f, 0, 16)
        } else {
            cajas += Caja(xBrazo, -2f, 0f, anchoBrazo, 12f, 4f, 32, 48)
            cajas += Caja(2f, 10f, 0f, 4f, 12f, 4f, 16, 48)
            // Capas exteriores (chaqueta, mangas, pantalones)
            cajas += Caja(0f, -2f, 0f, 8.7f, 12.7f, 4.7f, 16, 32, capaExterior = true)
            cajas += Caja(-xBrazo, -2f, 0f, anchoBrazo + 0.7f, 12.7f, 4.7f, 40, 32, capaExterior = true)
            cajas += Caja(xBrazo, -2f, 0f, anchoBrazo + 0.7f, 12.7f, 4.7f, 48, 48, capaExterior = true)
            cajas += Caja(-2f, 10f, 0f, 4.7f, 12.7f, 4.7f, 0, 32, capaExterior = true)
            cajas += Caja(2f, 10f, 0f, 4.7f, 12.7f, 4.7f, 0, 48, capaExterior = true)
        }
        return cajas
    }

    /** Las seis caras de una caja: vértices (índices) y rectángulo de textura. */
    private data class Cara(
        val v: IntArray,
        val u0: Int, val v0: Int, val u1: Int, val v1: Int,
        val sombra: Float,
    )

    private fun carasDe(c: Caja): List<Cara> {
        val a = c.ancho.toInt().coerceAtLeast(1)
        val al = c.alto.toInt().coerceAtLeast(1)
        val f = c.fondo.toInt().coerceAtLeast(1)
        val x = c.uvX
        val y = c.uvY
        // Vértices: 0..3 frente (z-), 4..7 detrás (z+)
        return listOf(
            // frente
            Cara(intArrayOf(0, 1, 2, 3), x + f, y + f, x + f + a, y + f + al, 1.00f),
            // detrás
            Cara(intArrayOf(5, 4, 7, 6), x + f + a + f, y + f, x + f + a + f + a, y + f + al, 0.78f),
            // derecha (x-)
            Cara(intArrayOf(4, 0, 6, 2), x, y + f, x + f, y + f + al, 0.86f),
            // izquierda (x+)
            Cara(intArrayOf(1, 5, 3, 7), x + f + a, y + f, x + f + a + f, y + f + al, 0.86f),
            // arriba
            Cara(intArrayOf(4, 5, 0, 1), x + f, y, x + f + a, y + f, 1.00f),
            // abajo
            Cara(intArrayOf(2, 3, 6, 7), x + f + a, y, x + f + a + a, y + f, 0.70f),
        )
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val bmp = textura ?: return
        val ancho = width.toFloat()
        val alto = height.toFloat()
        if (ancho <= 0f || alto <= 0f) return

        // Escala: el modelo mide 32 unidades de alto; se deja aire alrededor.
        val escala = alto / 44f
        val ox = ancho / 2f
        val oy = alto / 2f + 2f * escala

        val cosG = cos(giro)
        val senG = sin(giro)
        val cosI = cos(inclinacion)
        val senI = sin(inclinacion)

        // Cada cara con su profundidad, para ordenarlas de atrás hacia adelante.
        data class CaraProyectada(val puntos: FloatArray, val z: Float, val cara: Cara)

        val proyectadas = ArrayList<CaraProyectada>(72)

        for (caja in modelo()) {
            val hx = caja.ancho / 2f
            val hy = caja.alto / 2f
            val hz = caja.fondo / 2f
            // Orden de vértices: (x,y,z) con signos — frente = z negativo
            val locales = arrayOf(
                floatArrayOf(-hx, -hy, -hz), floatArrayOf(hx, -hy, -hz),
                floatArrayOf(-hx, hy, -hz), floatArrayOf(hx, hy, -hz),
                floatArrayOf(-hx, -hy, hz), floatArrayOf(hx, -hy, hz),
                floatArrayOf(-hx, hy, hz), floatArrayOf(hx, hy, hz),
            )
            val pantalla = Array(8) { FloatArray(2) }
            val prof = FloatArray(8)
            for (i in 0 until 8) {
                val px = locales[i][0] + caja.cx
                val py = locales[i][1] + caja.cy
                val pz = locales[i][2] + caja.cz
                // Giro sobre el eje vertical, luego inclinación.
                val rx = px * cosG + pz * senG
                val rz = -px * senG + pz * cosG
                val ry = py * cosI - rz * senI
                val rz2 = py * senI + rz * cosI
                pantalla[i][0] = ox + rx * escala
                pantalla[i][1] = oy + ry * escala
                prof[i] = rz2
            }
            for (cara in carasDe(caja)) {
                val idx = cara.v
                val z = (prof[idx[0]] + prof[idx[1]] + prof[idx[2]] + prof[idx[3]]) / 4f
                // Descarte de caras traseras: si el cuadrilátero queda con el
                // orden invertido en pantalla, mira hacia el otro lado.
                val ax = pantalla[idx[1]][0] - pantalla[idx[0]][0]
                val ay = pantalla[idx[1]][1] - pantalla[idx[0]][1]
                val bx = pantalla[idx[2]][0] - pantalla[idx[0]][0]
                val by = pantalla[idx[2]][1] - pantalla[idx[0]][1]
                if (ax * by - ay * bx <= 0f) continue
                proyectadas += CaraProyectada(
                    floatArrayOf(
                        pantalla[idx[0]][0], pantalla[idx[0]][1],
                        pantalla[idx[1]][0], pantalla[idx[1]][1],
                        pantalla[idx[2]][0], pantalla[idx[2]][1],
                        pantalla[idx[3]][0], pantalla[idx[3]][1],
                    ),
                    z, cara,
                )
            }
        }

        // Painter: lo más lejano primero (z mayor = más atrás en esta cámara).
        proyectadas.sortByDescending { it.z }

        for (p in proyectadas) {
            val c = p.cara
            val u0 = c.u0.coerceIn(0, bmp.width)
            val v0 = c.v0.coerceIn(0, bmp.height)
            val u1 = c.u1.coerceIn(0, bmp.width)
            val v1 = c.v1.coerceIn(0, bmp.height)
            if (u1 <= u0 || v1 <= v0) continue

            val origen = floatArrayOf(
                u0.toFloat(), v0.toFloat(),
                u1.toFloat(), v0.toFloat(),
                u0.toFloat(), v1.toFloat(),
                u1.toFloat(), v1.toFloat(),
            )
            matriz.reset()
            if (!matriz.setPolyToPoly(origen, 0, p.puntos, 0, 4)) continue

            // Sombreado plano por orientación: da volumen sin iluminación real.
            val nivel = (255 * c.sombra).toInt().coerceIn(0, 255)
            pintura.colorFilter = android.graphics.PorterDuffColorFilter(
                android.graphics.Color.argb(255, nivel, nivel, nivel),
                android.graphics.PorterDuff.Mode.MULTIPLY,
            )
            canvas.save()
            canvas.concat(matriz)
            canvas.clipRect(u0, v0, u1, v1)
            canvas.drawBitmap(bmp, 0f, 0f, pintura)
            canvas.restore()
        }
    }

    // ── Giro con el dedo ─────────────────────────────────────────────────────

    private var ultimoX = 0f
    private var ultimoY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(evento: MotionEvent): Boolean {
        if (!interactivo) return false
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                ultimoX = evento.x
                ultimoY = evento.y
            }
            MotionEvent.ACTION_MOVE -> {
                giro += (evento.x - ultimoX) * 0.01f
                // La inclinación se limita: mirar el modelo desde arriba o
                // abajo del todo lo deja irreconocible.
                inclinacion = (inclinacion + (evento.y - ultimoY) * 0.006f)
                    .coerceIn(-0.6f, 0.6f)
                ultimoX = evento.x
                ultimoY = evento.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}
