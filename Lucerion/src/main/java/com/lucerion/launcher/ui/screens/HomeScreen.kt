package com.lucerion.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucerion.launcher.R
import com.lucerion.launcher.ui.theme.AmbarCta1
import com.lucerion.launcher.ui.theme.AmbarCta2
import com.lucerion.launcher.ui.theme.Bg
import com.lucerion.launcher.ui.theme.Bg2
import com.lucerion.launcher.ui.theme.Bg3
import com.lucerion.launcher.ui.theme.Oro
import com.lucerion.launcher.ui.theme.OroClaro
import com.lucerion.launcher.ui.theme.OroProfundo
import com.lucerion.launcher.ui.theme.TextoSuave

/**
 * Pantalla principal. El principio de la especificación manda: el camino feliz
 * es UN toque — "JUGAR CRETANIA" — y todas las decisiones técnicas las toma el
 * launcher.
 *
 * Junto al botón está tu personaje: se ve al entrar y se cambia sin rodeos.
 * Cuenta y Ajustes ya no viven aquí — están en la barra de navegación.
 *
 * Dos distribuciones:
 *  · Vertical: pila única (marca arriba, CTA al centro, secciones abajo).
 *  · Horizontal: dos paneles — marca y JUGAR a la izquierda, el personaje a
 *    la derecha — porque en apaisado la pila desperdicia el ancho y obliga a
 *    hacer scroll para lo importante.
 */
@Composable
fun HomeScreen(
    versionApp: String,
    alJugar: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg2, Bg))),
    ) {
        if (maxWidth > maxHeight) {
            HomeHorizontal(versionApp, alJugar)
        } else {
            HomeVertical(versionApp, alJugar)
        }
    }
}

// ── Distribución vertical ────────────────────────────────────────────────────

@Composable
private fun HomeVertical(versionApp: String, alJugar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CabeceraMarca()
        Spacer(Modifier.height(12.dp))
        SeparadorRemaches()
        Spacer(Modifier.height(28.dp))
        BloqueJugar(alJugar)
        Spacer(Modifier.height(24.dp))
        BloqueSkinInicio(alturaVista = 300)
        Spacer(Modifier.height(28.dp))
        PieVersion(versionApp)
    }
}

// ── Distribución horizontal ──────────────────────────────────────────────────

@Composable
private fun HomeHorizontal(versionApp: String, alJugar: () -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Panel de marca y acción: identidad arriba y JUGAR debajo, ocupando
        // el hueco que antes quedaba vacío. Sin scroll: nada debe empujar al
        // botón fuera de la vista.
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CabeceraMarca()
            Spacer(Modifier.height(14.dp))
            SeparadorRemaches()
            Spacer(Modifier.height(26.dp))
            BloqueJugar(alJugar)
            Spacer(Modifier.weight(1f))
            PieVersion(versionApp)
        }

        // Borde dorado vertical que separa los paneles, como junta de placa.
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Oro, Color.Transparent))),
        )

        // Panel del personaje: solo la skin, ocupando el alto disponible para
        // que los botones queden siempre visibles sin desplazar nada.
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            BloqueSkinInicio(expandible = true, modifier = Modifier.fillMaxHeight())
        }
    }
}

// ── Piezas compartidas por ambas distribuciones ──────────────────────────────

@Composable
private fun CabeceraMarca() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.home_titulo),
            style = MaterialTheme.typography.displayLarge,
            color = OroClaro,
        )
        Text(
            text = stringResource(R.string.home_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSuave,
        )
    }
}

@Composable
private fun BloqueJugar(alJugar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BotonJugar(alPulsar = alJugar)
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_jugar_detalle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSuave,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PieVersion(versionApp: String) {
    Text(
        text = stringResource(R.string.home_version, versionApp),
        style = MaterialTheme.typography.bodyMedium,
        color = TextoSuave.copy(alpha = 0.6f),
    )
}

/** CTA dorado con gradiente de marca y marco tipo placa metálica. */
@Composable
private fun BotonJugar(alPulsar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(2.dp, OroProfundo, RoundedCornerShape(14.dp))
            .padding(3.dp)
            .background(
                brush = Brush.verticalGradient(listOf(AmbarCta1, AmbarCta2)),
                shape = RoundedCornerShape(11.dp),
            )
            .clickable(onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.home_jugar),
            style = MaterialTheme.typography.labelLarge,
            color = Bg,
        )
    }
}

/** Separador decorativo: línea dorada con "remaches" — detalle steampunk sutil. */
@Composable
private fun SeparadorRemaches() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Remache()
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Oro, Color.Transparent))),
        )
        Remache()
    }
}

@Composable
private fun Remache() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(Oro, RoundedCornerShape(percent = 50)),
    )
}
