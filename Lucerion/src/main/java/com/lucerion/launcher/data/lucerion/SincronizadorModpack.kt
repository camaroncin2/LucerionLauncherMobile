package com.lucerion.launcher.data.lucerion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Sincroniza la carpeta local de mods con lo que el backend declara.
 *
 * Patrón probado en el resto del ecosistema Lucerion: verificación SHA-1
 * (lo que ya está y coincide no se vuelve a bajar — reanudar es gratis),
 * descarga a .part con rename final (nunca queda un jar a medias con nombre
 * bueno), 4 descargas en paralelo, y limpieza de lo que sobra (mods que el
 * pack ya no trae, versiones viejas).
 */
class SincronizadorModpack(private val dirInstancia: File) {

    /**
     * Poda móvil validada en la Fase 0 (ver lucerion-mobile/FASE0-VALIDACION.md
     * del repo privado). La familia Sodium + Iris SE QUEDAN: iris lo exige
     * colorwheel (contenido del servidor) y sodium lo exige iris en runtime.
     * Cuando exista el pack `cretania-mobile` en el panel, esta lista muere y
     * la poda pasa a ser un dato del servidor.
     */
    private val exclusionesMovil = setOf(
        "Ixeris-4.5.2+1.21.1-neoforge.jar",
        "particlerain-4.0.0-beta.10+1.21.1-neoforge.jar",
        "minecraft-cursor-neoforge-3.11.3+1.21.1.jar",
        "forgematica-0.4.1+mc1.21.1.jar",
        "entity_model_features-3.2.4-1.21-neoforge.jar",
        "entity_texture_features_1.21-neoforge-7.1.jar",
        "balm-neoforge-1.21.1-21.0.56.jar", // duplicado: el pack trae la 21.0.62
    )

    sealed class Estado {
        data object Inactivo : Estado()
        data object Analizando : Estado()
        data class ListoParaDescargar(
            val version: String,
            val archivosFaltantes: Int,
            val bytesFaltantes: Long,
            val archivosTotales: Int,
        ) : Estado()

        data class Descargando(
            val archivosListos: Int,
            val archivosTotales: Int,
            val bytesListos: Long,
            val bytesTotales: Long,
            val archivoActual: String,
            val velocidadBps: Long,
        ) : Estado()

        data class Completo(val version: String, val mods: Int) : Estado()
        data class Fallo(val motivo: String) : Estado()
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Inactivo)
    val estado: StateFlow<Estado> = _estado

    private val dirMods = File(dirInstancia, "mods")

    private data class Plan(
        val info: ClienteLucerion.InstallInfo,
        val deseados: List<ClienteLucerion.Archivo>,
        val faltantes: List<ClienteLucerion.Archivo>,
    )

    private var plan: Plan? = null

    /** Consulta el backend y calcula qué falta. Barato: solo lee cabeceras y hashes locales. */
    suspend fun analizar() {
        _estado.value = Estado.Analizando
        try {
            val info = ClienteLucerion.obtenerInstallInfo()
            val deseados = info.files
                .filter { it.kind == "mod" }
                .filter { File(it.file).name !in exclusionesMovil }

            val faltantes = withContext(Dispatchers.IO) {
                dirMods.mkdirs()
                deseados.filter { !estaVerificado(it) }
            }
            plan = Plan(info, deseados, faltantes)
            _estado.value = if (faltantes.isEmpty()) {
                limpiarSobrantes(deseados)
                Estado.Completo(info.version, deseados.size)
            } else {
                Estado.ListoParaDescargar(
                    version = info.version,
                    archivosFaltantes = faltantes.size,
                    bytesFaltantes = faltantes.sumOf { it.size },
                    archivosTotales = deseados.size,
                )
            }
        } catch (e: Exception) {
            _estado.value = Estado.Fallo(e.message ?: "No se pudo consultar el servidor de modpacks")
        }
    }

