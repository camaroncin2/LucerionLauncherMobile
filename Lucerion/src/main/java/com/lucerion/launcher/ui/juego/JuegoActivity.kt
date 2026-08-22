package com.lucerion.launcher.ui.juego

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.tungsten.fclauncher.bridge.FCLBridge
import com.tungsten.fclauncher.bridge.FCLBridgeCallback
import com.tungsten.fclauncher.keycodes.LwjglGlfwKeycode

/**
 * Superficie del juego + controles táctiles.
 *
 * Esquema de control:
 *  · Cruceta abajo-izquierda: mantener = caminar (W/A/S/D). SALTO (mantener)
 *    y AGACHARSE (conmutador) abajo-derecha.
 *  · En juego (cámara agarrada): arrastrar = mirar. En la MITAD DERECHA:
 *    mantener quieto ≥250 ms = ROMPER (clic izquierdo sostenido hasta soltar);
 *    toque corto = usar/colocar (clic derecho); toque corto en la mitad
 *    izquierda = golpe rápido.
 *  · En menús (cursor libre): el dedo es el ratón; toque corto = clic.
 *  · Fila superior: ESC · E · ⌨ — press al tocar y release al soltar
 *    (mandarlos juntos en el mismo instante los perdía la cola de entrada).
 *  · ⌨ abre el teclado del sistema; lo escrito viaja al juego (chat, /login).
 */
class JuegoActivity : Activity(), TextureView.SurfaceTextureListener {

    companion object {
        var puente: FCLBridge? = null
        var dirJuego: java.io.File? = null
        private const val UMBRAL_ROMPER_MS = 250L
        private const val UMBRAL_MOVIMIENTO_PX2 = 100f
    }

