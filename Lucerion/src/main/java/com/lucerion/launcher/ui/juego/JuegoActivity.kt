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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.tungsten.fclauncher.bridge.FCLBridge
import com.tungsten.fclauncher.bridge.FCLBridgeCallback
import com.tungsten.fclauncher.keycodes.FCLKeycodes
import com.tungsten.fclauncher.keycodes.LwjglGlfwKeycode

/**
 * Superficie del juego + controles táctiles.
 *
 * Distribución y lógica de Bedrock, estética de Lucerion (placas de latón).
 * El HUD entero se construye desde el diseño editable del jugador
 * (Ajustes → Controles → editor), así que aquí no hay posiciones fijas:
 * cada control llega con su sitio, su tamaño y, si es personalizado, su tecla.
 *
 *  · Movimiento: palanca (predeterminada, con diagonales y sprint por
 *    doble-toque adelante) o cruceta, según ajuste.
 *  · GOLPEAR: mantener = clic izquierdo sostenido; arrastrando sin soltar se
 *    sigue moviendo la cámara con ese mismo dedo.
 *  · La pantalla NUNCA golpea: arrastrar = mirar; toque corto = usar/colocar;
 *    mantener = clic derecho sostenido (comer, beber, arco, escudo); toque
 *    sobre la hotbar = elegir slot; mantener sobre la hotbar = soltar el ítem.
 *  · En menús el dedo es el ratón; mantener = clic derecho (dividir stack).
 *  · Toda pulsación programática separa press y release en el tiempo: la cola
 *    de entrada del juego descarta las que llegan en el mismo instante.
 *  · OJO: pushEventKey espera códigos FCLKeycodes, no GLFW (los traduce
 *    LwjglKeycodeMap; un código GLFW se descarta en silencio).
 */
class JuegoActivity : Activity(), TextureView.SurfaceTextureListener {

    companion object {
        var puente: FCLBridge? = null
        var dirJuego: java.io.File? = null

        /**
         * La JVM solo se ejecuta UNA vez por partida: si la superficie
         * reaparece (volver de minimizar, Activity recreada), se re-adjunta
         * la ventana en vez de relanzar — relanzar sobre el mismo puente
         * tumba el proceso.
         */
        private var enEjecucion = false

        private const val UMBRAL_ROMPER_MS = 250L
        private const val UMBRAL_MOVIMIENTO_PX2 = 100f
        private const val TAM_BOTON_MENU = 46
    }

