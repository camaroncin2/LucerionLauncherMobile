package com.lucerion.launcher.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.lucerion.launcher.ui.theme.Bg
import com.lucerion.launcher.ui.theme.Bg2
import com.lucerion.launcher.ui.theme.Oro
import com.lucerion.launcher.ui.theme.OroClaro
import com.lucerion.launcher.ui.theme.OroProfundo
import com.lucerion.launcher.ui.theme.TextoSuave

/** Secciones del launcher. El orden es el de la barra. */
enum class Seccion(val titulo: String) {
    Inicio("Inicio"),
    Cuenta("Cuenta"),
    Skin("Skin"),
    Ajustes("Ajustes"),
}

/**
 * Navegación de la app, con dos formas según cómo sostengas el teléfono:
 *
 *  · **Vertical**: barra flotante abajo — el pulgar llega sin estirarse.
 *    La sección activa se marca con una cápsula dorada que se desliza.
 *  · **Horizontal**: barra lateral izquierda que se despliega al tocar el
 *    menú, mostrando los nombres junto a los iconos.
 *
 * Los iconos se dibujan vectoriales con la paleta de la marca: nada de
 * material genérico, misma familia visual que los controles del juego.
 */

// ── Barra inferior (vertical) ───────────────────────────────────────────────

@Composable
fun BarraInferior(
    seccion: Seccion,
    alElegir: (Seccion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Bg2, Bg)))
            .border(1.dp, OroProfundo.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (s in Seccion.entries) {
            val activa = s == seccion
            val fondo by animateColorAsState(
                if (activa) Oro.copy(alpha = 0.16f) else Color.Transparent,
                label = "fondoNav",
            )
            val tinte by animateColorAsState(if (activa) OroClaro else TextoSuave, label = "tinteNav")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(fondo)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { alElegir(s) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconoSeccion(s, tinte, 22.dp)
                // El nombre solo aparece en la activa: la barra respira.
                val ancho by animateDpAsState(
                    if (activa) 62.dp else 0.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                    label = "anchoNav",
                )
                if (ancho > 0.dp) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = s.titulo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tinte,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.width(ancho),
                    )
                }
            }
        }
    }
}

// ── Barra lateral (horizontal) ──────────────────────────────────────────────

@Composable
fun BarraLateral(
    seccion: Seccion,
    alElegir: (Seccion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var desplegada by remember { mutableStateOf(false) }
    val ancho by animateDpAsState(
        if (desplegada) 172.dp else 64.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "anchoLateral",
    )

    Column(
        modifier = modifier
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp)
            .width(ancho)
            .fillMaxHeight()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Bg2, Bg)))
            .border(1.dp, OroProfundo.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Botón hamburguesa: despliega los nombres.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { desplegada = !desplegada }
                .padding(12.dp),
        ) {
            IconoMenu(OroClaro, 22.dp)
        }
        Spacer(Modifier.height(6.dp))

        for (s in Seccion.entries) {
            val activa = s == seccion
            val fondo by animateColorAsState(
                if (activa) Oro.copy(alpha = 0.16f) else Color.Transparent,
                label = "fondoLat",
            )
            val tinte by animateColorAsState(if (activa) OroClaro else TextoSuave, label = "tinteLat")
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(fondo)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { alElegir(s) }
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .width(if (desplegada) 124.dp else 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconoSeccion(s, tinte, 22.dp)
                if (desplegada) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = s.titulo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tinte,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

// ── Iconos vectoriales de la marca ──────────────────────────────────────────

@Composable
private fun IconoSeccion(seccion: Seccion, color: Color, tam: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(tam)) {
        val w = size.width
        val h = size.height
        val grosor = w * 0.10f
        when (seccion) {
            // Casa: tejado y cuerpo.
            Seccion.Inicio -> {
                val tejado = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.92f, h * 0.48f)
                    lineTo(w * 0.08f, h * 0.48f)
                    close()
                }
                drawPath(tejado, color)
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.20f, h * 0.48f),
                    size = androidx.compose.ui.geometry.Size(w * 0.60f, h * 0.40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
                    style = Stroke(width = grosor),
                )
            }
            // Persona: cabeza y hombros.
            Seccion.Cuenta -> {
                drawCircle(color, radius = w * 0.17f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.30f))
                val hombros = Path().apply {
                    moveTo(w * 0.16f, h * 0.88f)
                    cubicTo(w * 0.16f, h * 0.58f, w * 0.84f, h * 0.58f, w * 0.84f, h * 0.88f)
                }
                drawPath(hombros, color, style = Stroke(width = grosor))
            }
            // Skin: figura de bloques (cabeza + cuerpo), guiño a Minecraft.
            Seccion.Skin -> {
                drawRect(
                    color,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.12f),
                    size = androidx.compose.ui.geometry.Size(w * 0.40f, h * 0.32f),
                )
                drawRect(
                    color,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.50f),
                    size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.38f),
                    style = Stroke(width = grosor),
                )
            }
            // Engranaje: la marca de la casa.
            Seccion.Ajustes -> {
                val cx = w / 2f
                val cy = h / 2f
                val r = w * 0.30f
                for (i in 0 until 8) {
                    rotate(i * 45f, pivot = androidx.compose.ui.geometry.Offset(cx, cy)) {
                        drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.055f, cy - r - w * 0.09f),
                            size = androidx.compose.ui.geometry.Size(w * 0.11f, w * 0.15f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
                        )
                    }
                }
                drawCircle(color, radius = r * 0.74f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(width = grosor * 0.9f))
                drawCircle(color, radius = r * 0.26f, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(width = grosor * 0.7f))
            }
        }
    }
}

@Composable
private fun IconoMenu(color: Color, tam: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(tam)) {
        val w = size.width
        val h = size.height
        val grosor = w * 0.11f
        for (f in listOf(0.26f, 0.5f, 0.74f)) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(w * 0.12f, h * f),
                end = androidx.compose.ui.geometry.Offset(w * 0.88f, h * f),
                strokeWidth = grosor,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
