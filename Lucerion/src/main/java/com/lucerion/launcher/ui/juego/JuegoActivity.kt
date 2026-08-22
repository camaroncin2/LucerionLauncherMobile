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

    // Raiz y HUD reconstruible: el menu del juego permite editar los controles
    // en plena partida, asi que hay que poder rehacerlos sin reiniciar nada.
    private lateinit var raiz: FrameLayout
    private var diseno = com.lucerion.launcher.data.RepositorioDiseno.porDefecto()
    private val controlesEnPantalla = mutableListOf<Pair<com.lucerion.launcher.data.ControlHud, View>>()
    private val velosEdicion = mutableMapOf<String, View>()
    private var modoEdicion = false
    private var panelMenu: View? = null
    private var barraEdicion: LinearLayout? = null
    private var tituloEdicion: android.widget.TextView? = null
    private var sliderEdicion: android.widget.SeekBar? = null
    private var sliderOpacidadEdicion: android.widget.SeekBar? = null
    private var botonBorrarEdicion: android.widget.Button? = null
    private var seleccionEdicion: com.lucerion.launcher.data.ControlHud? = null
    private var botonMenu: View? = null

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

        raiz = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

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

        diseno = com.lucerion.launcher.data.RepositorioDiseno.cargar(this)
        construirHud()
        botonMenu = crearBotonMenu()
        raiz.addView(botonMenu, FrameLayout.LayoutParams(dp(44), dp(44)))

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

    // ── HUD reconstruible ────────────────────────────────────────────────────

    private fun anchoPantalla() = maxOf(
        resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels,
    )

    private fun altoPantalla() = minOf(
        resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels,
    )

    /**
     * Crea los controles a partir del diseno guardado. Se puede llamar tantas
     * veces como haga falta (al salir del modo edicion, al cambiar de palanca
     * a cruceta) sin tocar la partida en curso.
     */
    private fun construirHud() {
        controlesEnPantalla.forEach { (_, v) -> raiz.removeView(v) }
        controlesEnPantalla.clear()

        val usarCruceta = com.lucerion.launcher.data.RepositorioAjustes.usarCruceta(this)
        val anchoP = anchoPantalla()
        val altoP = altoPantalla()

        fun coloca(v: View, c: com.lucerion.launcher.data.ControlHud) {
            val tamPx = dpc(c.tam)
            v.alpha = c.opacidad.coerceIn(0.15f, 1f)
            val lp = FrameLayout.LayoutParams(tamPx, tamPx)
            lp.leftMargin = (c.x * anchoP - tamPx / 2f).toInt().coerceIn(0, maxOf(0, anchoP - tamPx))
            lp.topMargin = (c.y * altoP - tamPx / 2f).toInt().coerceIn(0, maxOf(0, altoP - tamPx))
            raiz.addView(v, lp)
            controlesEnPantalla += c to v
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
        // Los controles recien creados quedarian sobre el engranaje y lo
        // taparian: el menu siempre manda en la capa de arriba.
        botonMenu?.bringToFront()
    }

    // ── Engranaje flotante y menu del juego ──────────────────────────────────

    /**
     * Boton flotante del menu: se arrastra a donde no moleste (su sitio queda
     * guardado) y al tocarlo despliega el menu del juego. Distingue toque de
     * arrastre por distancia, asi moverlo nunca abre el menu sin querer.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun crearBotonMenu(): View {
        val prefs = getSharedPreferences("lucerion", MODE_PRIVATE)
        val boton = BotonTactil(this, BotonTactil.Glifo.ENGRANAJE, alPresionar = {})
        boton.alpha = 0.75f

        var dX = 0f
        var dY = 0f
        var arrastro = false
        boton.post {
            boton.x = prefs.getFloat("menu_x", (anchoPantalla() - dp(56)).toFloat())
            boton.y = prefs.getFloat("menu_y", (altoPantalla() / 2f) - dp(22))
        }
        boton.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = e.rawX - v.x
                    dY = e.rawY - v.y
                    arrastro = false
                    v.alpha = 1f
                }
                MotionEvent.ACTION_MOVE -> {
                    val nx = e.rawX - dX
                    val ny = e.rawY - dY
                    if (Math.abs(nx - v.x) > dp(3) || Math.abs(ny - v.y) > dp(3)) arrastro = true
                    v.x = nx.coerceIn(0f, maxOf(0f, (raiz.width - v.width).toFloat()))
                    v.y = ny.coerceIn(0f, maxOf(0f, (raiz.height - v.height).toFloat()))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.75f
                    prefs.edit().putFloat("menu_x", v.x).putFloat("menu_y", v.y).apply()
                    if (!arrastro) alternarMenu()
                }
            }
            true
        }
        return boton
    }

    private fun alternarMenu() {
        if (panelMenu != null) cerrarMenu() else abrirMenu()
    }

    private fun cerrarMenu() {
        panelMenu?.let { raiz.removeView(it) }
        panelMenu = null
    }

    /** Panel semitransparente con las acciones disponibles en partida. */
    private fun abrirMenu() {
        if (modoEdicion) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE60C1424.toInt()) // navy translucido
            setPadding(dp(18), dp(14), dp(18), dp(16))
        }
        panel.addView(
            android.widget.TextView(this).apply {
                text = "Menu de Lucerion"
                setTextColor(0xFFE8C06A.toInt())
                textSize = 16f
            },
        )
        panel.addView(
            android.widget.TextView(this).apply {
                text = "La partida sigue corriendo detras."
                setTextColor(0xB3C8D0E0.toInt())
                textSize = 12f
                setPadding(0, 0, 0, dp(10))
            },
        )

        fun opcion(titulo: String, detalle: String, alPulsar: () -> Unit) {
            panel.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    setBackgroundColor(0x80111C31.toInt())
                    isClickable = true
                    setOnClickListener { alPulsar() }
                    addView(
                        android.widget.TextView(context).apply {
                            text = titulo
                            setTextColor(0xFFE8C06A.toInt())
                            textSize = 14f
                        },
                    )
                    addView(
                        android.widget.TextView(context).apply {
                            text = detalle
                            setTextColor(0xB3C8D0E0.toInt())
                            textSize = 11f
                        },
                    )
                },
                LinearLayout.LayoutParams(dp(250), LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dp(8) },
            )
        }

        opcion("Editar controles aqui mismo", "Mueve y redimensiona los botones sobre la partida.") {
            cerrarMenu()
            entrarModoEdicion()
        }
        val usandoCruceta = com.lucerion.launcher.data.RepositorioAjustes.usarCruceta(this)
        opcion(
            if (usandoCruceta) "Cambiar a palanca" else "Cambiar a cruceta",
            if (usandoCruceta) "Palanca: diagonales y correr con doble toque."
            else "Cruceta: cuatro flechas fijas, mas precision de boton.",
        ) {
            com.lucerion.launcher.data.RepositorioAjustes.guardarUsarCruceta(this, !usandoCruceta)
            cerrarMenu()
            construirHud()
        }
        opcion("Abrir el teclado", "Para escribir en el chat o en carteles.") {
            cerrarMenu()
            abrirTeclado()
        }
        opcion("Cerrar el menu", "Vuelve a la partida.") { cerrarMenu() }

        raiz.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        panelMenu = panel
    }

    // ── Edicion del HUD sobre la partida ─────────────────────────────────────

    /**
     * Modo edicion in situ: cada control recibe encima una capa que lo hace
     * arrastrable (y lo desconecta del juego mientras dure). Abajo aparece la
     * barra con el tamano del control elegido y guardar o descartar.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun entrarModoEdicion() {
        if (modoEdicion) return
        modoEdicion = true
        seleccionEdicion = null
        velosEdicion.clear()
        crearVelosEdicion()
        mostrarBarraEdicion()
    }

    /** Capa arrastrable sobre cada control (y lo desconecta del juego). */
    @SuppressLint("ClickableViewAccessibility")
    private fun crearVelosEdicion() {
        for ((c, vista) in controlesEnPantalla) {
            val lp = vista.layoutParams as FrameLayout.LayoutParams
            val velo = View(this).apply { setBackgroundColor(0x332E9BD6) }
            raiz.addView(
                velo,
                FrameLayout.LayoutParams(lp.width, lp.height).also {
                    it.leftMargin = lp.leftMargin
                    it.topMargin = lp.topMargin
                },
            )
            velo.x = vista.x
            velo.y = vista.y
            velosEdicion[c.id] = velo

            var dX = 0f
            var dY = 0f
            velo.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        seleccionarEnEdicion(c)
                        dX = e.rawX - v.x
                        dY = e.rawY - v.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val nx = (e.rawX - dX).coerceIn(0f, maxOf(0f, (raiz.width - v.width).toFloat()))
                        val ny = (e.rawY - dY).coerceIn(0f, maxOf(0f, (raiz.height - v.height).toFloat()))
                        v.x = nx
                        v.y = ny
                        vista.x = nx
                        vista.y = ny
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        c.x = (v.x + v.width / 2f) / raiz.width
                        c.y = (v.y + v.height / 2f) / raiz.height
                    }
                }
                true
            }
        }
    }

    private fun etiquetaDe(c: com.lucerion.launcher.data.ControlHud): String = when {
        c.tipo == "tecla" -> "Boton " + (c.etiqueta ?: "?")
        c.id == "movimiento" -> "Movimiento"
        else -> c.id.replaceFirstChar { it.uppercase() }
    }

    private fun seleccionarEnEdicion(c: com.lucerion.launcher.data.ControlHud) {
        seleccionEdicion = c
        actualizarTituloEdicion(c)
        sliderEdicion?.progress = c.tam - 36
        sliderOpacidadEdicion?.progress = ((c.opacidad * 100).toInt() - 15).coerceIn(0, 85)
        botonBorrarEdicion?.visibility = if (c.tipo == "tecla") View.VISIBLE else View.GONE
        for ((id, velo) in velosEdicion) {
            velo.setBackgroundColor(if (id == c.id) 0x662E9BD6 else 0x332E9BD6)
        }
    }

    private fun mostrarBarraEdicion() {
        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE60C1424.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        val titulo = android.widget.TextView(this).apply {
            text = "Toca un control y arrastralo"
            setTextColor(0xFFE8C06A.toInt())
            textSize = 13f
        }
        barra.addView(titulo)
        tituloEdicion = titulo

        // Tamano
        barra.addView(
            android.widget.TextView(this).apply {
                text = "Tamano"
                setTextColor(0xB3C8D0E0.toInt())
                textSize = 11f
            },
        )
        val slider = android.widget.SeekBar(this).apply {
            max = 184 // 36..220 dp
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, progreso: Int, delUsuario: Boolean) {
                    if (!delUsuario) return
                    val c = seleccionEdicion ?: return
                    c.tam = progreso + 36
                    actualizarTituloEdicion(c)
                    redimensionarEnEdicion(c)
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) = Unit
            })
        }
        barra.addView(slider, LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT))
        sliderEdicion = slider

        // Opacidad: lo justo para que el control se vea sin tapar el mundo.
        barra.addView(
            android.widget.TextView(this).apply {
                text = "Opacidad"
                setTextColor(0xB3C8D0E0.toInt())
                textSize = 11f
            },
        )
        val sliderOpacidad = android.widget.SeekBar(this).apply {
            max = 85 // 15..100 %
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, progreso: Int, delUsuario: Boolean) {
                    if (!delUsuario) return
                    val c = seleccionEdicion ?: return
                    c.opacidad = (progreso + 15) / 100f
                    actualizarTituloEdicion(c)
                    controlesEnPantalla.firstOrNull { it.first === c }?.second?.alpha = c.opacidad
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) = Unit
            })
        }
        barra.addView(sliderOpacidad, LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT))
        sliderOpacidadEdicion = sliderOpacidad

        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fila.addView(
            android.widget.Button(this).apply {
                text = "+ BOTON"
                textSize = 11f
                setOnClickListener { dialogoNuevoBotonEnJuego() }
            },
        )
        val borrar = android.widget.Button(this).apply {
            text = "BORRAR"
            textSize = 11f
            visibility = View.GONE
            setOnClickListener {
                val c = seleccionEdicion ?: return@setOnClickListener
                if (c.tipo != "tecla") return@setOnClickListener
                diseno.controles.remove(c)
                seleccionEdicion = null
                rehacerEdicion()
            }
        }
        fila.addView(borrar)
        botonBorrarEdicion = borrar
        fila.addView(
            android.widget.Button(this).apply {
                text = "GUARDAR"
                textSize = 11f
                setOnClickListener { salirModoEdicion(guardar = true) }
            },
        )
        fila.addView(
            android.widget.Button(this).apply {
                text = "DESCARTAR"
                textSize = 11f
                setOnClickListener { salirModoEdicion(guardar = false) }
            },
        )
        barra.addView(fila)

        raiz.addView(
            barra,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ),
        )
        barraEdicion = barra
    }

    private fun actualizarTituloEdicion(c: com.lucerion.launcher.data.ControlHud) {
        tituloEdicion?.text = etiquetaDe(c) + "  ·  " + c.tam + " dp  ·  " +
            (c.opacidad * 100).toInt() + " %"
    }

    /**
     * Alta de boton personalizado sin salir de la partida: primero la tecla
     * (dos pasos, porque un dialogo con lista Y campo de texto hace que
     * Android ignore uno de los dos), luego la etiqueta visible.
     */
    private fun dialogoNuevoBotonEnJuego() {
        val nombres = com.lucerion.launcher.data.CatalogoTeclas.disponibles.map { it.first }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Nuevo boton - que tecla pulsara?")
            .setItems(nombres) { _, cual -> dialogoEtiquetaEnJuego(cual) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun dialogoEtiquetaEnJuego(indice: Int) {
        val (nombre, codigo) = com.lucerion.launcher.data.CatalogoTeclas.disponibles[indice]
        val campo = EditText(this).apply {
            hint = "Etiqueta visible (p. ej. Mochila)"
            setText(nombre)
            setSelection(text.length)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Tecla " + nombre + " - etiqueta del boton")
            .setView(campo)
            .setPositiveButton("Anadir") { _, _ ->
                val nuevo = com.lucerion.launcher.data.ControlHud(
                    id = "tecla_" + System.nanoTime(),
                    tipo = "tecla",
                    x = 0.5f, y = 0.45f, tam = 56,
                    etiqueta = campo.text.toString().ifBlank { nombre },
                    tecla = codigo,
                )
                diseno.controles.add(nuevo)
                rehacerEdicion()
                seleccionarEnEdicion(nuevo)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Rehace HUD y capas de edicion tras anadir o borrar un control. */
    private fun rehacerEdicion() {
        velosEdicion.values.forEach { raiz.removeView(it) }
        velosEdicion.clear()
        construirHud()
        crearVelosEdicion()
        botonBorrarEdicion?.visibility =
            if (seleccionEdicion?.tipo == "tecla") View.VISIBLE else View.GONE
        barraEdicion?.bringToFront()
    }

    /** Redimensiona en vivo el control elegido (y su capa) manteniendo el centro. */
    private fun redimensionarEnEdicion(c: com.lucerion.launcher.data.ControlHud) {
        val vista = controlesEnPantalla.firstOrNull { it.first === c }?.second ?: return
        val velo = velosEdicion[c.id]
        val tamPx = dpc(c.tam)
        val cx = vista.x + vista.width / 2f
        val cy = vista.y + vista.height / 2f
        val nx = (cx - tamPx / 2f).coerceIn(0f, maxOf(0f, (raiz.width - tamPx).toFloat()))
        val ny = (cy - tamPx / 2f).coerceIn(0f, maxOf(0f, (raiz.height - tamPx).toFloat()))
        for (v in listOfNotNull(vista, velo)) {
            v.layoutParams = FrameLayout.LayoutParams(tamPx, tamPx)
            v.post {
                v.x = nx
                v.y = ny
            }
        }
        c.x = (nx + tamPx / 2f) / raiz.width
        c.y = (ny + tamPx / 2f) / raiz.height
    }

    private fun salirModoEdicion(guardar: Boolean) {
        modoEdicion = false
        velosEdicion.values.forEach { raiz.removeView(it) }
        velosEdicion.clear()
        barraEdicion?.let { raiz.removeView(it) }
        barraEdicion = null
        tituloEdicion = null
        sliderEdicion = null
        sliderOpacidadEdicion = null
        botonBorrarEdicion = null
        seleccionEdicion = null

        if (guardar) {
            com.lucerion.launcher.data.RepositorioDiseno.guardar(this, diseno)
        } else {
            diseno = com.lucerion.launcher.data.RepositorioDiseno.cargar(this)
        }
        construirHud()
    }


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