    private var cursorAgarrado = false
    private var cursorX = 0f
    private var cursorY = 0f
    private lateinit var entradaTexto: EditText
    private lateinit var textura: TextureView
    private var tecladoVisible = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // ── Ciclo de vida y layout ───────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        escalaControles = com.lucerion.launcher.data.RepositorioAjustes.escalaControles(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Pantalla completa REAL, incluida la zona de la cámara: el buffer del
        // juego debe medir lo mismo que el panel entero.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Pantalla completa con la API moderna de insets: las barras del
        // sistema (notificaciones, gesto atras) aparecen como CAPA transitoria
        // sin redimensionar la ventana. Con la API vieja cada aparicion
        // relayouteaba la superficie y eso pintaba las franjas negras al
        // desplegar notificaciones, retroceder o minimizar.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val raiz = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        textura = TextureView(this).apply {
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
        // Barra de texto visible: se posiciona justo ENCIMA del teclado cuando
        // este se abre, para ver lo que escribes sin achicar el juego.
        raiz.addView(
            entradaTexto,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(44), Gravity.TOP),
        )

        // HUD construido desde el diseno editable (Ajustes -> Controles ->
        // editor con previsualizacion). Posiciones en fraccion de pantalla.
        val diseno = com.lucerion.launcher.data.RepositorioDiseno.cargar(this)
        val metricas = resources.displayMetrics
        val anchoP = maxOf(metricas.widthPixels, metricas.heightPixels)
        val altoP = minOf(metricas.widthPixels, metricas.heightPixels)
        val usarCruceta = com.lucerion.launcher.data.RepositorioAjustes.usarCruceta(this)

        fun coloca(v: View, c: com.lucerion.launcher.data.ControlHud) {
            val tamPx = dpc(c.tam)
            val lp = FrameLayout.LayoutParams(tamPx, tamPx)
            lp.leftMargin = (c.x * anchoP - tamPx / 2f).toInt().coerceIn(0, maxOf(0, anchoP - tamPx))
            lp.topMargin = (c.y * altoP - tamPx / 2f).toInt().coerceIn(0, maxOf(0, altoP - tamPx))
            raiz.addView(v, lp)
        }

        for (c in diseno.controles) {
            when {
                c.tipo == "tecla" -> coloca(
                    // Boton personalizado: etiqueta visible + tecla configurada.
                    BotonTactil(
                        this, BotonTactil.Glifo.PAUSA, texto = c.etiqueta ?: "?",
                        alPresionar = { puente?.pushEventKey(c.tecla, 0, true) },
                        alSoltar = { puente?.pushEventKey(c.tecla, 0, false) },
                    ),
                    c,
                )

                c.id == "movimiento" -> {
                    if (usarCruceta) {
                        coloca(crearCruceta(c.tam), c)
                    } else {
                        coloca(
                            PalancaTactil(
                                this,
                                alCambiar = { adelante, atras, izquierda, derecha ->
                                    aplicarMovimiento(adelante, atras, izquierda, derecha)
                                },
                                alCorrer = { corriendo ->
                                    // Sprint de Bedrock: doble-toque adelante = CTRL mantenido.
                                    puente?.pushEventKey(FCLKeycodes.KEY_LEFTCTRL, 0, corriendo)
                                },
                            ),
                            c,
                        )
                    }
                }

                c.id == "salto" -> coloca(botonTecla(BotonTactil.Glifo.SALTO, FCLKeycodes.KEY_SPACE), c)

                c.id == "golpear" -> coloca(
                    // Mantener = clic izquierdo sostenido; arrastrar sin soltar
                    // sigue moviendo la camara (un pulgar rompe y mira).
                    BotonTactil(
                        this, BotonTactil.Glifo.GOLPEAR,
                        alPresionar = {
                            puente?.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), true)
                        },
                        alSoltar = {
                            puente?.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), false)
                        },
                        alArrastrar = { dx, dy ->
                            if (cursorAgarrado) {
                                cursorX += dx
                                cursorY += dy
                                puente?.pushEventPointer(cursorX, cursorY)
                            }
                        },
                    ),
                    c,
                )

                c.id == "agacharse" -> coloca(
                    BotonTactil(
                        this, BotonTactil.Glifo.AGACHARSE, conmutador = true,
                        alPresionar = { puente?.pushEventKey(FCLKeycodes.KEY_LEFTSHIFT, 0, true) },
                        alSoltar = { puente?.pushEventKey(FCLKeycodes.KEY_LEFTSHIFT, 0, false) },
                    ),
                    c,
                )

                c.id == "chat" -> coloca(BotonTactil(this, BotonTactil.Glifo.CHAT, alPresionar = { abrirChat() }), c)
                c.id == "pausa" -> coloca(botonTecla(BotonTactil.Glifo.PAUSA, FCLKeycodes.KEY_ESC), c)
                c.id == "inventario" -> coloca(botonTecla(BotonTactil.Glifo.INVENTARIO, FCLKeycodes.KEY_E), c)
                c.id == "teclado" -> coloca(BotonTactil(this, BotonTactil.Glifo.TECLADO, alPresionar = { abrirTeclado() }), c)
            }
        }

        setContentView(raiz)

        // Mantener viva la partida al minimizar: sin servicio en primer
        // plano, Android mata este proceso (la JVM vive aqui) en segundos.
        startForegroundService(android.content.Intent(this, com.lucerion.launcher.motor.ServicioJuego::class.java))

        // El juego queda a TAMANO COMPLETO siempre; cuando el teclado se abre,
        // la barra de entrada aparece pegada justo encima de el mostrando lo
        // que escribes (el teclado tapa la parte baja del juego, nada mas).
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val alturaPantalla = window.decorView.height
            if (alturaPantalla == 0) return@addOnGlobalLayoutListener
            val visible = android.graphics.Rect()
            window.decorView.getWindowVisibleDisplayFrame(visible)
            if (alturaPantalla * 2 / 3 > visible.bottom) {
                tecladoVisible = true
                entradaTexto.alpha = 1f
                entradaTexto.translationY = (visible.bottom - dp(44)).toFloat()
            } else if (tecladoVisible) {
                tecladoVisible = false
                entradaTexto.alpha = 0f
                entradaTexto.translationY = 6000f
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** dp escalado por el ajuste "Tamaño de los controles". */
    private var escalaControles = 1f
    private fun dpc(v: Int): Int = dp((v * escalaControles).toInt())

    // ── Botones estilo Bedrock: press al tocar, release al soltar ────────────

    // OJO: pushEventKey espera codigos FCLKeycodes (scancodes de Linux) que
    // LwjglKeycodeMap traduce a GLFW; un codigo GLFW directo cae en
    // "desconocido" y se descarta EN SILENCIO. Por eso ninguna tecla llegaba.
    private fun botonTecla(glifo: BotonTactil.Glifo, codigo: Int): BotonTactil =
        BotonTactil(
            this, glifo,
            alPresionar = { puente?.pushEventKey(codigo, 0, true) },
            alSoltar = { puente?.pushEventKey(codigo, 0, false) },
        )

    /** Estado deseado de W/A/S/D desde la palanca: solo se envían los cambios. */
    private val movimientoActivo = mutableMapOf(
        FCLKeycodes.KEY_W to false, FCLKeycodes.KEY_S to false,
        FCLKeycodes.KEY_A to false, FCLKeycodes.KEY_D to false,
    )

    private fun aplicarMovimiento(adelante: Boolean, atras: Boolean, izquierda: Boolean, derecha: Boolean) {
        val p = puente ?: return
        val deseado = mapOf(
            FCLKeycodes.KEY_W to adelante, FCLKeycodes.KEY_S to atras,
            FCLKeycodes.KEY_A to izquierda, FCLKeycodes.KEY_D to derecha,
        )
        for ((codigo, presionada) in deseado) {
            if (movimientoActivo[codigo] != presionada) {
                movimientoActivo[codigo] = presionada
                p.pushEventKey(codigo, 0, presionada)
            }
        }
    }

    /** Cruceta clásica de Bedrock: flechas en cruz con agacharse al centro. */
    private fun crearCruceta(tamTotal: Int): FrameLayout {
        val lado = (tamTotal - 4) / 3
        val paso = lado + 2
        val cont = FrameLayout(this)
        fun celda(v: View, col: Int, fila: Int) {
            cont.addView(
                v,
                FrameLayout.LayoutParams(dpc(lado), dpc(lado)).apply {
                    leftMargin = dpc(col * paso); topMargin = dpc(fila * paso)
                },
            )
        }
        celda(botonTecla(BotonTactil.Glifo.FLECHA_ARRIBA, FCLKeycodes.KEY_W), 1, 0)
        celda(botonTecla(BotonTactil.Glifo.FLECHA_IZQUIERDA, FCLKeycodes.KEY_A), 0, 1)
        celda(
            // Agacharse como conmutador: mantenerlo mientras caminas es incómodo.
            BotonTactil(
                this, BotonTactil.Glifo.AGACHARSE, conmutador = true,
                alPresionar = { puente?.pushEventKey(FCLKeycodes.KEY_LEFTSHIFT, 0, true) },
                alSoltar = { puente?.pushEventKey(FCLKeycodes.KEY_LEFTSHIFT, 0, false) },
            ),
            1, 1,
        )
        celda(botonTecla(BotonTactil.Glifo.FLECHA_DERECHA, FCLKeycodes.KEY_D), 2, 1)
        celda(botonTecla(BotonTactil.Glifo.FLECHA_ABAJO, FCLKeycodes.KEY_S), 1, 2)
        return cont
    }

    /**
     * Solo abre el chat del juego (T), sin desplegar el teclado: el teclado
     * sale unicamente del boton con su icono (arriba a la derecha).
     */
    private fun abrirChat() {
        val p = puente ?: return
        tecla(p, FCLKeycodes.KEY_T)
    }

    // ── Superficie ───────────────────────────────────────────────────────────

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, ancho: Int, alto: Int) {
        val p = puente ?: run { finish(); return }
        if (enEjecucion) {
            // El juego ya corre: solo re-adjuntar la ventana nueva.
            st.setDefaultBufferSize(ancho, alto)
            p.setSurfaceDestroyed(false)
            p.setSurfaceTexture(st)
            org.lwjgl.glfw.CallbackBridge.setupBridgeWindow(Surface(st))
            return
        }
        enEjecucion = true
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
                    // Diagnostico ANTES de soltar el estado: si el juego murio
                    // por un fallo, el menu debe poder explicarlo en espanol.
                    dirJuego?.let { dir ->
                        com.lucerion.launcher.motor.DiagnosticoJuego.analizar(dir, codigo)
                            ?.let { com.lucerion.launcher.motor.InstaladorJuego.ultimoDiagnostico = it }
                    }
                    runOnUiThread {
                        enEjecucion = false
                        puente = null
                        stopService(android.content.Intent(this@JuegoActivity, com.lucerion.launcher.motor.ServicioJuego::class.java))
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

    // ── Toque estilo Bedrock: la pantalla NUNCA golpea ──────────────────────
    //  · Arrastrar = mirar (camara). Golpear/romper SOLO con su boton.
    //  · Toque corto en juego = usar/colocar/interactuar (clic derecho).
    //  · Toque corto sobre la hotbar = elegir ese slot (teclas 1-9).
    //  · En menus el dedo es el raton (toque corto = clic izquierdo).

    private var ultimoX = 0f
    private var ultimoY = 0f
    private var inicioToque = 0L
    private var seMovio = false
    private var toqueLargoHecho = false

    // Toques largos estilo Bedrock:
    //  · En menus/inventario: mantener = clic DERECHO (dividir stack a la
    //    mitad, colocar de a uno) — como mantener en Bedrock.
    //  · En juego sobre la hotbar: mantener = soltar ese item (slot + Q).
    //  · En juego en el resto de la pantalla: mantener = clic derecho
    //    SOSTENIDO hasta soltar el dedo — comer, beber, arco, escudo.
    private var sosteniendoDerecho = false

    private val toqueLargo = Runnable {
        val p = puente ?: return@Runnable
        if (seMovio) return@Runnable
        if (!cursorAgarrado) {
            toqueLargoHecho = true
            clic(p, LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt())
        } else {
            val slot = slotHotbarEn(ultimoX, ultimoY)
            if (slot != null) {
                toqueLargoHecho = true
                tecla(p, slot)           // primero elegirlo…
                tecla(p, FCLKeycodes.KEY_Q) // …y soltarlo (tecla() encadena solo)
            } else {
                toqueLargoHecho = true
                sosteniendoDerecho = true
                p.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt(), true)
            }
        }
    }

    private fun manejarToque(evento: MotionEvent) {
        val p = puente ?: return
        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                inicioToque = System.currentTimeMillis()
                seMovio = false
                toqueLargoHecho = false
                sosteniendoDerecho = false
                ultimoX = evento.x
                ultimoY = evento.y
                if (!cursorAgarrado) {
                    cursorX = evento.x
                    cursorY = evento.y
                    p.pushEventPointer(cursorX, cursorY)
                }
                handler.postDelayed(toqueLargo, 420)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = evento.x - ultimoX
                val dy = evento.y - ultimoY
                if (dx * dx + dy * dy > UMBRAL_MOVIMIENTO_PX2) {
                    seMovio = true
                    handler.removeCallbacks(toqueLargo)
                }
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
                handler.removeCallbacks(toqueLargo)
                if (sosteniendoDerecho) {
                    // Fin de comer/beber/apuntar: soltar el clic derecho.
                    p.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt(), false)
                    sosteniendoDerecho = false
                }
                val duracion = System.currentTimeMillis() - inicioToque
                if (!toqueLargoHecho && !seMovio && duracion < 300) {
                    if (!cursorAgarrado) {
                        clic(p, LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt())
                    } else {
                        val slot = slotHotbarEn(evento.x, evento.y)
                        if (slot != null) tecla(p, slot)
                        else clic(p, LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt())
                    }
                }
            }
        }
    }

    /**
     * Si el toque cae sobre la hotbar del juego, devuelve la tecla 1-9 del
     * slot. Geometria de Minecraft: GUI a escala automatica (la mayor que
     * mantenga 320x240 visibles), hotbar de 182 unidades centrada abajo.
     */
    private fun slotHotbarEn(x: Float, y: Float): Int? {
        val w = textura.width
        val h = textura.height
        if (w == 0 || h == 0) return null
        val escala = maxOf(1, minOf(w / 320, h / 240))
        val altoHotbar = 22f * escala + dp(6) // margen tactil tolerante
        if (y < h - altoHotbar) return null
        val ancho = 182f * escala
        val inicio = (w - ancho) / 2f
        if (x < inicio || x > inicio + ancho) return null
        val indice = ((x - inicio) / (ancho / 9f)).toInt().coerceIn(0, 8)
        return intArrayOf(
            FCLKeycodes.KEY_1, FCLKeycodes.KEY_2, FCLKeycodes.KEY_3,
            FCLKeycodes.KEY_4, FCLKeycodes.KEY_5, FCLKeycodes.KEY_6,
            FCLKeycodes.KEY_7, FCLKeycodes.KEY_8, FCLKeycodes.KEY_9,
        )[indice]
    }

    // ── Teclado del sistema → juego ──────────────────────────────────────────

    private fun crearEntradaTexto(): EditText = EditText(this).apply {
        // Invisible hasta que el teclado se abre; entonces aparece como barra
        // pegada encima de el (el listener de layout la posiciona).
        alpha = 0f
        translationY = 6000f
        setBackgroundColor(0xF0141414.toInt())
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 16f
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setText(" ") // centinela para detectar borrados
        setSelection(1)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, inicio: Int, antes: Int, nuevos: Int) {
                if (reiniciandoTexto) return
                val p = puente ?: return
                if (nuevos > antes) {
                    s?.subSequence(inicio + antes, inicio + nuevos)?.forEach { c ->
                        if (c == '\n') enviarYLimpiar(p)
                        else p.pushEventChar(c)
                    }
                } else if (antes > nuevos) {
                    repeat(antes - nuevos) { tecla(p, FCLKeycodes.KEY_BACKSPACE) }
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (reiniciandoTexto) return
                if (s != null && s.isEmpty()) {
                    reiniciarBarraTexto()
                }
            }
        })
        setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_DONE || accion == EditorInfo.IME_ACTION_SEND) {
                puente?.let { enviarYLimpiar(it) }
                true
            } else {
                false
            }
        }
    }

    private var reiniciandoTexto = false

    /** ENTER al juego y barra limpia: el mensaje ya se fue, no debe quedar escrito. */
    private fun enviarYLimpiar(p: FCLBridge) {
        tecla(p, FCLKeycodes.KEY_ENTER)
        reiniciarBarraTexto()
    }

    private fun reiniciarBarraTexto() {
        reiniciandoTexto = true
        entradaTexto.setText(" ")
        entradaTexto.setSelection(1)
        reiniciandoTexto = false
    }

    // La cola de entrada del juego DESCARTA press+release mandados en el mismo
    // instante (asi murieron los primeros botones). Toda pulsacion programatica
    // se separa en el tiempo, y pulsaciones consecutivas se encadenan para no
    // pisarse entre si (p. ej. varios retrocesos seguidos del teclado).
    private var proximoDisparo = 0L

    private fun tecla(p: FCLBridge, codigo: Int) {
        val base = maxOf(android.os.SystemClock.uptimeMillis(), proximoDisparo)
        proximoDisparo = base + 90
        handler.postAtTime({ p.pushEventKey(codigo, 0, true) }, base)
        handler.postAtTime({ p.pushEventKey(codigo, 0, false) }, base + 55)
    }

    private fun clic(p: FCLBridge, boton: Int) {
        p.pushEventMouseButton(boton, true)
        handler.postDelayed({ p.pushEventMouseButton(boton, false) }, 60)
    }

    private fun abrirTeclado() {
        entradaTexto.requestFocus()
        entradaTexto.setSelection(entradaTexto.text.length)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(entradaTexto, InputMethodManager.SHOW_IMPLICIT)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        puente?.let { tecla(it, FCLKeycodes.KEY_ESC) }
    }

    // ── Foco y visibilidad de la ventana GLFW (paridad con FCL) ─────────────
    // Sin esto el juego cree seguir enfocado al minimizar y algunos mods
    // (pausa automática, sonido) se comportan raro.

    override fun onResume() {
        super.onResume()
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 1)
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1)
    }

    override fun onPause() {
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 0)
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0)
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 1)
    }

    override fun onStop() {
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 0)
        super.onStop()
    }
}
