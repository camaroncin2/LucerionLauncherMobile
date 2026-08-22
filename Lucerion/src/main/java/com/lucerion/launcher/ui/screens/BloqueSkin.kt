package com.lucerion.launcher.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lucerion.launcher.data.RepositorioSkin
import com.lucerion.launcher.ui.skin.SkinPorDefecto
import com.lucerion.launcher.ui.skin.VistaSkin3D
import com.lucerion.launcher.ui.theme.Bg3
import com.lucerion.launcher.ui.theme.Oro
import com.lucerion.launcher.ui.theme.OroClaro
import com.lucerion.launcher.ui.theme.OroProfundo
import com.lucerion.launcher.ui.theme.TextoSuave

/**
 * Tu personaje en la pantalla de inicio: se ve al entrar, se gira si quieres
 * y se cambia sin dar rodeos. Lo fino (modelo delgado, explicaciones) vive en
 * la sección Skin; aquí solo lo que se usa a diario.
 */
@Composable
fun BloqueSkinInicio(
    alturaVista: Int = 260,
    /** Ocupa el alto disponible en vez de una altura fija: en apaisado evita
     *  que los botones queden fuera de la pantalla. */
    expandible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current

    var bitmap by remember { mutableStateOf<Bitmap?>(RepositorioSkin.cargarBitmap(contexto)) }
    var gira by remember { mutableStateOf(RepositorioSkin.vista3D(contexto)) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val delgada = RepositorioSkin.modeloDelgado(contexto)
    val mostrada = bitmap ?: SkinPorDefecto.bitmap(contexto)

    val elegir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            aviso = RepositorioSkin.importar(contexto, uri)
            if (aviso == null) bitmap = RepositorioSkin.cargarBitmap(contexto)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OroProfundo.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
        color = Bg3,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = if (expandible) {
                Modifier.fillMaxHeight().padding(14.dp)
            } else {
                Modifier.padding(14.dp)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                if (expandible) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth().height(alturaVista.dp)
                },
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> VistaSkin3D(ctx) },
                    update = { vista ->
                        vista.ponerTextura(mostrada)
                        vista.ponerModeloDelgado(delgada)
                        vista.ponerInteractivo(gira)
                    },
                )
            }

            aviso?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonSimple(texto = "3D", activo = gira, modifier = Modifier) {
                    gira = !gira
                    RepositorioSkin.guardarVista3D(contexto, gira)
                }
                BotonSimple(texto = "CAMBIAR SKIN", activo = false) {
                    aviso = null
                    elegir.launch(arrayOf("image/png"))
                }
            }

            Text(
                if (gira) "Arrastra el personaje para girarlo." else "Vista fija de frente.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSuave.copy(alpha = 0.7f),
            )
        }
    }
}

/** Botón compacto: encendido en dorado cuando la opción está activa. */
@Composable
private fun BotonSimple(
    texto: String,
    activo: Boolean,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .border(1.dp, if (activo) Oro else OroProfundo.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = alPulsar)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge,
            color = if (activo) OroClaro else TextoSuave,
        )
    }
}
