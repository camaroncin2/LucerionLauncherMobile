package com.lucerion.launcher

import com.tungsten.fcl.FCLApplication

/**
 * FCLCore y CallbackBridge piden la Activity en curso a través de
 * FCLApplication.getCurrentActivity() (p. ej. los procesadores del instalador
 * de NeoForge). Extenderla es el contrato de integración con el motor.
 */
class AplicacionLucerion : FCLApplication()
