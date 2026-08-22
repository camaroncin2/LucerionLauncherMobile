package com.lucerion.launcher.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.lucerion.launcher.R
import com.lucerion.launcher.ui.theme.Bg
import com.lucerion.launcher.ui.theme.Bg2
import com.lucerion.launcher.ui.theme.Oro
import com.lucerion.launcher.ui.theme.OroClaro
import com.lucerion.launcher.ui.theme.TextoSuave
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pantalla de arranque: engranaje dorado girando sobre fondo navy.
 * Dura lo justo (1,4 s) — es identidad, no una espera artificial.
 */
@Composable
fun SplashScreen(alTerminar: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1400)
        alTerminar()
    }

    val transicion = rememberInfiniteTransition(label = "engranaje")
    val angulo by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "giro",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Bg2, Bg)))
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        if (maxWidth > maxHeight) {
            // Horizontal: engranaje y marca lado a lado, aprovechando el ancho.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                EmblemaLucerion(angulo)
                MarcaSplash()
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                EmblemaLucerion(angulo)
                MarcaSplash()
            }
        }
    }
}

@Composable
private fun MarcaSplash() {
    // Contenedor propio: sueltos, los dos textos heredaban el espaciado de
    // cada rama (20 dp en vertical, 8 en apaisado) y la marca se veia
    // distinta segun como sostuvieras el telefono.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
    Text(
        text = stringResource(R.string.home_titulo),
        style = MaterialTheme.typography.displayLarge,
        color = OroClaro,
    )
    Text(
        text = stringResource(R.string.splash_lema),
        style = MaterialTheme.typography.bodyMedium,
        color = TextoSuave,
    )
    }
}

/**
 * Emblema de la marca: el icono de Lucerion quieto y nitido, con el engranaje
 * girando alrededor. El logo no gira a proposito — un logo dando vueltas se
 * lee peor y pierde la fuerza de la marca.
 */
@Composable
private fun EmblemaLucerion(angulo: Float) {
    Box(contentAlignment = Alignment.Center) {
        EngranajeGirando(angulo)
        Image(
            painter = painterResource(R.drawable.logo_lucerion),
            contentDescription = null,
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape),
        )
    }
}

/** Engranaje de 10 dientes dibujado a mano — el sello steampunk de la marca. */
@Composable
private fun EngranajeGirando(angulo: Float) {
    Canvas(modifier = Modifier.size(96.dp)) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val radioExterior = size.minDimension / 2f
        val radioCorona = radioExterior * 0.78f
        val radioInterior = radioExterior * 0.36f

        rotate(degrees = angulo, pivot = centro) {
            // Dientes
            val dientes = 10
            val anchoDiente = radioExterior * 0.22f
            for (i in 0 until dientes) {
                val a = Math.toRadians((i * (360.0 / dientes)))
                val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(
                    color = Oro,
                    start = centro + dir * radioCorona,
                    end = centro + dir * radioExterior,
                    strokeWidth = anchoDiente,
                )
            }
            // Corona
            drawCircle(
                color = Oro,
                radius = radioCorona,
                center = centro,
                style = Stroke(width = radioExterior * 0.18f),
            )
            // Eje
            drawCircle(
                color = OroClaro,
                radius = radioInterior * 0.45f,
                center = centro,
            )
        }
    }
}
