package com.lucerion.launcher.ui.juego

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.tungsten.fclauncher.bridge.FCLBridge
import com.tungsten.fclauncher.bridge.FCLBridgeCallback
import com.tungsten.fclauncher.keycodes.LwjglGlfwKeycode

/**
 * Superficie del juego: la JVM arranca cuando la textura está disponible
 * (bridge.execute) — el mismo contrato que la JVMActivity de FCL, reescrito
 * mínimo (~200 líneas) porque la de FCL arrastra todo su sistema de menús.
 *
 * Controles de esta capa (validación):
 *  · Toque = ratón (mover con el dedo; toque corto = clic izquierdo,
 *    toque largo = clic derecho). En modo cámara el movimiento es relativo.
 *  · Fila de botones: ESC · E · ENTER · ⌨ (teclado del sistema para el chat).
 * El overlay táctil completo (cruceta, salto, cámara) es la fase siguiente.
 */
class JuegoActivity : Activity(), TextureView.SurfaceTextureListener {

    companion object {
        /** Traspaso estático del puente, como hace FCL con su JVMActivity. */
        var puente: FCLBridge? = null

        /** Carpeta de ejecución del juego (donde vive options.txt). */
        var dirJuego: java.io.File? = null
        private const val UMBRAL_TOQUE_LARGO_MS = 400L
    }

    /**
     * Minecraft debe renderizar EXACTAMENTE al tamaño del buffer de la
     * superficie: si difieren, el stride de cada fila queda desfasado y la
     * imagen sale rayada con columnas negras. FCL escribe este override en
     * options.txt antes de arrancar; nosotros igual.
     */
    private fun fijarResolucion(ancho: Int, alto: Int) {
        val dir = dirJuego ?: return
        val archivo = java.io.File(dir, "options.txt")
        val lineas = if (archivo.isFile) archivo.readLines().toMutableList() else mutableListOf()
        val valores = mapOf(
            "fullscreen" to "false",
            "overrideWidth" to ancho.toString(),
            "overrideHeight" to alto.toString(),
        )
        for ((clave, valor) in valores) {
            val idx = lineas.indexOfFirst { it.startsWith("$clave:") }
            if (idx >= 0) lineas[idx] = "$clave:$valor" else lineas.add("$clave:$valor")
        }
        archivo.writeText(lineas.joinToString(separator = System.lineSeparator()))
    }

    private var cursorAgarrado = false // modo cámara (GLFW_CURSOR_DISABLED)
    private var cursorX = 0f
    private var cursorY = 0f
    private var ultimoX = 0f
    private var ultimoY = 0f
    private var inicioToque = 0L
    private var seMovio = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val raiz = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val textura = TextureView(this).apply {
            surfaceTextureListener = this@JuegoActivity
            isOpaque = true
            setOnTouchListener { _, evento -> manejarToque(evento); true }
        }
        raiz.addView(
            textura,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        raiz.addView(
            crearBarraBotones(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ),
        )

        setContentView(raiz)
    }

    private fun crearBarraBotones(): LinearLayout {
        fun boton(texto: String, alPulsar: () -> Unit) = Button(this).apply {
            text = texto
            alpha = 0.55f
            setOnClickListener { alPulsar() }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(boton("ESC") { pulsarTecla(LwjglGlfwKeycode.KEY_ESCAPE.toInt()) })
            addView(boton("E") { pulsarTecla(LwjglGlfwKeycode.KEY_E.toInt()) })
            addView(boton("↵") { pulsarTecla(LwjglGlfwKeycode.KEY_ENTER.toInt()) })
            addView(
                boton("⌨") {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                },
            )
        }
    }

    // ── Superficie ───────────────────────────────────────────────────────────

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, ancho: Int, alto: Int) {
        val p = puente ?: run { finish(); return }
        fijarResolucion(ancho, alto)
        st.setDefaultBufferSize(ancho, alto)
        p.setSurfaceDestroyed(false)
        p.execute(
            Surface(st),
            object : FCLBridgeCallback {
                override fun onCursorModeChange(modo: Int) {
                    runOnUiThread { cursorAgarrado = (modo == FCLBridge.CursorDisabled) }
                }

                override fun onLog(log: String?) {
                    // El log completo va al archivo del motor (latest_game.log).
                }

                override fun onExit(codigo: Int) {
                    runOnUiThread {
                        puente = null
                        finish()
                    }
                }
            },
        )
        p.setSurfaceTexture(st)
        p.pushEventWindow(ancho, alto)
        cursorX = ancho / 2f
        cursorY = alto / 2f
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, ancho: Int, alto: Int) {
        puente?.pushEventWindow(ancho, alto)
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        puente?.setSurfaceDestroyed(true)
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

    // ── Toque = ratón ────────────────────────────────────────────────────────

    private fun manejarToque(evento: MotionEvent) {
        val p = puente ?: return
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                inicioToque = System.currentTimeMillis()
                seMovio = false
                ultimoX = evento.x
                ultimoY = evento.y
                if (!cursorAgarrado) {
                    cursorX = evento.x
                    cursorY = evento.y
                    p.pushEventPointer(cursorX, cursorY)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = evento.x - ultimoX
                val dy = evento.y - ultimoY
                if (dx * dx + dy * dy > 9f) seMovio = true
                ultimoX = evento.x
                ultimoY = evento.y
                if (cursorAgarrado) {
                    // Modo cámara: el movimiento es relativo.
                    cursorX += dx
                    cursorY += dy
                } else {
                    cursorX = evento.x
                    cursorY = evento.y
                }
                p.pushEventPointer(cursorX, cursorY)
            }

            MotionEvent.ACTION_UP -> {
                val duracion = System.currentTimeMillis() - inicioToque
                if (!seMovio) {
                    val boton = if (duracion >= UMBRAL_TOQUE_LARGO_MS) {
                        LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt()
                    } else {
                        LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt()
                    }
                    p.pushEventMouseButton(boton, true)
                    p.pushEventMouseButton(boton, false)
                }
            }
        }
    }

    // ── Teclado del sistema → juego (chat, /login, nombres) ──────────────────

    override fun dispatchKeyEvent(evento: KeyEvent): Boolean {
        val p = puente ?: return super.dispatchKeyEvent(evento)
        if (evento.action == KeyEvent.ACTION_DOWN || evento.action == KeyEvent.ACTION_MULTIPLE) {
            when (evento.keyCode) {
                KeyEvent.KEYCODE_ENTER -> pulsarTecla(LwjglGlfwKeycode.KEY_ENTER.toInt())
                KeyEvent.KEYCODE_DEL -> pulsarTecla(LwjglGlfwKeycode.KEY_BACKSPACE.toInt())
                else -> {
                    val caracter = evento.unicodeChar
                    if (caracter != 0) p.pushEventChar(caracter.toChar())
                }
            }
            return true
        }
        return super.dispatchKeyEvent(evento)
    }

    private fun pulsarTecla(codigo: Int) {
        puente?.pushEventKey(codigo, 0, true)
        puente?.pushEventKey(codigo, 0, false)
    }

    override fun onBackPressed() {
        // Atrás = ESC del juego (abre su menú de pausa); salir de verdad se
        // hace desde el propio Minecraft. Evita matar la JVM por accidente.
        pulsarTecla(LwjglGlfwKeycode.KEY_ESCAPE.toInt())
    }
}
