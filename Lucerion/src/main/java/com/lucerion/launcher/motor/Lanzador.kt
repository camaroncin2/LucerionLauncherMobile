package com.lucerion.launcher.motor

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.lucerion.launcher.ui.juego.JuegoActivity
import com.tungsten.fclauncher.bridge.FCLBridge
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.auth.authlibinjector.SimpleAuthlibInjectorArtifactProvider
import com.tungsten.fclcore.auth.offline.OfflineAccountFactory
import com.tungsten.fclcore.download.MaintainTask
import com.tungsten.fclcore.game.LaunchOptions
import com.tungsten.fclcore.launch.DefaultLauncher
import com.mio.JavaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import java.nio.file.Paths

/**
 * Capa C: de "todo instalado" a la JVM corriendo Minecraft.
 *
 * Cuenta libre con el apodo del jugador (el servidor autentica con AuthMod),
 * renderer Zink sobre Turnip (la config validada en la Fase 0) y conexión
 * directa a mc.cretania.net: JUGAR significa jugar.
 */
object Lanzador {

    const val SERVIDOR = "mc.cretania.net"

    /** Memoria para la JVM: 40 % de la RAM del equipo, entre 2 y 6 GB. */
    private fun memoriaMb(actividad: Activity): Int {
        val am = actividad.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalMb = (info.totalMem / 1048576L).toInt()
        return (totalMb * 40 / 100).coerceIn(2048, 6144)
    }

    /** Copia los jars auxiliares que DefaultLauncher exige (los desempaqueta FCL en su app). */
    private fun prepararJarsAuxiliares(actividad: Activity) {
        val pares = listOf(
            "game/MioLibPatcher.jar" to FCLPath.LIB_PATCHER_PATH,
            "game/MioLaunchWrapper.jar" to FCLPath.MIO_LAUNCH_WRAPPER,
        )
        for ((asset, destino) in pares) {
            val archivo = File(destino)
            if (!archivo.isFile) {
                archivo.parentFile?.mkdirs()
                actividad.assets.open(asset).use { entrada ->
                    archivo.outputStream().use { salida -> entrada.copyTo(salida) }
                }
            }
        }
    }

    /**
     * Perfil de rendimiento móvil: se fusiona UNA sola vez sobre options.txt
     * (el marcador evita repetirlo) — después el jugador manda. Valores
     * elegidos para Adreno + Zink: vsync activo (las franjas), gráficos
     * rápidos, 8 chunks, 60 FPS de tope y detalles caros apagados.
     */
    private fun aplicarPerfilRendimiento(dirEjecucion: File) {
        val marcador = File(dirEjecucion, "lucerion-perfil-rendimiento.txt")
        if (marcador.isFile) return
        val archivo = File(dirEjecucion, "options.txt")
        val lineas = if (archivo.isFile) archivo.readLines().toMutableList() else mutableListOf()
        val valores = linkedMapOf(
            "enableVsync" to "true",        // sincronía de presentación
            "graphicsMode" to "0",          // gráficos rápidos
            "renderDistance" to "8",
            "simulationDistance" to "8",
            "maxFps" to "60",               // el panel rinde ~45; 60 de tope sobra
            "particles" to "1",             // partículas reducidas
            "entityShadows" to "false",
            "renderClouds" to "\"fast\"",
            "mipmapLevels" to "2",
            "biomeBlendRadius" to "1",
            "entityDistanceScaling" to "0.75",
            "ao" to "true",                 // luz suave: barata con Sodium y se nota
        )
        for ((clave, valor) in valores) {
            val idx = lineas.indexOfFirst { it.startsWith("$clave:") }
            if (idx >= 0) lineas[idx] = "$clave:$valor" else lineas.add("$clave:$valor")
        }
        archivo.writeText(lineas.joinToString(System.lineSeparator()))
        marcador.writeText("v1")
    }

