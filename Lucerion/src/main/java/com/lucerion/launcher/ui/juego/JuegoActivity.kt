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
import kotlinx.coroutines.launch

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

        /** ¿Hay una partida viva? Arrancar una segunda JVM deja la primera
         *  sin controles y las dos peleando por la memoria. */
        fun partidaEnCurso(): Boolean = enEjecucion && puente != null

        private const val UMBRAL_MOVIMIENTO_PX2 = 100f
        private const val TAM_BOTON_MENU = 46
        private const val VELO_NORMAL = 0x332E9BD6
        private const val VELO_ELEGIDO = 0x662E9BD6
        private const val CLAVE_AISLANTE = "__aislante__"

        /** Cuanto hay que mantener el dedo quieto para que salga el panel. */
        private const val ESPERA_MANTENER = 300L
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
    private var accionesEdicion: LinearLayout? = null
    private var panelEdicion: View? = null
    private var tituloEdicion: android.widget.TextView? = null
    private var sliderEdicion: android.widget.SeekBar? = null
    private var sliderOpacidadEdicion: android.widget.SeekBar? = null
    private var botonBorrarEdicion: View? = null

    /** Distancia a partir de la cual un toque cuenta como arrastre. */
    private val umbralArrastre by lazy {
        android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()
    }

    /**
     * Tamano con el que el juego dibuja cada fotograma. Se fija UNA vez y ya
     * no se toca.
     *
     * Antes el buffer seguia al tamano de la vista: cualquier reajuste
     * transitorio de la ventana (desplegar las notificaciones, una capa del
     * sistema encima, volver de segundo plano) cambiaba la medida, se le
     * pedia al juego que rehiciera su fotograma y, hasta que terminaba, el
     * trozo de superficie sin cubrir se veia negro. Eso eran las franjas
     * —verticales u horizontales segun por donde no llegara el fotograma—.
     * Con el tamano fijo no hay hueco posible: TextureView escala.
     */
    private var anchoJuego = 0
    private var altoJuego = 0
    private var seleccionEdicion: com.lucerion.launcher.data.ControlHud? = null
    private var botonMenu: View? = null
    private var observadorTeclado: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

    // La ventana del juego arranca su PROPIA partida: la superficie y el
    // puente llegan por caminos distintos (una por el sistema, el otro por el
    // motor) y arrancamos cuando estan los dos.
    private var superficiePendiente: SurfaceTexture? = null
    private var anchoSuperficie = 0
    private var altoSuperficie = 0
    private var arrancando = false
    private var avisoArranque: android.widget.TextView? = null

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
        // Mismo tamaño relativo que el resto de controles (respeta la escala).
        raiz.addView(botonMenu, FrameLayout.LayoutParams(dpc(TAM_BOTON_MENU), dpc(TAM_BOTON_MENU)))

        setContentView(raiz)

        // Mantener viva la partida al minimizar: sin servicio en primer
        // plano, Android mata este proceso (la JVM vive aqui) en segundos.
        startForegroundService(android.content.Intent(this, com.lucerion.launcher.motor.ServicioJuego::class.java))

        prepararPartida(intent)

        // El juego queda a TAMANO COMPLETO siempre; cuando el teclado se abre,
        // la barra de entrada aparece pegada justo encima de el mostrando lo
        // que escribes (el teclado tapa la parte baja del juego, nada mas).
        observadorTeclado = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val alturaPantalla = window.decorView.height
            if (alturaPantalla == 0) return@OnGlobalLayoutListener
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
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(observadorTeclado)
    }

    /**
     * Volver a la ventana desde recientes trae un intent nuevo. Si ya hay
     * partida, NO se toca: relanzar sobre una JVM viva la tumba.
     */
    override fun onNewIntent(nuevo: android.content.Intent?) {
        super.onNewIntent(nuevo)
        nuevo?.let { intent = it }
        prepararPartida(nuevo)
    }

    /**
     * Arranca la partida en ESTE proceso a partir de los datos que trae el
     * intent. Antes el launcher preparaba el puente y se lo pasaba por una
     * variable compartida; con procesos separados eso ya no existe, y ademas
     * la partida deja de morir cuando el sistema recorta al launcher.
     */
    private fun prepararPartida(datos: android.content.Intent?) {
        if (puente != null || arrancando) return
        val ruta = datos?.getStringExtra("instancia") ?: run {
            mostrarAvisoArranque("No se recibieron los datos de la partida.")
            return
        }
        val apodo = datos.getStringExtra("apodo") ?: "Jugador"
        arrancando = true
        mostrarAvisoArranque("Preparando Cretania…")
        com.lucerion.launcher.motor.Lanzador.ambitoArranque.launch {
            try {
                com.lucerion.launcher.motor.Lanzador.lanzar(
                    this@JuegoActivity, java.io.File(ruta), apodo,
                )
                arrancando = false
                mostrarAvisoArranque(null)
                intentarArrancar()
            } catch (e: Throwable) {
                // Throwable y no Exception: los fallos de carga de bibliotecas
                // nativas (UnsatisfiedLinkError, ExceptionInInitializerError)
                // son Error, y sin capturarlos la ventana moria en duro sin
                // dejar ni un mensaje en pantalla.
                arrancando = false
                android.util.Log.e("LucerionJuego", "No se pudo preparar la partida", e)
                com.lucerion.launcher.motor.RegistroFallos.anotar(this@JuegoActivity, e)
                val raiz = generateSequence<Throwable>(e) { it.cause }.last()
                mostrarAvisoArranque(
                    "No se pudo entrar: " + (raiz.message ?: raiz.javaClass.simpleName),
                )
            }
        }
    }

    /** Mensaje sobre la superficie negra mientras arranca (o si falla). */
    private fun mostrarAvisoArranque(texto: String?) {
        if (texto == null) {
            avisoArranque?.let { raiz.removeView(it) }
            avisoArranque = null
            return
        }
        val vista = avisoArranque ?: android.widget.TextView(this).apply {
            setTextColor(0xFFE8C06A.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            raiz.addView(
                this,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            avisoArranque = this
        }
        vista.text = texto
        vista.bringToFront()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ── HUD reconstruible ────────────────────────────────────────────────────

    /**
     * Medidas de la VENTANA real, no de la pantalla fisica.
     *
     * Antes se leia displayMetrics con maxOf/minOf: al guardar se dividia por
     * el tamano de la raiz y al leer por el de la pantalla, asi que cada
     * ciclo de edicion corria un poco los controles. Y en una ventana mas
     * alta que ancha (pantalla dividida) los ejes se intercambiaban y el HUD
     * entero aterrizaba sobre el eje equivocado.
     */
    private fun anchoPantalla() =
        if (raiz.width > 0) raiz.width else resources.displayMetrics.widthPixels

    private fun altoPantalla() =
        if (raiz.height > 0) raiz.height else resources.displayMetrics.heightPixels

    /**
     * Crea los controles a partir del diseno guardado. Se puede llamar tantas
     * veces como haga falta (al salir del modo edicion, al cambiar de palanca
     * a cruceta) sin tocar la partida en curso.
     */
    private fun construirHud() {
        // La contabilidad de W/A/S/D pertenece a los controles que se van:
        // heredarla dejaba al personaje andando hacia un lado sin que el
        // control nuevo pudiera volver a enviar el press.
        val p = puente
        for ((codigo, pulsada) in movimientoActivo) {
            if (pulsada) p?.pushEventKey(codigo, 0, false)
        }
        movimientoActivo.keys.forEach { movimientoActivo[it] = false }

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
            // La cruceta ya lleva agacharse en el centro: colocar tambien el
            // suelto dejaba DOS conmutadores para la misma tecla, cada uno con
            // su estado, y el sneak acababa desincronizado.
            if (usarCruceta && c.id == "agacharse") continue
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

                else -> {
                    // Id que esta version no conoce (diseño de otra versión):
                    // se descarta en vez de quedar ocupando sitio invisible en
                    // el diseño para siempre.
                    android.util.Log.w("LucerionJuego", "Control desconocido descartado: ${c.id}")
                }
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
        var inicioX = 0f
        var inicioY = 0f
        var arrastro = false
        boton.post {
            // Esquina superior izquierda: el sitio de fábrica anterior (borde
            // derecho, a media altura) caía ENCIMA del botón de agacharse y se
            // comía dos tercios de su superficie en toda instalación nueva.
            val tam = dpc(TAM_BOTON_MENU)
            val maxX = maxOf(0f, (raiz.width - tam).toFloat())
            val maxY = maxOf(0f, (raiz.height - tam).toFloat())
            // Acotado SIEMPRE: sin esto, un sitio guardado con la ventana más
            // ancha dejaba el engranaje fuera de pantalla — y sin engranaje no
            // hay forma de abrir el menú nunca más.
            boton.x = prefs.getFloat("menu_x", dp(12).toFloat()).coerceIn(0f, maxX)
            boton.y = prefs.getFloat("menu_y", dp(12).toFloat()).coerceIn(0f, maxY)
        }
        boton.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = e.rawX - v.x
                    dY = e.rawY - v.y
                    inicioX = e.rawX
                    inicioY = e.rawY
                    arrastro = false
                    v.alpha = 1f
                }
                MotionEvent.ACTION_MOVE -> {
                    val nx = e.rawX - dX
                    val ny = e.rawY - dY
                    // Distancia ACUMULADA desde que se apoyó el dedo: comparar
                    // contra la posición actual medía el paso entre fotogramas,
                    // así que un arrastre lento terminaba abriendo el menú.
                    if (Math.abs(e.rawX - inicioX) > dp(6) ||
                        Math.abs(e.rawY - inicioY) > dp(6)
                    ) {
                        arrastro = true
                    }
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
        // En modo edicion el engranaje era un boton muerto y sin explicacion:
        // ahora sirve de salida (descartando, como el boton DESCARTAR).
        when {
            modoEdicion -> salirModoEdicion(guardar = false)
            panelMenu != null -> cerrarMenu()
            else -> abrirMenu()
        }
    }

    private fun cerrarMenu() {
        val capa = panelMenu ?: return
        panelMenu = null
        // Se va como vino: si desaparece de golpe parece un parpadeo.
        capa.animate().alpha(0f).setDuration(110)
            .withEndAction { raiz.removeView(capa) }
            .start()
    }

    /** Fondo redondeado reutilizable para los paneles sobre la partida. */
    private fun fondoRedondeado(
        color: Int,
        radio: Int,
        borde: Int = 0,
    ): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radio).toFloat()
            if (borde != 0) setStroke(maxOf(1, dp(1)), borde)
        }

    /**
     * Menu de partida: tarjeta compacta y translucida en el centro.
     *
     * Antes era una lista alta de tarjetas casi opacas metida en un
     * ScrollView, que tapaba media pantalla y aparecia de golpe. Ahora entra
     * con un fundido corto, deja ver la partida por detras y se cierra
     * tocando fuera —que es lo que uno intenta primero—.
     */
    private fun abrirMenu() {
        if (modoEdicion) return

        // Velo a pantalla completa: ademas de cerrar al tocar fuera, impide
        // que los toques del menu lleguen al juego y coloquen bloques.
        val capa = FrameLayout(this).apply {
            setBackgroundColor(0x4D000000)
            isClickable = true
            isFocusable = true
            setOnClickListener { cerrarMenu() }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = fondoRedondeado(0xBF0B1220.toInt(), 18, 0x4DE8C06A)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            isClickable = true // los toques del panel no cierran el menu
        }
        panel.addView(
            android.widget.TextView(this).apply {
                text = "Menú de Lucerion"
                setTextColor(0xFFE8C06A.toInt())
                textSize = 14f
                setPadding(0, 0, 0, dp(9))
            },
        )

        fun opcion(titulo: String, detalle: String, alPulsar: () -> Unit) {
            panel.addView(
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = fondoRedondeado(0x59172A44, 12)
                    setPadding(dp(11), dp(8), dp(11), dp(8))
                    minimumHeight = dp(48) // objetivo tactil accesible
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    setOnClickListener { alPulsar() }
                    addView(
                        android.widget.TextView(context).apply {
                            text = titulo
                            setTextColor(0xFFE8C06A.toInt())
                            textSize = 13f
                        },
                    )
                    addView(
                        android.widget.TextView(context).apply {
                            text = detalle
                            setTextColor(0x99C8D0E0.toInt())
                            textSize = 10f
                        },
                    )
                },
                LinearLayout.LayoutParams(dp(258), LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dp(6) },
            )
        }

        opcion("Editar controles aquí mismo", "Mantén pulsado un botón para ajustarlo.") {
            cerrarMenu()
            entrarModoEdicion()
        }
        val usandoCruceta = com.lucerion.launcher.data.RepositorioAjustes.usarCruceta(this)
        opcion(
            if (usandoCruceta) "Cambiar a palanca" else "Cambiar a cruceta",
            if (usandoCruceta) "Palanca: diagonales y correr con doble toque."
            else "Cruceta: cuatro flechas fijas con agacharse en el centro.",
        ) {
            com.lucerion.launcher.data.RepositorioAjustes.guardarUsarCruceta(this, !usandoCruceta)
            cerrarMenu()
            construirHud()
        }
        opcion("Abrir el teclado", "Para escribir en el chat o en carteles.") {
            cerrarMenu()
            abrirTeclado()
        }

        // Cierre en una sola linea: con el velo detras ya se puede cerrar
        // tocando fuera, asi que aqui sobra la explicacion.
        panel.addView(
            android.widget.TextView(this).apply {
                text = "Volver a la partida"
                setTextColor(0x99C8D0E0.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
                minimumHeight = dp(44)
                setPadding(0, dp(12), 0, 0)
                isClickable = true
                setOnClickListener { cerrarMenu() }
            },
            LinearLayout.LayoutParams(dp(258), LinearLayout.LayoutParams.WRAP_CONTENT),
        )

        capa.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        raiz.addView(
            capa,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        panelMenu = capa

        // Entrada breve: aparece creciendo un poco, no de golpe.
        capa.alpha = 0f
        panel.scaleX = 0.94f
        panel.scaleY = 0.94f
        capa.animate().alpha(1f).setDuration(140).start()
        panel.animate().scaleX(1f).scaleY(1f).setDuration(160)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
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
        mostrarAccionesEdicion()
    }

    /** Capa arrastrable sobre cada control (y lo desconecta del juego). */
    @SuppressLint("ClickableViewAccessibility")
    private fun crearVelosEdicion() {
        // Capa a pantalla completa por DEBAJO de los velos: mientras editas, el
        // juego no debe recibir nada. Sin ella, tocar el fondo cambiaba de slot
        // y mantener pulsado tiraba el ítem al suelo.
        val aislante = View(this).apply {
            setBackgroundColor(0x14000000)
            isClickable = true
            isFocusable = true
            // Tocar el fondo cierra el panel de ajuste: asi el centro queda
            // libre para colocar controles ahi mismo.
            setOnClickListener { ocultarPanelEdicion() }
        }
        raiz.addView(
            aislante,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        velosEdicion[CLAVE_AISLANTE] = aislante

        for ((c, vista) in controlesEnPantalla) {
            val lp = vista.layoutParams as FrameLayout.LayoutParams
            val velo = View(this).apply { setBackgroundColor(VELO_NORMAL) }
            // MISMOS margenes y MISMA traslacion que el control: mezclar
            // margen con setX duplicaba el desplazamiento y las capas
            // aparecian corridas respecto a los botones.
            raiz.addView(
                velo,
                FrameLayout.LayoutParams(lp.width, lp.height).also {
                    it.leftMargin = lp.leftMargin
                    it.topMargin = lp.topMargin
                },
            )
            velo.translationX = vista.translationX
            velo.translationY = vista.translationY
            velosEdicion[c.id] = velo

            var dX = 0f
            var dY = 0f
            var xInicio = 0f
            var yInicio = 0f
            var arrastrando = false
            // El panel de ajuste sale SOLO si mantienes el dedo quieto sobre
            // el control: ni al tocarlo ni al arrastrarlo. Asi deja de estorbar
            // justo donde estas colocando algo.
            val alMantener = Runnable { mostrarPanelEdicion(c) }
            velo.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        seleccionarEnEdicion(c)
                        dX = e.rawX - v.x
                        dY = e.rawY - v.y
                        xInicio = e.rawX
                        yInicio = e.rawY
                        arrastrando = false
                        v.postDelayed(alMantener, ESPERA_MANTENER)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!arrastrando &&
                            kotlin.math.hypot(e.rawX - xInicio, e.rawY - yInicio) > umbralArrastre
                        ) {
                            arrastrando = true
                            v.removeCallbacks(alMantener)
                            ocultarPanelEdicion()
                            accionesEdicion?.animate()?.alpha(0f)?.setDuration(90)?.start()
                            // Reagarrar aqui: si se conserva el offset del
                            // toque inicial, el control pega un salto del
                            // tamano del umbral al empezar a moverse.
                            dX = e.rawX - v.x
                            dY = e.rawY - v.y
                        }
                        if (arrastrando) {
                            val nx = (e.rawX - dX).coerceIn(0f, maxOf(0f, (raiz.width - v.width).toFloat()))
                            val ny = (e.rawY - dY).coerceIn(0f, maxOf(0f, (raiz.height - v.height).toFloat()))
                            v.x = nx
                            v.y = ny
                            vista.x = nx
                            vista.y = ny
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.removeCallbacks(alMantener)
                        if (arrastrando) {
                            c.x = (v.x + v.width / 2f) / raiz.width
                            c.y = (v.y + v.height / 2f) / raiz.height
                            accionesEdicion?.animate()?.alpha(1f)?.setDuration(120)?.start()
                        }
                    }
                }
                true
            }
        }
    }

    private fun etiquetaDe(c: com.lucerion.launcher.data.ControlHud): String = when {
        c.tipo == "tecla" -> "Botón " + (c.etiqueta ?: "?")
        c.id == "movimiento" -> "Movimiento"
        else -> c.id.replaceFirstChar { it.uppercase() }
    }

    private fun seleccionarEnEdicion(c: com.lucerion.launcher.data.ControlHud) {
        seleccionEdicion = c
        for ((id, velo) in velosEdicion) {
            velo.setBackgroundColor(if (id == c.id) VELO_ELEGIDO else VELO_NORMAL)
        }
        // Si el panel ya estaba abierto, pasa a mandar sobre el nuevo control
        // en vez de quedarse mostrando los datos del anterior.
        if (panelEdicion != null) mostrarPanelEdicion(c)
    }

    /**
     * Fila de acciones del modo edicion: minima y arriba del todo.
     *
     * Antes toda la edicion vivia en una barra fija abajo, con titulo, dos
     * deslizadores y cuatro botones. Ocupaba un cuarto de la pantalla y, peor,
     * hacia imposible dejar un control debajo de ella. Aqui solo queda lo que
     * de verdad tiene que estar siempre a mano.
     */
    private fun mostrarAccionesEdicion() {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = fondoRedondeado(0xB00B1220.toInt(), 14, 0x3DE8C06A)
            gravity = Gravity.CENTER_VERTICAL
        }
        fun chip(texto: String, alPulsar: () -> Unit) = android.widget.TextView(this).apply {
            text = texto
            textSize = 11f
            setTextColor(0xFFE8C06A.toInt())
            gravity = Gravity.CENTER
            minimumHeight = dp(44) // objetivo tactil accesible
            minWidth = dp(72)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { alPulsar() }
        }
        fila.addView(chip("+ BOTÓN") { dialogoNuevoBotonEnJuego() })
        fila.addView(chip("GUARDAR") { salirModoEdicion(guardar = true) })
        fila.addView(chip("DESCARTAR") { salirModoEdicion(guardar = false) })

        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        columna.addView(fila)

        // Pista de uso: el panel ya no esta a la vista, asi que hay que decir
        // como se abre. Se desvanece sola para no seguir estorbando.
        val pista = android.widget.TextView(this).apply {
            text = "Arrastra para mover · mantén pulsado para ajustar"
            textSize = 10f
            setTextColor(0x99C8D0E0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        columna.addView(pista)
        pista.animate().alpha(0f).setStartDelay(4200).setDuration(600).start()

        raiz.addView(
            columna,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = dp(6) },
        )
        accionesEdicion = columna
    }

    private fun ocultarPanelEdicion() {
        val panel = panelEdicion ?: return
        panelEdicion = null
        tituloEdicion = null
        sliderEdicion = null
        sliderOpacidadEdicion = null
        botonBorrarEdicion = null
        panel.animate().alpha(0f).setDuration(90)
            .withEndAction { raiz.removeView(panel) }
            .start()
    }

    /**
     * Ajuste fino del control elegido: aparece al MANTENER pulsado, va en el
     * centro y se cierra tocando fuera. Solo lo imprescindible —tamano,
     * opacidad y borrar— porque cada cosa de mas es pantalla que tapa.
     */
    private fun mostrarPanelEdicion(c: com.lucerion.launcher.data.ControlHud) {
        val previo = panelEdicion
        if (previo != null) {
            panelEdicion = null
            raiz.removeView(previo)
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = fondoRedondeado(0xBF0B1220.toInt(), 16, 0x4DE8C06A)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isClickable = true
        }

        val titulo = android.widget.TextView(this).apply {
            setTextColor(0xFFE8C06A.toInt())
            textSize = 12f
            setPadding(0, 0, 0, dp(4))
        }
        panel.addView(titulo)
        tituloEdicion = titulo

        fun filaSlider(
            etiqueta: String,
            maximo: Int,
            valor: Int,
            alCambiar: (Int) -> Unit,
        ): android.widget.SeekBar {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            fila.addView(
                android.widget.TextView(this).apply {
                    text = etiqueta
                    setTextColor(0x99C8D0E0.toInt())
                    textSize = 10f
                    width = dp(58)
                },
            )
            val sb = android.widget.SeekBar(this).apply {
                max = maximo
                progress = valor
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        s: android.widget.SeekBar?,
                        progreso: Int,
                        delUsuario: Boolean,
                    ) {
                        if (delUsuario) alCambiar(progreso)
                    }
                    override fun onStartTrackingTouch(s: android.widget.SeekBar?) = Unit
                    override fun onStopTrackingTouch(s: android.widget.SeekBar?) = Unit
                })
            }
            fila.addView(sb, LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT))
            panel.addView(fila)
            return sb
        }

        sliderEdicion = filaSlider("Tamaño", 184, c.tam - 36) { progreso ->
            c.tam = progreso + 36
            actualizarTituloEdicion(c)
            redimensionarEnEdicion(c)
        }
        sliderOpacidadEdicion = filaSlider(
            "Opacidad", 85, ((c.opacidad * 100).toInt() - 15).coerceIn(0, 85),
        ) { progreso ->
            c.opacidad = (progreso + 15) / 100f
            actualizarTituloEdicion(c)
            controlesEnPantalla.firstOrNull { it.first === c }?.second?.alpha = c.opacidad
        }

        val pie = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        fun chip(texto: String, color: Int, alPulsar: () -> Unit) =
            android.widget.TextView(this).apply {
                text = texto
                textSize = 11f
                setTextColor(color)
                gravity = Gravity.CENTER
                minimumHeight = dp(40)
                minWidth = dp(78)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                isClickable = true
                isFocusable = true
                setOnClickListener { alPulsar() }
            }
        // Borrar solo tiene sentido en los botones que anadio el jugador: los
        // controles de serie no se pueden quitar.
        if (c.tipo == "tecla") {
            val borrar = chip("BORRAR", 0xFFE08A7A.toInt()) {
                diseno.controles.remove(c)
                seleccionEdicion = null
                ocultarPanelEdicion()
                rehacerEdicion()
            }
            pie.addView(borrar)
            botonBorrarEdicion = borrar
        }
        pie.addView(chip("LISTO", 0xFFE8C06A.toInt()) { ocultarPanelEdicion() })
        panel.addView(pie)

        raiz.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        panelEdicion = panel
        actualizarTituloEdicion(c)

        panel.alpha = 0f
        panel.scaleX = 0.94f
        panel.scaleY = 0.94f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(130)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
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
            .setTitle("Nuevo botón — ¿qué tecla pulsará?")
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
            .setTitle("Tecla " + nombre + " — etiqueta del botón")
            .setView(campo)
            .setPositiveButton("Añadir") { _, _ ->
                // Desplazamiento por cada botón ya existente: dos botones
                // nuevos seguidos nacían exactamente uno encima del otro.
                val personalizados = diseno.controles.count { it.tipo == "tecla" }
                val nuevo = com.lucerion.launcher.data.ControlHud(
                    id = "tecla_" + System.nanoTime(),
                    tipo = "tecla",
                    x = (0.42f + personalizados * 0.06f).coerceAtMost(0.9f),
                    y = (0.40f + personalizados * 0.05f).coerceAtMost(0.85f),
                    tam = 56,
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
        // Los velos se recrean encima, asi que las capas de edicion tienen
        // que volver al frente o quedan enterradas y dejan de responder.
        accionesEdicion?.bringToFront()
        panelEdicion?.bringToFront()
    }

    /** Redimensiona en vivo el control elegido (y su capa) manteniendo el centro. */
    private fun redimensionarEnEdicion(c: com.lucerion.launcher.data.ControlHud) {
        val vista = controlesEnPantalla.firstOrNull { it.first === c }?.second ?: return
        val velo = velosEdicion[c.id]
        val tamPx = dpc(c.tam)
        // Centro actual en coordenadas de la raiz.
        val cx = vista.x + vista.width / 2f
        val cy = vista.y + vista.height / 2f
        val nx = (cx - tamPx / 2f).coerceIn(0f, maxOf(0f, (raiz.width - tamPx).toFloat()))
        val ny = (cy - tamPx / 2f).coerceIn(0f, maxOf(0f, (raiz.height - tamPx).toFloat()))

        for (v in listOfNotNull(vista, velo)) {
            val lp = v.layoutParams as FrameLayout.LayoutParams
            // Conservar los margenes: crear los LayoutParams de cero los ponia
            // a cero y el control saltaba a la esquina.
            v.layoutParams = FrameLayout.LayoutParams(tamPx, tamPx).also {
                it.leftMargin = lp.leftMargin
                it.topMargin = lp.topMargin
            }
            // Traslacion relativa al margen: asi la posicion vale desde ya,
            // sin esperar a que el sistema recalcule el layout.
            v.translationX = nx - lp.leftMargin
            v.translationY = ny - lp.topMargin
        }
        c.x = (nx + tamPx / 2f) / raiz.width
        c.y = (ny + tamPx / 2f) / raiz.height
    }

    private fun salirModoEdicion(guardar: Boolean) {
        modoEdicion = false
        velosEdicion.values.forEach { raiz.removeView(it) }
        velosEdicion.clear()
        accionesEdicion?.let { raiz.removeView(it) }
        accionesEdicion = null
        panelEdicion?.let { raiz.removeView(it) }
        panelEdicion = null
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
        // La superficie puede estar lista ANTES que el puente (el motor tarda
        // en resolver la version y la cuenta): se guarda y arranca quien
        // llegue ultimo.
        superficiePendiente = st
        anchoSuperficie = ancho
        altoSuperficie = alto
        intentarArrancar()
    }

    /** Arranca en cuanto estan la superficie Y el puente. */
    private fun intentarArrancar() {
        val st = superficiePendiente ?: return
        val p = puente ?: return
        arrancarJuego(st, p, anchoSuperficie, altoSuperficie)
    }

    private fun arrancarJuego(st: SurfaceTexture, p: FCLBridge, ancho: Int, alto: Int) {
        if (enEjecucion) {
            // El juego ya corre: solo re-adjuntar la ventana nueva, SIEMPRE al
            // tamano de render original (no al de la vista, que puede venir
            // alterado por una capa del sistema).
            st.setDefaultBufferSize(anchoJuego, altoJuego)
            p.setSurfaceDestroyed(false)
            p.setSurfaceTexture(st)
            org.lwjgl.glfw.CallbackBridge.setupBridgeWindow(Surface(st))
            return
        }
        enEjecucion = true
        anchoJuego = ancho
        altoJuego = alto
        fijarResolucion(ancho, alto)
        st.setDefaultBufferSize(anchoJuego, altoJuego)
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
                        dirJuego = null
                        detenerServicio()
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
        // A PROPOSITO no se avisa al juego del nuevo tamano. La ventana cambia
        // de medida por motivos pasajeros —barra de notificaciones, capas del
        // sistema, teclado— y cada aviso obligaba al juego a rehacer su
        // fotograma; durante ese rato la parte no cubierta se veia negra. Se
        // vuelve a fijar el buffer al tamano de render y TextureView escala.
        if (anchoJuego > 0 && altoJuego > 0) {
            st.setDefaultBufferSize(anchoJuego, altoJuego)
        } else {
            puente?.pushEventWindow(ancho, alto)
        }
    }

    /**
     * Al recuperar el foco (cerrar las notificaciones, volver de otra app) el
     * sistema deja sus barras visibles: si no se vuelven a esconder, la
     * ventana queda con otra medida y reaparecen las franjas.
     */
    override fun onWindowFocusChanged(tieneFoco: Boolean) {
        super.onWindowFocusChanged(tieneFoco)
        if (!tieneFoco) return
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
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

            // Un segundo dedo levantandose dejaba el clic derecho pegado y la
            // camara saltaba al otro dedo: se trata como fin de gesto.
            MotionEvent.ACTION_POINTER_UP -> {
                handler.removeCallbacks(toqueLargo)
                if (sosteniendoDerecho) {
                    p.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt(), false)
                    sosteniendoDerecho = false
                }
                seMovio = true // ya no cuenta como toque corto
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
                        // Por la misma cola que ENTER y retroceso: enviados al
                        // instante, las letras adelantaban a los borrados y el
                        // juego recibía el texto en otro orden del que veías.
                        else caracter(p, c)
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

    /** Un carácter, respetando el orden de la cola de pulsaciones. */
    private fun caracter(p: FCLBridge, c: Char) {
        val base = maxOf(android.os.SystemClock.uptimeMillis(), proximoDisparo)
        proximoDisparo = base + 12
        handler.postAtTime({ p.pushEventChar(c) }, base)
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
        // Atras cierra primero lo que este abierto en la app; solo cuando no
        // hay nada propio abierto llega al juego como ESC. Antes, editando el
        // HUD abrias ademas el menu de pausa DEBAJO de la barra de edicion.
        when {
            modoEdicion -> salirModoEdicion(guardar = false)
            panelMenu != null -> cerrarMenu()
            else -> puente?.let { tecla(it, FCLKeycodes.KEY_ESC) }
        }
    }

    // ── Foco y visibilidad de la ventana GLFW (paridad con FCL) ─────────────
    // Sin esto el juego cree seguir enfocado al minimizar y algunos mods
    // (pausa automática, sonido) se comportan raro.

    override fun onResume() {
        super.onResume()
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 1)
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1)
        // Si el jugador editó el HUD en Ajustes mientras la partida estaba
        // minimizada, al volver hay que traer ESE diseño: la copia en memoria
        // pisaba silenciosamente todo lo hecho allí.
        if (!modoEdicion) {
            val guardado = com.lucerion.launcher.data.RepositorioDiseno.cargar(this)
            if (guardado != diseno) {
                diseno = guardado
                construirHud()
            }
        }
    }

    override fun onPause() {
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 0)
        org.lwjgl.glfw.CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0)
        soltarTodo()
        super.onPause()
    }

    /**
     * Suelta todo lo que pudiera haber quedado pulsado al irse a segundo
     * plano. Sin esto, minimizar en pleno toque dejaba al personaje andando
     * o agachado para siempre, sin nadie que soltara la tecla.
     */
    private fun soltarTodo() {
        val p = puente ?: return
        for ((codigo, pulsada) in movimientoActivo) {
            if (pulsada) p.pushEventKey(codigo, 0, false)
        }
        movimientoActivo.keys.forEach { movimientoActivo[it] = false }
        p.pushEventKey(FCLKeycodes.KEY_LEFTCTRL, 0, false)
        if (sosteniendoDerecho) {
            p.pushEventMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt(), false)
            sosteniendoDerecho = false
        }
        handler.removeCallbacks(toqueLargo)
    }

    private fun detenerServicio() {
        runCatching {
            stopService(
                android.content.Intent(this, com.lucerion.launcher.motor.ServicioJuego::class.java),
            )
        }
    }

    override fun onDestroy() {
        // El observador de layout retiene la Activity y los envíos diferidos
        // pueden dispararse sobre un puente que ya no existe.
        observadorTeclado?.let { window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        observadorTeclado = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
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