    /** Descarga lo que falta. Cancelable: al cancelar la corrutina no queda basura con nombre bueno. */
    suspend fun descargar() {
        val p = plan ?: run { analizar(); plan } ?: return
        if (p.faltantes.isEmpty()) return

        val bytesTotales = p.faltantes.sumOf { it.size }
        val bytesListos = AtomicLong(0)
        val archivosListos = AtomicLong(0)
        val velocidad = AtomicLong(0)
        val archivoActual = java.util.concurrent.atomic.AtomicReference("")

        fun emitir() {
            _estado.value = Estado.Descargando(
                archivosListos.get().toInt(), p.faltantes.size,
                bytesListos.get(), bytesTotales,
                archivoActual.get(), velocidad.get(),
            )
        }

        try {
            coroutineScope {
                // Velocimetro: delta de bytes por segundo, emitido en cada tick.
                val ticker = async(Dispatchers.IO) {
                    var previo = 0L
                    while (true) {
                        kotlinx.coroutines.delay(1000)
                        val ahora = bytesListos.get()
                        velocidad.set(ahora - previo)
                        previo = ahora
                        emitir()
                    }
                }
                val semaforo = Semaphore(4)
                p.faltantes.map { archivo ->
                    async(Dispatchers.IO) {
                        semaforo.withPermit {
                            archivoActual.set(File(archivo.file).name)
                            emitir()
                            descargarUno(archivo) { delta ->
                                bytesListos.addAndGet(delta)
                            }
                            archivosListos.incrementAndGet()
                            emitir()
                        }
                    }
                }.awaitAll()
                ticker.cancel()
            }
            withContext(Dispatchers.IO) { limpiarSobrantes(p.deseados) }
            _estado.value = Estado.Completo(p.info.version, p.deseados.size)
        } catch (e: kotlinx.coroutines.CancellationException) {
            _estado.value = Estado.Inactivo
            throw e
        } catch (e: Exception) {
            _estado.value = Estado.Fallo(e.message ?: "Fallo la descarga")
        }
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private fun destinoDe(archivo: ClienteLucerion.Archivo) = File(dirMods, File(archivo.file).name)

    private fun estaVerificado(archivo: ClienteLucerion.Archivo): Boolean {
        val destino = destinoDe(archivo)
        if (!destino.isFile) return false
        val esperado = archivo.sha1?.lowercase() ?: return destino.length() == archivo.size
        return sha1De(destino) == esperado
    }

    private fun descargarUno(archivo: ClienteLucerion.Archivo, alProgresar: (Long) -> Unit) {
        val destino = destinoDe(archivo)
        val temporal = File(dirMods, destino.name + ".part")
        var intento = 0
        while (true) {
            intento++
            try {
                val conexion = URL(ClienteLucerion.urlDescarga(archivo))
                    .openConnection() as HttpURLConnection
                conexion.connectTimeout = 15_000
                conexion.readTimeout = 60_000
                try {
                    if (conexion.responseCode != 200) error("HTTP ${conexion.responseCode}")
                    conexion.inputStream.use { entrada ->
                        temporal.outputStream().use { salida ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val leidos = entrada.read(buffer)
                                if (leidos < 0) break
                                salida.write(buffer, 0, leidos)
                                alProgresar(leidos.toLong())
                            }
                        }
                    }
                } finally {
                    conexion.disconnect()
                }
                val esperado = archivo.sha1?.lowercase()
                if (esperado != null && sha1De(temporal) != esperado) {
                    error("La verificación SHA-1 no coincide")
                }
                if (destino.exists()) destino.delete()
                if (!temporal.renameTo(destino)) {
                    temporal.copyTo(destino, overwrite = true)
                    temporal.delete()
                }
                return
            } catch (e: Exception) {
                temporal.delete()
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (intento >= 3) throw IllegalStateException("${destino.name}: ${e.message}")
                Thread.sleep(1200L * intento)
            }
        }
    }

    /** Borra jars que el pack ya no declara (mods quitados o versiones viejas) y .part huérfanos. */
    private fun limpiarSobrantes(deseados: List<ClienteLucerion.Archivo>) {
        val nombresValidos = deseados.map { File(it.file).name }.toSet()
        dirMods.listFiles()?.forEach { f ->
            if (f.isFile && (f.name.endsWith(".part") || (f.name.endsWith(".jar") && f.name !in nombresValidos))) {
                f.delete()
            }
        }
    }

    private fun sha1De(archivo: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        archivo.inputStream().use { entrada ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val leidos = entrada.read(buffer)
                if (leidos < 0) break
                md.update(buffer, 0, leidos)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
