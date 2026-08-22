package com.lucerion.launcher.motor

import com.lucerion.launcher.BuildConfig
import com.tungsten.fclcore.auth.OAuth
import com.tungsten.fclcore.auth.microsoft.MicrosoftAccount
import com.tungsten.fclcore.auth.microsoft.MicrosoftAccountFactory
import com.tungsten.fclcore.auth.microsoft.MicrosoftService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inicio de sesión con cuenta de Microsoft (la cuenta con la que se compra
 * Minecraft Java), por **flujo de código de dispositivo**: la app muestra un
 * código corto, el jugador lo escribe en microsoft.com/link desde donde
 * quiera, y la app detecta sola cuando se autorizó.
 *
 * Se eligió este flujo y no el del navegador porque en móvil no hace falta
 * levantar un servidor local ni volver a la app desde el navegador: funciona
 * incluso autorizando desde otro dispositivo.
 *
 * REQUISITO: un identificador de aplicación de Azure con permiso para la API
 * de Minecraft. Se inyecta al compilar (propiedad `lucerion.oauthClientId`);
 * sin él, la app lo dice claramente en vez de fallar de forma críptica.
 */
object ServicioMicrosoft {

    private val idAplicacion: String = BuildConfig.OAUTH_CLIENT_ID

    fun disponible(): Boolean = idAplicacion.isNotBlank()

    /** Datos que la app enseña mientras espera la autorización. */
    data class Codigo(val codigo: String, val url: String)

    private var alRecibirCodigo: ((Codigo) -> Unit)? = null

    private val callback = object : OAuth.Callback {
        override fun startServer(): OAuth.Session =
            // El flujo de dispositivo no usa servidor de redirección; si el
            // motor lo pidiera, es que se eligió otro flujo por error.
            throw UnsupportedOperationException("Lucerion usa el flujo de código de dispositivo")

        override fun grantDeviceCode(userCode: String, verificationURI: String) {
            alRecibirCodigo?.invoke(Codigo(userCode, verificationURI))
        }

        override fun openBrowser(url: String) {
            // Nada que abrir: el jugador ya tiene el código y la dirección.
        }

        override fun getClientId(): String = idAplicacion

        override fun getClientSecret(): String? = null

        override fun isPublicClient(): Boolean = true
    }

    private val fabrica by lazy { MicrosoftAccountFactory(MicrosoftService(callback)) }

    /**
     * Arranca la sesión. Llama a [alMostrarCodigo] en cuanto Microsoft entrega
     * el código, y no vuelve hasta que el jugador autoriza (o falla).
     */
    suspend fun iniciarSesion(alMostrarCodigo: (Codigo) -> Unit): MicrosoftAccount =
        withContext(Dispatchers.IO) {
            alRecibirCodigo = alMostrarCodigo
            try {
                fabrica.create(null, null, null, null, null)
            } finally {
                alRecibirCodigo = null
            }
        }

    /** Reconstruye la cuenta guardada (con su token de refresco). */
    fun desdeAlmacenamiento(datos: Map<Any, Any>): MicrosoftAccount? =
        runCatching { fabrica.fromStorage(datos) }.getOrNull()
}
