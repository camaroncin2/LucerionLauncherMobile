//
// Created by Tungsten on 2022/10/11.
//

#include "fcl_internal.h"

#include <android/native_window_jni.h>
#include <jni.h>
#include <android/log.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>

struct FCLInternal *fcl;

/*
 * OJO — colision de simbolos con el resto del motor:
 *
 * environ/environ.c exporta un constructor llamado EXACTAMENTE igual
 * ("env_init"), pero que inicializa OTRO entorno (el de Pojav). Con
 * visibilidad por defecto, la llamada se resuelve contra la primera
 * definicion que encuentre el enlazador: si gana la ajena, este puntero se
 * queda en nulo y el proceso muere con SIGSEGV en plena carga de la
 * biblioteca, sin dejar rastro en Java (es un fallo nativo).
 *
 * Por eso la inicializacion vive en una funcion ESTATICA con nombre propio:
 * asi no puede ser suplantada, no depende del orden de carga y se puede
 * invocar tantas veces como haga falta.
 */
static void fcl_preparar_entorno(void) {
    if (fcl != NULL) return;

    char* strptr_env = getenv("FCL_ENVIRON");
    if (strptr_env != NULL) {
        fcl = (void*) strtoul(strptr_env, NULL, 0x10);
        __android_log_print(ANDROID_LOG_INFO, "Environ", "Entorno FCL existente: %s", strptr_env);
    }
    if (fcl == NULL) {
        __android_log_print(ANDROID_LOG_INFO, "Environ", "Sin entorno FCL, creando...");
        fcl = malloc(sizeof(struct FCLInternal));
        if (fcl == NULL) abort();
        memset(fcl, 0, sizeof(struct FCLInternal));
        char* nuevo = NULL;
        if (asprintf(&nuevo, "%p", fcl) == -1) abort();
        setenv("FCL_ENVIRON", nuevo, 1);
        free(nuevo);
    }
    __android_log_print(ANDROID_LOG_INFO, "Environ", "FCL environ = %p", fcl);
}

__attribute__((constructor)) static void fcl_env_init(void) {
    fcl_preparar_entorno();
}

JNIEXPORT void JNICALL Java_com_tungsten_fclauncher_bridge_FCLBridge_setFCLBridge(JNIEnv *env, jobject thiz, jobject fcl_bridge) {
    fcl->object_FCLBridge = (jclass)(*env)->NewGlobalRef(env, thiz);
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    // Red de seguridad: el constructor pudo no haber dejado el entorno listo
    // (colision de simbolos, cargas repetidas). Es idempotente.
    fcl_preparar_entorno();
    if (fcl->android_jvm == NULL) {
        fcl->android_jvm = vm;
        JNIEnv* env = 0;
        jint result = (*fcl->android_jvm)->AttachCurrentThread(fcl->android_jvm, &env, 0);
        if (result != JNI_OK || env == 0) {
            FCL_INTERNAL_LOG("Failed to attach thread to JavaVM.");
            abort();
        }
        jclass class_FCLBridge = (*env)->FindClass(env, "com/tungsten/fclauncher/bridge/FCLBridge");
        if (class_FCLBridge == 0) {
            FCL_INTERNAL_LOG("Failed to find class: com/tungsten/fclauncher/bridge/FCLBridge.");
            abort();
        }
        fcl->class_FCLBridge = (jclass)(*env)->NewGlobalRef(env, class_FCLBridge);
    }
    return JNI_VERSION_1_2;
}