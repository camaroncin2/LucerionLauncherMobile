package com.lucerion.launcher.data.lucerion

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente del backend de Lucerion (el mismo que alimenta el launcher de
 * escritorio). Un solo endpoint da todo lo que la sincronización necesita:
 * install-info trae versión, lista de archivos, tamaños y SHA-1.
 *
 * Las URL de descarga se reconstruyen SIEMPRE contra BASE tomando solo la
 * ruta: así un manifest comprometido no puede redirigir descargas a otro
 * host, y el día que el backend tenga dominio con TLS se cambia una constante.
 */
object ClienteLucerion {

    // HTTP plano a propósito (IP:puerto): documentado en el AndroidManifest.
    const val BASE = "http://103.195.100.133:3100"
    const val PACK_ID = "cretania"

    private val gson = Gson()

    data class InstallInfo(
        @SerializedName("modpackId") val id: String,
        val name: String,
        val version: String,
        val minecraft: String,
        val loader: String,
        val files: List<Archivo>,
    )

    data class Archivo(
        val kind: String,
        val file: String,
        val url: String,
        val size: Long,
        val sha1: String?,
    )

    suspend fun obtenerInstallInfo(): InstallInfo = withContext(Dispatchers.IO) {
        val conexion = URL("$BASE/api/public/modpack/$PACK_ID/install-info")
            .openConnection() as HttpURLConnection
        conexion.connectTimeout = 15_000
        conexion.readTimeout = 30_000
        try {
            if (conexion.responseCode != 200) {
                error("El servidor de modpacks respondió ${conexion.responseCode}")
            }
            conexion.inputStream.bufferedReader().use { lector ->
                gson.fromJson(lector, InstallInfo::class.java)
            }
        } finally {
            conexion.disconnect()
        }
    }

    /** Ruta de descarga saneada: solo el path del manifest, contra nuestra BASE. */
    fun urlDescarga(archivo: Archivo): String {
        val ruta = URL(archivo.url).path
        require(ruta.startsWith("/files/")) { "Ruta inesperada en el manifest: $ruta" }
        return BASE + ruta
    }
}
