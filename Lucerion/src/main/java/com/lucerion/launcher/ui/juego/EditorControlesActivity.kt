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
    // Clave por id (inmutable): ControlHud es data class y su hash cambia al
    // arrastrar (x/y mutan) — como clave de mapa se "perdia" tras moverlo.
    private val velos = HashMap<String, View>()
    private val etiquetasTam = HashMap<String, TextView>()
    private lateinit var panelTitulo: TextView
    private lateinit var deslizador: SeekBar
    private lateinit var etiquetaTam: TextView
    private lateinit var deslizadorOpacidad: SeekBar
    private lateinit var botonBorrar: Button
    private var escala = 1f

    private companion object {
        const val SELECCION = 0x2E5EC8FF
        const val TAM_BASE_PERSONALIZADO = 56
    }

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
        val aQuitar = (0 until lienzo.childCount).map { lienzo.getChildAt(it) }
            .filter { it.tag is ControlHud }
        aQuitar.forEach { lienzo.removeView(it) }
        velos.clear()
        etiquetasTam.clear()

        val w = lienzo.width
        val h = lienzo.height
        for (c in diseno.controles) {
            val tamPx = dp((c.tam * escala).toInt())
            val cont = FrameLayout(this)
            cont.tag = c
            cont.addView(vistaDe(c), FrameLayout.LayoutParams(tamPx, tamPx))
            cont.alpha = c.opacidad.coerceIn(0.15f, 1f)
            // Capa transparente encima: captura el arrastre sin que el control
            // "funcione" dentro del editor.
            val velo = View(this)
            velo.setBackgroundColor(if (c === seleccionado) SELECCION else Color.TRANSPARENT)
            cont.addView(velo, FrameLayout.LayoutParams(tamPx, tamPx))
            velos[c.id] = velo

            // Porcentaje sobre el propio control: comparar de un vistazo.
            val etiqueta = TextView(this).apply {
                text = "${porcentajeDe(c)} %"
                setTextColor(0xFFE8D9A0.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
                setBackgroundColor(0xB3101820.toInt())
            }
            cont.addView(
                etiqueta,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ),
            )
            etiquetasTam[c.id] = etiqueta

            // Posicion por propiedades x/y (composicion GPU): el arrastre es
            // fluido porque NO se relayoutea nada por cada movimiento.
            val lp = FrameLayout.LayoutParams(tamPx, tamPx)
            lienzo.addView(cont, lp)
            cont.x = (c.x * w - tamPx / 2f).coerceIn(0f, maxOf(0f, (w - tamPx).toFloat()))
            cont.y = (c.y * h - tamPx / 2f).coerceIn(0f, maxOf(0f, (h - tamPx).toFloat()))

            var dX = 0f
            var dY = 0f
            velo.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        seleccionar(c)
                        dX = e.rawX - cont.x
                        dY = e.rawY - cont.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        cont.x = (e.rawX - dX).coerceIn(0f, maxOf(0f, (lienzo.width - cont.width).toFloat()))
                        cont.y = (e.rawY - dY).coerceIn(0f, maxOf(0f, (lienzo.height - cont.height).toFloat()))
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        c.x = (cont.x + cont.width / 2f) / lienzo.width
                        c.y = (cont.y + cont.height / 2f) / lienzo.height
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
        deslizadorOpacidad.progress = ((c.opacidad * 100).toInt() - 15).coerceIn(0, 85)
        mostrarTam(c)
        botonBorrar.visibility = if (c.tipo == "tecla") View.VISIBLE else View.GONE
        for (control in diseno.controles) {
            velos[control.id]?.setBackgroundColor(if (control === c) SELECCION else Color.TRANSPARENT)
        }
    }

    /**
     * Tamaño en porcentaje respecto del predeterminado de ESE control (100 % =
     * el de fábrica) y en dp: con el número a la vista se pueden igualar dos
     * botones sin ojímetro.
     */
    private fun porcentajeDe(c: ControlHud): Int {
        val base = RepositorioDiseno.porDefecto().controles
            .firstOrNull { it.id == c.id }?.tam
            ?: TAM_BASE_PERSONALIZADO
        return Math.round(c.tam * 100f / base)
    }

    private fun mostrarTam(c: ControlHud) {
        etiquetaTam.text = "Tamaño: ${porcentajeDe(c)} %  ·  ${c.tam} dp  ·  opacidad ${(c.opacidad * 100).toInt()} %"
        etiquetasTam[c.id]?.text = "${porcentajeDe(c)} %"
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

        etiquetaTam = TextView(context).apply {
            text = "Tamaño del control seleccionado:"
            setTextColor(0xB3FFFFFF.toInt())
        }
        addView(etiquetaTam)
        deslizador = SeekBar(context).apply {
            max = 184 // 36..220 dp
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progreso: Int, delUsuario: Boolean) {
                    if (delUsuario) {
                        // Redimensionar SOLO el seleccionado manteniendo su
                        // centro: reconstruir todo por cada tick trababa.
                        seleccionado?.let { c ->
                            c.tam = progreso + 36
                            mostrarTam(c)
                            val velo = velos[c.id] ?: return@let
                            val cont = velo.parent as FrameLayout
                            val tamPx = dp((c.tam * escala).toInt())
                            val cx = cont.x + cont.width / 2f
                            val cy = cont.y + cont.height / 2f
                            (0 until cont.childCount).forEach { i ->
                                cont.getChildAt(i).layoutParams = FrameLayout.LayoutParams(tamPx, tamPx)
                            }
                            cont.layoutParams = FrameLayout.LayoutParams(tamPx, tamPx)
                            cont.x = cx - tamPx / 2f
                            cont.y = cy - tamPx / 2f
                        }
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }
        addView(deslizador, LinearLayout.LayoutParams(dp(230), LinearLayout.LayoutParams.WRAP_CONTENT))

        addView(TextView(context).apply {
            text = "Opacidad del control:"
            setTextColor(0xB3FFFFFF.toInt())
        })
        deslizadorOpacidad = SeekBar(context).apply {
            max = 85 // 15..100 %
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progreso: Int, delUsuario: Boolean) {
                    if (!delUsuario) return
                    val c = seleccionado ?: return
                    c.opacidad = (progreso + 15) / 100f
                    mostrarTam(c)
                    (velos[c.id]?.parent as? FrameLayout)?.alpha = c.opacidad
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }
        addView(deslizadorOpacidad, LinearLayout.LayoutParams(dp(230), LinearLayout.LayoutParams.WRAP_CONTENT))

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

    /**
     * Alta en dos pasos (mezclar lista y campo en un mismo dialogo hace que
     * Android ignore uno de los dos): 1) elegir la tecla, 2) etiqueta visible.
     */
    private fun dialogoNuevoBoton() {
        val nombres = CatalogoTeclas.disponibles.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Nuevo botón — ¿qué tecla pulsará?")
            .setItems(nombres) { _, cual -> dialogoEtiqueta(cual) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun dialogoEtiqueta(indice: Int) {
        val (nombre, codigo) = CatalogoTeclas.disponibles[indice]
        val etiqueta = EditText(this).apply {
            hint = "Etiqueta visible (p. ej. Mochila)"
            setText(nombre)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Tecla $nombre — etiqueta del botón")
            .setView(etiqueta)
            .setPositiveButton("Añadir") { _, _ ->
                val nuevo = ControlHud(
                    id = "tecla_${System.nanoTime()}",
                    tipo = "tecla",
                    x = 0.5f, y = 0.45f, tam = 56,
                    etiqueta = etiqueta.text.toString().ifBlank { nombre },
                    tecla = codigo,
                )
                diseno.controles.add(nuevo)
                redibujar()
                seleccionar(nuevo)
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
