package com.lucerion.launcher.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lucerion.launcher.R
import com.lucerion.launcher.data.RepositorioCuenta
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
 * Elección del apodo — lo único imprescindible para jugar.
 *
 * La validación corre en vivo y el error habla en palabras del jugador
 * (principio "ninguna opción sin explicar"). La cuenta Microsoft aparece
 * como tarjeta informativa: existe, es opcional, y todavía no está — las
 * tres cosas dichas de frente.
 */
@Composable
fun CuentaScreen(
    apodoInicial: String?,
    alGuardar: (String) -> Unit,
    alVolver: () -> Unit,
) {
    BackHandler(onBack = alVolver)

    var apodo by remember { mutableStateOf(apodoInicial ?: "") }
    // El error solo aparece tras el primer intento de escribir: un formulario
    // que regaña antes de que toques nada es hostil.
    val error = if (apodo.isEmpty()) null else RepositorioCuenta.validarApodo(apodo)
    val puedeGuardar = apodo.isNotEmpty() && error == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg2, Bg)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cabecera con vuelta atrás
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.cuenta_volver),
                style = MaterialTheme.typography.titleMedium,
                color = OroClaro,
                modifier = Modifier.clickable(onClick = alVolver),
            )
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.cuenta_titulo),
            style = MaterialTheme.typography.headlineMedium,
            color = OroClaro,
        )
        Spacer(Modifier.height(24.dp))

        // En horizontal el formulario no debe estirarse a lo ancho de todo:
        // una columna contenida se lee mejor.
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.cuenta_explicacion),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSuave,
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = apodo,
                onValueChange = { apodo = it.take(20) },
                label = { Text(stringResource(R.string.cuenta_apodo_label)) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                supportingText = {
                    Text(
                        text = error ?: stringResource(R.string.cuenta_reglas),
                        color = if (error != null) MaterialTheme.colorScheme.error else TextoSuave,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Oro,
                    unfocusedBorderColor = OroProfundo,
                    focusedLabelColor = OroClaro,
                    unfocusedLabelColor = TextoSuave,
                    cursorColor = OroClaro,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            // Botón guardar: mismo lenguaje visual que JUGAR, apagado si no procede.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(2.dp, if (puedeGuardar) OroProfundo else Bg3, RoundedCornerShape(12.dp))
                    .padding(3.dp)
                    .background(
                        brush = if (puedeGuardar) Brush.verticalGradient(listOf(AmbarCta1, AmbarCta2))
                        else Brush.verticalGradient(listOf(Bg3, Bg3)),
                        shape = RoundedCornerShape(9.dp),
                    )
                    .clickable(enabled = puedeGuardar) { alGuardar(apodo.trim()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.cuenta_guardar),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (puedeGuardar) Bg else TextoSuave.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(28.dp))

            // Microsoft: presente y explicada, deshabilitada sin disimulo.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OroProfundo.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                color = Bg3.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cuenta_microsoft_titulo),
                        style = MaterialTheme.typography.titleMedium,
                        color = OroClaro.copy(alpha = 0.7f),
                    )
                    Text(
                        text = stringResource(R.string.cuenta_microsoft_detalle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave,
                    )
                }
            }
        }
    }
}
