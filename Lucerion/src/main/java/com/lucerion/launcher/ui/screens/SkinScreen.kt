package com.lucerion.launcher.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucerion.launcher.data.RepositorioSkin
import com.lucerion.launcher.ui.skin.VistaSkin3D
import com.lucerion.launcher.ui.theme.Bg3
import com.lucerion.launcher.ui.theme.Oro
import com.lucerion.launcher.ui.theme.OroClaro
import com.lucerion.launcher.ui.theme.OroProfundo
import com.lucerion.launcher.ui.theme.TextoSuave

/**
 * Skin del jugador: elegirla, verla en 3D girándola con el dedo y decidir el
 * modelo del cuerpo. La skin elegida entra al juego — el motor la sirve a
 * través de su propio servidor de sesiones para cuentas libres.
 */
@Composable
fun SkinScreen(alVolver: () -> Unit = {}) {
    val contexto = LocalContext.current
    // Coherencia con Cuenta y Ajustes: el boton atras vuelve a Inicio en vez
    // de cerrar la app desde una seccion que no es la raiz.
    androidx.activity.compose.BackHandler(onBack = alVolver)

    // Sin skin propia se muestra la clasica: asi es como te ven en el
    // servidor mientras no elijas una.
    var bitmap by remember { mutableStateOf<Bitmap?>(RepositorioSkin.cargarBitmap(contexto)) }
    val propia = bitmap != null
    val mostrada = bitmap ?: com.lucerion.launcher.ui.skin.SkinPorDefecto.bitmap(contexto)
    var delgada by remember { mutableStateOf(RepositorioSkin.modeloDelgado(contexto)) }
    var vista3D by remember { mutableStateOf(RepositorioSkin.vista3D(contexto)) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val elegir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            aviso = RepositorioSkin.importar(contexto, uri)
            if (aviso == null) bitmap = RepositorioSkin.cargarBitmap(contexto)
        }
    }

    // En apaisado hay poco alto: un recuadro fijo de 340 dejaba los ajustes
    // fuera de la vista y el modelo se comia el gesto de desplazar.
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val apaisado = config.screenWidthDp > config.screenHeightDp
    val altoVista = if (apaisado) 250.dp else 340.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("Tu skin", style = MaterialTheme.typography.displayLarge, color = OroClaro)
        Text(
            "Así te ven los demás en Cretania. Elige una imagen de skin de " +
                "Minecraft (64×64, 64×32 o 128×128) y gírala con el dedo para " +
                "revisarla por todos lados.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSuave,
        )
        Spacer(Modifier.height(18.dp))

        // ── Escenario del previsualizador ────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // En apaisado el recuadro se estiraba a todo el ancho con el
                // modelo diminuto al centro; acotado, el personaje se ve igual
                // y queda sitio para lo demás.
                .widthIn(max = if (apaisado) 420.dp else Dp.Infinity)
                .height(altoVista)
                .border(1.dp, OroProfundo.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
            color = Bg3,
            shape = RoundedCornerShape(16.dp),
        ) {
            // Siempre el modelo armado: el interruptor solo decide si se
            // puede girar (3D) o queda de frente y quieto (vista plana).
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> VistaSkin3D(ctx) },
                update = { vista ->
                    vista.ponerTextura(mostrada)
                    vista.ponerModeloDelgado(delgada)
                    vista.ponerInteractivo(vista3D)
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            (if (propia) "Tu skin. " else "Aspecto clásico: el que usas mientras no elijas una skin propia. ") +
                (if (vista3D) "Arrastra sobre el modelo para girarlo e inclinarlo."
                else "Vista fija de frente."),
            style = MaterialTheme.typography.bodyMedium,
            color = TextoSuave.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(16.dp))

        aviso?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(10.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonBorde(if (!propia) "ELEGIR SKIN" else "CAMBIAR SKIN") {
                aviso = null
                elegir.launch(arrayOf("image/png"))
            }
            if (propia) {
                BotonBorde("QUITAR") {
                    RepositorioSkin.quitar(contexto)
                    bitmap = null
                    aviso = null
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        TarjetaOpcion(
            titulo = "Vista en 3D",
            explicacion = "Deja girar el personaje con el dedo para revisarlo por todos " +
                "lados. Al desactivarla se ve de frente y quieto, como una figura plana.",
            valor = vista3D,
            alCambiar = {
                vista3D = it
                RepositorioSkin.guardarVista3D(contexto, it)
            },
        )
        TarjetaOpcion(
            titulo = "Modelo delgado (Alex)",
            explicacion = "Brazos de 3 píxeles en vez de 4. Actívalo si tu skin fue hecha " +
                "para el modelo delgado; si los brazos se ven cortados o estirados, es esto.",
            valor = delgada,
            alCambiar = {
                delgada = it
                RepositorioSkin.guardarModeloDelgado(contexto, it)
            },
        )
    }
}

@Composable
private fun BotonBorde(texto: String, alPulsar: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .border(1.dp, Oro, RoundedCornerShape(10.dp))
            .clickable(onClick = alPulsar)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge, color = OroClaro)
    }
}

@Composable
private fun TarjetaOpcion(
    titulo: String,
    explicacion: String,
    valor: Boolean,
    alCambiar: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, OroProfundo.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
        color = Bg3,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, color = OroClaro)
                Text(explicacion, style = MaterialTheme.typography.bodyMedium, color = TextoSuave)
            }
            Spacer(Modifier.width(14.dp))
            Switch(
                checked = valor,
                onCheckedChange = alCambiar,
                colors = SwitchDefaults.colors(checkedTrackColor = Oro),
            )
        }
    }
}
