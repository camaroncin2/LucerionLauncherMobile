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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import kotlinx.coroutines.launch

/**
 * Elección del apodo — lo único imprescindible para jugar.
 *
 * La validación corre en vivo y el error habla en palabras del jugador
 * (principio "ninguna opción sin explicar"). Debajo, el inicio de sesión con
 * Microsoft: opcional y explicado — Cretania se juega igual con solo el apodo.
 */
@Composable
fun CuentaScreen(
    apodoInicial: String?,
    alGuardar: (String) -> Unit,
    alVolver: () -> Unit,
    /** Por qué se llegó aquí (p. ej. venir de JUGAR sin apodo elegido). */
    motivo: String? = null,
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
            // Con el teclado abierto en apaisado quedan ~165 dp: sin esto el
            // campo del apodo podia quedar debajo del teclado.
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cabecera con vuelta atrás
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.cuenta_volver),
                style = MaterialTheme.typography.titleMedium,
                color = OroClaro,
                modifier = Modifier
                    .clickable(onClick = alVolver)
                    .padding(vertical = 14.dp, horizontal = 8.dp),
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
            motivo?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = OroClaro,
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = stringResource(R.string.cuenta_explicacion),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSuave,
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = apodo,
                // 16 es el maximo real (RepositorioCuenta.validarApodo): dejar teclear
                // mas caracteres solo servia para que el campo se pusiera en rojo.
                onValueChange = { apodo = it.take(16) },
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

            BloqueMicrosoft()
        }
    }
}

/**
 * Cuenta de Microsoft: la que compró Minecraft Java. Es OPCIONAL — Cretania
 * autentica con su propio sistema y el apodo basta para entrar — pero permite
 * jugar con tu identidad real y tu skin oficial.
 *
 * El flujo es de código de dispositivo: la app muestra un código corto y la
 * dirección; el jugador lo autoriza desde donde quiera (incluso otro
 * dispositivo) y la app lo detecta sola. La contraseña se escribe SOLO en la
 * página de Microsoft: Lucerion nunca la ve.
 */
@Composable
private fun BloqueMicrosoft() {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val ambito = androidx.compose.runtime.rememberCoroutineScope()

    var sesionGuardada by remember {
        mutableStateOf(RepositorioCuenta.leerCuentaMicrosoft(contexto) != null)
    }
    var codigo by remember { mutableStateOf<com.lucerion.launcher.motor.ServicioMicrosoft.Codigo?>(null) }
    var esperando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OroProfundo.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
        color = Bg3,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Cuenta de Microsoft (opcional)",
                style = MaterialTheme.typography.titleMedium,
                color = OroClaro,
            )
            Text(
                "Para entrar a Cretania alcanza con el apodo. Inicia sesión con Microsoft " +
                    "si quieres jugar con tu cuenta oficial de Minecraft y su skin. Tu " +
                    "contraseña se escribe solo en la página de Microsoft: el launcher nunca la ve.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSuave,
            )

            when {
                !com.lucerion.launcher.motor.ServicioMicrosoft.disponible() -> Text(
                    "Esta opción todavía no está habilitada en esta versión del launcher: " +
                        "requiere que el estudio registre la aplicación ante Microsoft.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextoSuave.copy(alpha = 0.75f),
                )

                sesionGuardada -> {
                    Text(
                        "Sesión iniciada. Se usará al entrar al juego.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OroClaro,
                    )
                    BotonBordeCuenta("CERRAR SESIÓN") {
                        RepositorioCuenta.olvidarCuentaMicrosoft(contexto)
                        sesionGuardada = false
                        mensaje = null
                    }
                }

                codigo != null -> {
                    val c = codigo!!
                    Text(
                        "1) Entra en ${c.url}\n2) Escribe este código:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave,
                    )
                    Text(
                        c.codigo,
                        style = MaterialTheme.typography.displayLarge,
                        color = OroClaro,
                    )
                    Text(
                        "Esperando a que autorices… puedes hacerlo desde este teléfono o " +
                            "desde otro dispositivo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSuave.copy(alpha = 0.75f),
                    )
                }

                else -> BotonBordeCuenta(if (esperando) "CONECTANDO…" else "INICIAR SESIÓN CON MICROSOFT") {
                    if (esperando) return@BotonBordeCuenta
                    esperando = true
                    mensaje = null
                    ambito.launch {
                        try {
                            val cuenta = com.lucerion.launcher.motor.ServicioMicrosoft
                                .iniciarSesion { recibido -> codigo = recibido }
                            RepositorioCuenta.guardarCuentaMicrosoft(contexto, cuenta.toStorage())
                            sesionGuardada = true
                            mensaje = null
                        } catch (e: Exception) {
                            mensaje = explicarFalloDeSesion(e)
                        } finally {
                            esperando = false
                            codigo = null
                        }
                    }
                }
            }

            mensaje?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun BotonBordeCuenta(texto: String, alPulsar: () -> Unit) {
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

/**
 * Traduce el fallo de inicio de sesión a algo accionable.
 *
 * Antes se mostraba el mensaje crudo de la excepción: un volcado en inglés con
 * la URL y el código HTTP. Decía qué había pasado a quien ya sabía leerlo, y
 * nada a todos los demás. Cada uno de estos casos se resuelve de forma
 * distinta, y ninguno se resuelve volviendo a pulsar el botón.
 */
private fun explicarFalloDeSesion(e: Throwable): String {
    val codigo = generateSequence<Throwable>(e) { it.cause }
        .mapNotNull { t ->
            Regex("response code: (\\d+)").find(t.message ?: "")?.groupValues?.get(1)?.toIntOrNull()
        }
        .firstOrNull()

    return when (codigo) {
        429 ->
            "Minecraft está rechazando peticiones por exceso (429). No es un " +
                "problema de tu cuenta ni de la contraseña: hay que esperar. " +
                "Deja pasar un rato largo antes de volver a intentarlo — cada " +
                "intento durante el bloqueo lo alarga. Mientras tanto puedes " +
                "jugar con tu apodo, que es lo que usa Cretania."
        403 ->
            "Minecraft rechazó la aplicación (403). Suele significar que el " +
                "registro de Lucerion todavía está en revisión por Microsoft. " +
                "No hay nada que hacer desde aquí; entra con tu apodo mientras " +
                "tanto."
        401 ->
            "La sesión caducó o fue revocada (401). Vuelve a iniciar sesión " +
                "desde cero."
        404 ->
            "Esa cuenta de Microsoft no tiene Minecraft: Java Edition. Si lo " +
                "compraste con otra cuenta, inicia sesión con esa."
        else -> {
            val raiz = generateSequence<Throwable>(e) { it.cause }.last()
            val detalle = raiz.message ?: raiz.javaClass.simpleName
            if (detalle.contains("Unable to resolve host", true) ||
                detalle.contains("timeout", true) ||
                detalle.contains("Failed to connect", true)
            ) {
                "No se pudo contactar con Microsoft. Revisa tu conexión e " +
                    "inténtalo de nuevo."
            } else {
                "No se pudo iniciar sesión: " + detalle
            }
        }
    }
}
