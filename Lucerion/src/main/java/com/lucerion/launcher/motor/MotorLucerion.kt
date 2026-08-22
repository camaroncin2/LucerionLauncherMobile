package com.lucerion.launcher.motor

import android.app.Activity
import com.mio.data.Renderer
import com.tungsten.fclauncher.plugins.DriverPlugin
import com.tungsten.fclauncher.plugins.RendererPlugin
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.download.DefaultCacheRepository
import com.tungsten.fclcore.util.CacheRepository
import com.tungsten.fclcore.util.Logging
import com.tungsten.fclcore.util.io.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths

/**
 * Arranque del motor: rutas, runtimes y plugins, en el orden exacto que usa
 * FCL (SplashActivity → RuntimeFragment → enterLauncher), adaptado a
 * almacenamiento propio de la app — sin pedir "acceso a todos los archivos".
 *
 * Los runtimes (JRE 21, LWJGL, puentes AWT, JNA) viajan en el APK como assets
 * y se desempaquetan UNA vez; las siguientes aperturas los validan por
 * versión y siguen de largo.
 */
object MotorLucerion {

    sealed class Estado {
        data object SinPreparar : Estado()
        data class Preparando(val paso: String) : Estado()
        data object Listo : Estado()
        data class Fallo(val motivo: String) : Estado()
    }

    private val _estado = MutableStateFlow<Estado>(Estado.SinPreparar)
    val estado: StateFlow<Estado> = _estado

    /** Config de referencia validada en dispositivo (Fase 0): Kopper Zink sobre Turnip. */
    val rendererZink: Renderer by lazy {
        Renderer(
            name = "Zink",
            des = "Kopper Zink (OpenGL 4.6)",
            glName = "libglxshim.so",
            eglName = "libEGL_mesa.so",
            path = "", // vacío ⇒ resolver desde nativeLibraryDir
            boatEnv = null,
            pojavEnv = null,
            id = Renderer.ID_ZINK,
        )
    }

    /** Rutas del motor + redirección de las que FCL clava en /storage/emulated/0/FCL. */
    fun cargarRutas(activity: Activity) {
        FCLPath.loadPaths(activity)
        val base = activity.getExternalFilesDir(null)!!
        FCLPath.LOG_DIR = File(base, "log").apply { mkdirs() }.absolutePath
        FCLPath.SHARED_COMMON_DIR = File(base, ".minecraft-compartido").apply { mkdirs() }.absolutePath
        FCLPath.CONTROLLER_DIR = File(base, "controles").apply { mkdirs() }.absolutePath
        FCLPath.SHARE_DIR = File(base, "compartido").apply { mkdirs() }.absolutePath
        Logging.start(Paths.get(FCLPath.LOG_DIR))

        // Cache global de descargas del motor. FetchTask usa la INSTANCIA
        // GLOBAL (CacheRepository.getInstance()) y su indice de ETags solo se
        // crea en changeDirectory(): sin esto, toda descarga del motor muere
        // con NPE (FCL lo hace en Settings.java:50).
        val cache = DefaultCacheRepository(Paths.get(File(activity.cacheDir, "motor-cache").absolutePath))
        cache.changeDirectory(Paths.get(File(activity.cacheDir, "motor-cache").absolutePath))
        CacheRepository.setInstance(cache)
    }

    /** ¿Los runtimes ya están instalados y al día? Barato: compara archivos de versión. */
    fun estaListo(activity: Activity): Boolean {
        cargarRutas(activity)
        return try {
            RuntimeUtils.isLatest(FCLPath.LWJGL_DIR, "/assets/app_runtime/lwjgl") &&
                RuntimeUtils.isLatest(FCLPath.CACIOCAVALLO_8_DIR, "/assets/app_runtime/caciocavallo") &&
                RuntimeUtils.isLatest(FCLPath.CACIOCAVALLO_17_DIR, "/assets/app_runtime/caciocavallo17") &&
                RuntimeUtils.isLatest(FCLPath.JAVA_21_PATH, "/assets/app_runtime/java/jre21") &&
                RuntimeUtils.isLatest(FCLPath.JNA_PATH, "/assets/app_runtime/jna").also {
                    if (it) inicializarPlugins(activity)
                }
        } catch (e: Exception) {
            false
        }
    }

    /** Desempaqueta los runtimes del APK. Idempotente; ~1 minuto la primera vez. */
    suspend fun preparar(activity: Activity) = withContext(Dispatchers.IO) {
        try {
            cargarRutas(activity)

            _estado.value = Estado.Preparando("Componentes gráficos (LWJGL)")
            if (!RuntimeUtils.isLatest(FCLPath.LWJGL_DIR, "/assets/app_runtime/lwjgl")) {
                RuntimeUtils.install(activity, FCLPath.LWJGL_DIR, "app_runtime/lwjgl")
            }

            _estado.value = Estado.Preparando("Puentes gráficos de Java (AWT)")
            if (!RuntimeUtils.isLatest(FCLPath.CACIOCAVALLO_8_DIR, "/assets/app_runtime/caciocavallo")) {
                RuntimeUtils.install(activity, FCLPath.CACIOCAVALLO_8_DIR, "app_runtime/caciocavallo")
            }
            if (!RuntimeUtils.isLatest(FCLPath.CACIOCAVALLO_17_DIR, "/assets/app_runtime/caciocavallo17")) {
                RuntimeUtils.install(activity, FCLPath.CACIOCAVALLO_17_DIR, "app_runtime/caciocavallo17")
            }

            _estado.value = Estado.Preparando("Java 21 (el que usa Minecraft 1.21)")
            if (!RuntimeUtils.isLatest(FCLPath.JAVA_21_PATH, "/assets/app_runtime/java/jre21")) {
                RuntimeUtils.installJava(activity, FCLPath.JAVA_21_PATH, "app_runtime/java/jre21")
            }

            _estado.value = Estado.Preparando("Puente nativo (JNA)")
            if (!RuntimeUtils.isLatest(FCLPath.JNA_PATH, "/assets/app_runtime/jna")) {
                RuntimeUtils.installJna(activity, FCLPath.JNA_PATH, "app_runtime/jna")
            }

            // DNS para la JVM del juego (la resolución del sistema no llega dentro).
            FileUtils.writeText(
                File(FCLPath.JAVA_PATH, "resolv.conf"),
                "nameserver 1.1.1.1\nnameserver 1.0.0.1\n",
            )

            _estado.value = Estado.Preparando("Renderers y drivers")
            inicializarPlugins(activity)

            _estado.value = Estado.Listo
        } catch (e: Exception) {
            _estado.value = Estado.Fallo(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun inicializarPlugins(activity: Activity) {
        DriverPlugin.init(activity)           // Turnip por defecto (nativeLibraryDir)
        RendererPlugin.init(activity)
        com.mio.JavaManager.init()            // detecta los JRE instalados
        _estado.value = Estado.Listo
    }
}
