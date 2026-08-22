package com.lucerion.launcher.ui.juego

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.lucerion.launcher.data.CatalogoTeclas
import com.lucerion.launcher.data.ControlHud
import com.lucerion.launcher.data.DisenoHud
import com.lucerion.launcher.data.RepositorioAjustes
import com.lucerion.launcher.data.RepositorioDiseno

/**
 * Editor de controles con previsualización, como los launchers móviles:
 * los controles se ven tal cual en el juego; arrastras para mover, el
 * deslizador cambia el tamaño del seleccionado, y puedes añadir botones
 * personalizados ligados a una tecla (p. ej. "B" para la mochila).
 */
class EditorControlesActivity : Activity() {

    private lateinit var lienzo: FrameLayout
    private lateinit var diseno: DisenoHud
    private var seleccionado: ControlHud? = null
    private lateinit var panelTitulo: TextView
    private lateinit var deslizador: SeekBar
    private lateinit var botonBorrar: Button
    private var escala = 1f

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        escala = RepositorioAjustes.escalaControles(this)
        diseno = RepositorioDiseno.cargar(this)

        lienzo = FrameLayout(this).apply { setBackgroundColor(0xFF10151C.toInt()) }

        // Franja guía: recuerda que abajo-centro vive la hotbar del juego.
        lienzo.addView(
            TextView(this).apply {
                text = "· hotbar del juego ·"
                setTextColor(0x66FFFFFF)
                gravity = Gravity.CENTER
            },
            FrameLayout.LayoutParams(dp(360), dp(30), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
        )

        lienzo.addView(crearPanel(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ).apply { topMargin = dp(8); leftMargin = dp(8) })

        setContentView(lienzo)
        lienzo.post { redibujar() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ── Vista previa ─────────────────────────────────────────────────────────

    private fun vistaDe(c: ControlHud): View = when {
        c.tipo == "tecla" -> BotonTactil(this, BotonTactil.Glifo.PAUSA, texto = c.etiqueta ?: "?", alPresionar = {})
        c.id == "movimiento" ->
            if (RepositorioAjustes.usarCruceta(this)) BotonTactil(this, BotonTactil.Glifo.FLECHA_ARRIBA, alPresionar = {})
            else PalancaTactil(this, alCambiar = { _, _, _, _ -> })
        c.id == "salto" -> BotonTactil(this, BotonTactil.Glifo.SALTO, alPresionar = {})
        c.id == "golpear" -> BotonTactil(this, BotonTactil.Glifo.GOLPEAR, alPresionar = {})
        c.id == "agacharse" -> BotonTactil(this, BotonTactil.Glifo.AGACHARSE, alPresionar = {})
        c.id == "chat" -> BotonTactil(this, BotonTactil.Glifo.CHAT, alPresionar = {})
        c.id == "pausa" -> BotonTactil(this, BotonTactil.Glifo.PAUSA, alPresionar = {})
        c.id == "inventario" -> BotonTactil(this, BotonTactil.Glifo.INVENTARIO, alPresionar = {})
        else -> BotonTactil(this, BotonTactil.Glifo.TECLADO, alPresionar = {})
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun redibujar() {
        // Quitar controles previos (todo lo que tenga ControlHud de tag).
        val aQuitar = (0 until lienzo.childCount).map { lienzo.getChildAt(it) }
            .filter { it.tag is ControlHud }
        aQuitar.forEach { lienzo.removeView(it) }

        val w = lienzo.width
        val h = lienzo.height
        for (c in diseno.controles) {
            val tamPx = dp((c.tam * escala).toInt())
            val cont = FrameLayout(this)
            cont.tag = c
            cont.addView(vistaDe(c), FrameLayout.LayoutParams(tamPx, tamPx))
            // Capa transparente encima: captura el arrastre sin que el control
            // "funcione" dentro del editor.
            val velo = View(this)
            velo.setBackgroundColor(if (c === seleccionado) 0x2E5EC8FF else Color.TRANSPARENT)
            cont.addView(velo, FrameLayout.LayoutParams(tamPx, tamPx))

            val lp = FrameLayout.LayoutParams(tamPx, tamPx)
            lp.leftMargin = (c.x * w - tamPx / 2f).toInt().coerceIn(0, maxOf(0, w - tamPx))
            lp.topMargin = (c.y * h - tamPx / 2f).toInt().coerceIn(0, maxOf(0, h - tamPx))
            lienzo.addView(cont, lp)

            var iniX = 0f
            var iniY = 0f
            velo.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        seleccionar(c)
                        iniX = e.rawX - (cont.layoutParams as FrameLayout.LayoutParams).leftMargin
                        iniY = e.rawY - (cont.layoutParams as FrameLayout.LayoutParams).topMargin
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val nlp = cont.layoutParams as FrameLayout.LayoutParams
                        nlp.leftMargin = (e.rawX - iniX).toInt().coerceIn(0, maxOf(0, lienzo.width - nlp.width))
                        nlp.topMargin = (e.rawY - iniY).toInt().coerceIn(0, maxOf(0, lienzo.height - nlp.height))
                        cont.layoutParams = nlp
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val nlp = cont.layoutParams as FrameLayout.LayoutParams
                        c.x = (nlp.leftMargin + nlp.width / 2f) / lienzo.width
                        c.y = (nlp.topMargin + nlp.height / 2f) / lienzo.height
                    }
                }
                true
            }
        }
    }

