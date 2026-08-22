package com.lucerion.launcher

import com.tungsten.fcl.FCLApplication

/**
 * FCLCore y CallbackBridge piden la Activity en curso a través de
 * FCLApplication.getCurrentActivity() (p. ej. los procesadores del instalador
 * de NeoForge). Extenderla es el contrato de integración con el motor.
 */
class AplicacionLucerion : FCLApplication() {

    override fun onCreate() {
        super.onCreate()
        // El motor lee este contexto global para CUALQUIER peticion de red
        // (arma con el la cadena de identificacion del cliente). Solo se
        // asignaba al preparar el juego, asi que todo lo que usa la red
        // ANTES — el inicio de sesion con Microsoft — moria con un fallo
        // incomprensible. Se fija al arrancar la app, que es cuando toca.
        com.tungsten.fclauncher.utils.FCLPath.CONTEXT = this
        // Caja negra: cualquier fallo que nadie capture queda escrito en un
        // archivo propio. El proceso del juego muere sin dejar rastro en el
        // registro del sistema si este ya roto.
        com.lucerion.launcher.motor.RegistroFallos.instalarRedDeSeguridad(this)
    }
}