    private var cursorAgarrado = false
    private var cursorX = 0f
    private var cursorY = 0f
    private var agachado = false
    private lateinit var entradaTexto: EditText
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // ── Ciclo de vida y layout ───────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Pantalla completa REAL, incluida la zona de la cámara: el buffer del
        // juego debe medir lo mismo que el panel entero.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val raiz = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val textura = TextureView(this).apply {
            surfaceTextureListener = this@JuegoActivity
            isOpaque = true
            setOnTouchListener { _, evento -> manejarToque(evento); true }
        }
        raiz.addView(
            textura,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        entradaTexto = crearEntradaTexto()
        raiz.addView(entradaTexto, FrameLayout.LayoutParams(1, 1))

        raiz.addView(
            crearBarraSuperior(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply { topMargin = dp(10); rightMargin = dp(10) },
        )
        raiz.addView(
            crearCruceta(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ).apply { bottomMargin = dp(14); leftMargin = dp(14) },
        )
        raiz.addView(
            crearBotonesAccion(),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ).apply { bottomMargin = dp(14); rightMargin = dp(14) },
        )

        setContentView(raiz)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ── Botones: press al tocar, release al soltar ───────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun botonTecla(texto: String, codigo: Short, tam: Int = 56): Button =
        Button(this).apply {
            text = texto
            alpha = 0.6f
            minWidth = dp(tam)
            minHeight = dp(tam)
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.alpha = 1f
                        puente?.pushEventKey(codigo.toInt(), 0, true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.alpha = 0.6f
                        puente?.pushEventKey(codigo.toInt(), 0, false)
                    }
                }
                true
            }
        }

    private fun crearBarraSuperior(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(botonTecla("ESC", LwjglGlfwKeycode.KEY_ESCAPE))
        addView(botonTecla("E", LwjglGlfwKeycode.KEY_E))
        addView(
            Button(this@JuegoActivity).apply {
                text = "⌨"
                alpha = 0.6f
                setOnClickListener { abrirTeclado() }
            },
        )
    }

    private fun crearCruceta(): FrameLayout {
        val lado = 62
        val cont = FrameLayout(this)
        fun coloca(b: Button, x: Int, y: Int) {
            cont.addView(
                b,
                FrameLayout.LayoutParams(dp(lado), dp(lado)).apply {
                    leftMargin = dp(x); topMargin = dp(y)
                },
            )
        }
        coloca(botonTecla("▲", LwjglGlfwKeycode.KEY_W, lado), lado, 0)
        coloca(botonTecla("◀", LwjglGlfwKeycode.KEY_A, lado), 0, lado)
        coloca(botonTecla("▶", LwjglGlfwKeycode.KEY_D, lado), lado * 2, lado)
        coloca(botonTecla("▼", LwjglGlfwKeycode.KEY_S, lado), lado, lado * 2)
        return cont
    }

    private fun crearBotonesAccion(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        // Agacharse: conmutador — mantenerlo mientras caminas es incómodo.
        addView(
            Button(this@JuegoActivity).apply {
                text = "⇩"
                alpha = 0.6f
                minWidth = dp(56); minHeight = dp(56)
                setOnClickListener {
                    agachado = !agachado
                    alpha = if (agachado) 1f else 0.6f
                    puente?.pushEventKey(LwjglGlfwKeycode.KEY_LEFT_SHIFT.toInt(), 0, agachado)
                }
            },
        )
        addView(botonTecla("⤒", LwjglGlfwKeycode.KEY_SPACE, 64)) // saltar
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

                override fun onLog(log: String?) = Unit // queda en latest_game.log

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

    // ── Toque: cámara, romper (mantener) y usar (toque corto) ────────────────

    private var ultimoX = 0f
    private var ultimoY = 0f
    private var inicioToque = 0L
    private var seMovio = false
    private var toqueDerecha = false
    private var rompiendo = false
    private val iniciarRomper = Runnable {
        if (cursorAgarrado) {
            rompiendo = true
            puente?.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), true)
        }
    }

    private fun manejarToque(evento: MotionEvent) {
        val p = puente ?: return
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                inicioToque = System.currentTimeMillis()
                seMovio = false
                rompiendo = false
                ultimoX = evento.x
                ultimoY = evento.y
                toqueDerecha = evento.x > (window.decorView.width / 2f)
                if (!cursorAgarrado) {
                    cursorX = evento.x
                    cursorY = evento.y
                    p.pushEventPointer(cursorX, cursorY)
                } else if (toqueDerecha) {
                    handler.postDelayed(iniciarRomper, UMBRAL_ROMPER_MS)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = evento.x - ultimoX
                val dy = evento.y - ultimoY
                if (dx * dx + dy * dy > UMBRAL_MOVIMIENTO_PX2) seMovio = true
                ultimoX = evento.x
                ultimoY = evento.y
                if (cursorAgarrado) {
                    cursorX += dx
                    cursorY += dy
                } else {
                    cursorX = evento.x
                    cursorY = evento.y
                }
                p.pushEventPointer(cursorX, cursorY)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(iniciarRomper)
                val duracion = System.currentTimeMillis() - inicioToque
                if (rompiendo) {
                    p.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), false)
                    rompiendo = false
                } else if (!seMovio && duracion < UMBRAL_ROMPER_MS + 100) {
                    val boton = when {
                        !cursorAgarrado -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT
                        toqueDerecha -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT
                        else -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT
                    }
                    p.pushEventMouseButton(boton.toInt(), true)
                    p.pushEventMouseButton(boton.toInt(), false)
                }
            }
        }
    }

    // ── Teclado del sistema → juego ──────────────────────────────────────────

    private fun crearEntradaTexto(): EditText = EditText(this).apply {
        alpha = 0f
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setText(" ") // centinela para detectar borrados
        setSelection(1)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, inicio: Int, antes: Int, nuevos: Int) {
                val p = puente ?: return
                if (nuevos > antes) {
                    s?.subSequence(inicio + antes, inicio + nuevos)?.forEach { c ->
                        if (c == '\n') tecla(p, LwjglGlfwKeycode.KEY_ENTER.toInt())
                        else p.pushEventChar(c)
                    }
                } else if (antes > nuevos) {
                    repeat(antes - nuevos) { tecla(p, LwjglGlfwKeycode.KEY_BACKSPACE.toInt()) }
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.isEmpty()) {
                    entradaTexto.setText(" ")
                    entradaTexto.setSelection(1)
                }
            }
        })
        setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE || accion == EditorInfo.IME_ACTION_SEND) {
                puente?.let { tecla(it, LwjglGlfwKeycode.KEY_ENTER.toInt()) }
                true
            } else {
                false
            }
        }
    }

    private fun tecla(p: FCLBridge, codigo: Int) {
        p.pushEventKey(codigo, 0, true)
        p.pushEventKey(codigo, 0, false)
    }

    private fun abrirTeclado() {
        entradaTexto.requestFocus()
        entradaTexto.setSelection(entradaTexto.text.length)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(entradaTexto, InputMethodManager.SHOW_IMPLICIT)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        puente?.let { tecla(it, LwjglGlfwKeycode.KEY_ESCAPE.toInt()) }
    }
}