    private fun seleccionar(c: ControlHud) {
        seleccionado = c
        panelTitulo.text = when {
            c.tipo == "tecla" -> "Botón «${c.etiqueta}» (tecla)"
            c.id == "movimiento" -> "Movimiento (palanca/cruceta)"
            else -> c.id.replaceFirstChar { it.uppercase() }
        }
        deslizador.progress = c.tam - 36
        botonBorrar.visibility = if (c.tipo == "tecla") View.VISIBLE else View.GONE
        redibujar()
    }

    // ── Panel de edición ─────────────────────────────────────────────────────

    private fun crearPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xD91A222C.toInt())
        setPadding(dp(12), dp(10), dp(12), dp(10))

        panelTitulo = TextView(context).apply {
            text = "Toca un control para editarlo; arrástralo para moverlo."
            setTextColor(0xFFE8D9A0.toInt())
        }
        addView(panelTitulo)

        addView(TextView(context).apply {
            text = "Tamaño del control seleccionado:"
            setTextColor(0xB3FFFFFF.toInt())
        })
        deslizador = SeekBar(context).apply {
            max = 184 // 36..220 dp
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progreso: Int, delUsuario: Boolean) {
                    if (delUsuario) {
                        seleccionado?.let { it.tam = progreso + 36; redibujar() }
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }
        addView(deslizador, LinearLayout.LayoutParams(dp(230), LinearLayout.LayoutParams.WRAP_CONTENT))

        val fila = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun accion(texto: String, alPulsar: () -> Unit): Button = Button(context).apply {
            text = texto
            textSize = 12f
            setOnClickListener { alPulsar() }
        }
        fila.addView(accion("＋ BOTÓN") { dialogoNuevoBoton() })
        botonBorrar = accion("BORRAR") {
            seleccionado?.let { if (it.tipo == "tecla") { diseno.controles.remove(it); seleccionado = null; redibujar() } }
        }.apply { visibility = View.GONE }
        fila.addView(botonBorrar)
        fila.addView(accion("RESTABLECER") {
            RepositorioDiseno.restablecer(this@EditorControlesActivity)
            diseno = RepositorioDiseno.porDefecto()
            seleccionado = null
            redibujar()
        })
        fila.addView(accion("GUARDAR Y SALIR") {
            RepositorioDiseno.guardar(this@EditorControlesActivity, diseno)
            finish()
        })
        addView(fila)
    }

    /** Diálogo: etiqueta + tecla del catálogo → nuevo botón personalizado al centro. */
    private fun dialogoNuevoBoton() {
        val nombres = CatalogoTeclas.disponibles.map { it.first }.toTypedArray()
        var indice = 1 // "B": el ejemplo canónico (mochila)
        val etiqueta = EditText(this).apply {
            hint = "Etiqueta visible (p. ej. Mochila)"
            setText("B")
        }
        AlertDialog.Builder(this)
            .setTitle("Nuevo botón — elige la tecla que pulsará")
            .setSingleChoiceItems(nombres, indice) { _, cual -> indice = cual }
            .setView(etiqueta)
            .setPositiveButton("Añadir") { _, _ ->
                val (nombre, codigo) = CatalogoTeclas.disponibles[indice]
                diseno.controles.add(
                    ControlHud(
                        id = "tecla_${System.currentTimeMillis()}",
                        tipo = "tecla",
                        x = 0.5f, y = 0.5f, tam = 56,
                        etiqueta = etiqueta.text.toString().ifBlank { nombre },
                        tecla = codigo,
                    ),
                )
                redibujar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onPause() {
        // Guardado defensivo: salir por gesto no pierde la edición.
        RepositorioDiseno.guardar(this, diseno)
        super.onPause()
    }
}