    /**
     * Construye el puente del juego y abre la Activity de la superficie.
     * La JVM arranca cuando la superficie está lista (JuegoActivity.execute).
     */
    suspend fun lanzar(actividad: Activity, dirInstancia: File, apodo: String) =
        withContext(Dispatchers.IO) {
            MotorLucerion.cargarRutas(actividad)
            prepararJarsAuxiliares(actividad)

            // Vsync forzado contra las franjas negras verticales de Zink.
            // OJO: addCommonEnv de FCLauncher escribe FORCE_VSYNC=false DESPUES
            // de cualquier Os.setenv (por eso nunca surtio efecto, ni en la
            // Fase 0 con FCL). El unico canal que gana es el env personalizado
            // (prefs "launcher"/"env"), que se aplica al final.
            // FIFO en el WSI de Mesa: fuerza presentacion sincronizada al
            // vblank en la capa Vulkan (Turnip) — remata las franjas que el
            // vsync de GL redujo pero no elimino.
            actividad.getSharedPreferences("launcher", Context.MODE_PRIVATE)
                .edit().putString("env", "FORCE_VSYNC=true\nMESA_VK_WSI_PRESENT_MODE=fifo").apply()

            val repo = InstaladorJuego.repositorio(dirInstancia)
            val version = MaintainTask.maintain(
                repo, repo.getResolvedVersion(InstaladorJuego.NOMBRE_VERSION),
            )

            // Cuenta libre: mismo UUID determinista que usa Minecraft offline.
            val cuenta = OfflineAccountFactory(
                SimpleAuthlibInjectorArtifactProvider(Paths.get(FCLPath.AUTHLIB_INJECTOR_PATH)),
            ).create(apodo, OfflineAccountFactory.getUUIDFromUserName(apodo))
            val credenciales = cuenta.playOffline()

            val memoria = memoriaMb(actividad)
            val metrics = actividad.resources.displayMetrics
            val ancho = maxOf(metrics.widthPixels, metrics.heightPixels)
            val alto = minOf(metrics.widthPixels, metrics.heightPixels)

            val opciones = LaunchOptions.Builder()
                .setGameDir(dirInstancia)
                .setJava(JavaManager.getSuitableJavaVersion(version))
                .setRenderer(MotorLucerion.rendererZink)
                .setVkDriverSystem(false)
                .setPojavBigCore(false)
                .setWidth(ancho)
                .setHeight(alto)
                .setMaxMemory(memoria)
                .setMinMemory(1024)
                .setVersionName(InstaladorJuego.NOMBRE_VERSION)
                .setProfileName("Lucerion")
                .setServerIp(SERVIDOR) // JUGAR = entrar a Cretania
                // El motor exige el UUID en las opciones (getConfigurations hace
                // replace sobre el sin validar). 32 hex sin guiones = "custom".
                .setUUid(credenciales.uuid.toString().replace("-", ""))
                .create()

            val lanzador = DefaultLauncher(actividad, repo, version, credenciales, opciones)
            version.libraries.forEach { libreria ->
                val nombre = libreria.name ?: return@forEach
                if (nombre.startsWith("net.java.dev.jna:jna:")) lanzador.setJnaVersion(libreria.version)
                if (nombre.startsWith("org.lwjgl:lwjgl:")) lanzador.setLwjglVersion(libreria.version)
            }

            val puente: FCLBridge = lanzador.launch()

            // El resto va en el hilo principal: CallbackBridge se inicializa con
            // el Choreographer de Android (exige un Looper), y FCL hace este
            // mismo bloque en Schedulers.androidUIThread().
            withContext(Dispatchers.Main) {
                puente.setGameDir(repo.getRunDirectory(version.id).absolutePath)
                puente.setJava("21")
                puente.setRenderer(MotorLucerion.rendererZink.name)
                puente.setScaleFactor(1.0)
                puente.setHasTouchController(false)
                CallbackBridge.nativeSetUseInputStackQueue(version.arguments.isPresent)

                // options.txt por defecto (idioma, controles sanos) la primera vez;
            // la Activity le escribira la resolucion exacta encima.
            val dirEjecucion = repo.getRunDirectory(version.id)
            val opcionesTxt = File(dirEjecucion, "options.txt")
            if (!opcionesTxt.isFile) {
                runCatching {
                    actividad.assets.open("options.txt").use { entrada ->
                        opcionesTxt.outputStream().use { salida -> entrada.copyTo(salida) }
                    }
                }
            }
            aplicarPerfilRendimiento(dirEjecucion)

            JuegoActivity.dirJuego = dirEjecucion
            JuegoActivity.puente = puente
                actividad.startActivity(Intent(actividad, JuegoActivity::class.java))
            }
        }
}
