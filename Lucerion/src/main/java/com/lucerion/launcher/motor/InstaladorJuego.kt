package com.lucerion.launcher.motor

import com.lucerion.launcher.data.lucerion.ClienteLucerion
import com.tungsten.fclcore.download.DefaultCacheRepository
import com.tungsten.fclcore.download.DefaultDependencyManager
import com.tungsten.fclcore.download.MojangDownloadProvider
import com.tungsten.fclcore.game.DefaultGameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths

/**
 * Capa B: instalar Minecraft + NeoForge dentro de la instancia, con la
 * maquinaria de FCLCore (la misma de HMCL). El instalador de NeoForge corre
 * sus procesadores en el servicio :jvm declarado en el manifest.
 *
 * La versión instalada se llama "cretania" y su carpeta de ejecución es la
 * propia instancia — así el mods/ que ya sincroniza el launcher es el que el
 * juego carga, sin copiar nada.
 */
object InstaladorJuego {

    const val NOMBRE_VERSION = "cretania"

    sealed class Estado {
        data object SinInstalar : Estado()
        data class Instalando(
            val detalle: String,
            val bytesDescargados: Long = 0,
            val velocidadBps: Long = 0,
            val tareasHechas: Int = 0,
            val tareasConocidas: Int = 0,
        ) : Estado()
        data object Instalado : Estado()
        data class Fallo(val motivo: String) : Estado()
    }

    private val _estado = MutableStateFlow<Estado>(Estado.SinInstalar)
    val estado: StateFlow<Estado> = _estado

    fun repositorio(dirInstancia: File): DefaultGameRepository =
        DefaultGameRepository(dirInstancia).also { it.refreshVersions() }

    /** ¿La versión ya está instalada? (el json de la versión existe y resuelve) */
    fun estaInstalado(dirInstancia: File): Boolean {
        val json = File(dirInstancia, "versions/$NOMBRE_VERSION/$NOMBRE_VERSION.json")
        if (!json.isFile) return false
        _estado.value = Estado.Instalado
        return true
    }

    /**
     * Instala Minecraft (la versión que declare el pack) + NeoForge (ídem).
     * Descarga grande la primera vez: cliente, librerías y assets del juego.
     */
    suspend fun instalar(dirInstancia: File, cacheDir: File) = withContext(Dispatchers.IO) {
        try {
            _estado.value = Estado.Instalando("Consultando versión del modpack")
            val pack = ClienteLucerion.obtenerPack()

            _estado.value = Estado.Instalando("Preparando el repositorio del juego")
            val repo = repositorio(dirInstancia)
            val proveedor = MojangDownloadProvider()
            // La instancia global la fija MotorLucerion.cargarRutas; reutilizarla
            // evita dos indices de cache pisandose.
            val dependencias = DefaultDependencyManager(
                repo, proveedor,
                com.tungsten.fclcore.util.CacheRepository.getInstance() as DefaultCacheRepository,
            )

            _estado.value = Estado.Instalando("Buscando NeoForge ${pack.loaderVersion} para ${pack.minecraft}")
            val listaNeoForge = proveedor.getVersionListById("neoforge")
            listaNeoForge.loadAsync(pack.minecraft).get()
            val remoto = listaNeoForge.getVersion(pack.minecraft, pack.loaderVersion)
                .orElseThrow { IllegalStateException("NeoForge ${pack.loaderVersion} no existe para ${pack.minecraft}") }

            val detalle = "Descargando Minecraft ${pack.minecraft} + NeoForge"
            _estado.value = Estado.Instalando(detalle)
            val tarea = dependencias.gameBuilder()
                .name(NOMBRE_VERSION)
                .gameVersion(pack.minecraft)
                .version(remoto)
                .buildAsync()

            // Progreso real: bytes y velocidad los publica FetchTask cada segundo
            // (speedEvent); el conteo de tareas sale del TaskListener. El total de
            // tareas crece a medida que el motor descubre trabajo — por eso el
            // porcentaje se etiqueta como aproximado en la UI.
            val bytes = java.util.concurrent.atomic.AtomicLong(0)
            val hechas = java.util.concurrent.atomic.AtomicInteger(0)
            lateinit var ejecutor: com.tungsten.fclcore.task.TaskExecutor
            val oyenteVelocidad = java.util.function.Consumer<com.tungsten.fclcore.task.FetchTask.SpeedEvent> { ev ->
                val total = bytes.addAndGet(ev.speed.toLong())
                _estado.value = Estado.Instalando(
                    detalle, total, ev.speed.toLong(), hechas.get(), ejecutor.taskCount,
                )
            }
            com.tungsten.fclcore.task.FetchTask.speedEvent
                .channel(com.tungsten.fclcore.task.FetchTask.SpeedEvent::class.java)
                .registerWeak(oyenteVelocidad)

            ejecutor = tarea.executor(object : com.tungsten.fclcore.task.TaskListener() {
                override fun onFinished(t: com.tungsten.fclcore.task.Task<*>) {
                    hechas.incrementAndGet()
                }
            })
            val ok = ejecutor.test()
            if (!ok) {
                val motivo = generateSequence<Throwable>(ejecutor.exception) { it.cause }
                    .lastOrNull()?.message
                error(motivo ?: "La instalación no terminó bien; revisa la conexión y reintenta")
            }

            repo.refreshVersions()
            if (!estaInstalado(dirInstancia)) error("La versión no quedó registrada tras instalar")
            _estado.value = Estado.Instalado
        } catch (e: Exception) {
            _estado.value = Estado.Fallo(e.message ?: e.javaClass.simpleName)
        }
    }
}
