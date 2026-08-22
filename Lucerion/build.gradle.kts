import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ═════════════════════════════════════════════════════════════════════════════
// Lucerion — la aplicación propia del launcher.
//
// Este módulo es 100 % de Lucerion Studios: pantallas, flujos y estética.
// El motor (JVM en Android, renderers, lanzamiento del juego) entra como
// dependencia: FCLCore (port de HMCL) y FCLauncher (capa nativa). Ver
// docs/ESPECIFICACION.md.
// ═════════════════════════════════════════════════════════════════════════════

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lucerion.launcher"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.lucerion.launcher"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        // Mismo nombre de buildType que usan los módulos del motor y el CI del
        // upstream (assemblefordebug): así la matriz de GitHub Actions compila
        // este módulo sin tocar nada, y el variant matching contra FCLCore y
        // FCLauncher resuelve solo.
        create("fordebug") {
            initWith(getByName("debug"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.VERSION_NAME se muestra en la Home
    }

    sourceSets {
        getByName("main") {
            // Los runtimes del motor (JREs, LWJGL, cacio, JNA) y los assets que
            // FCLCore lee del classpath viven en el modulo FCL. Se comparten por
            // sourceSet en vez de duplicar ~200 MB en el repo: mismo APK final.
            assets.srcDir("../FCL/src/main/assets")
            // jreAssets filtrados: solo el JRE 21 (MC 1.21 no usa otro).
            // Los cuatro JRE completos sumarian ~180 MB de APK.
            assets.srcDir(layout.buildDirectory.dir("jreAssetsFiltrados"))
        }
    }

    packaging {
        jniLibs {
            // El motor nativo lo exige (mismos motivos que el módulo FCL):
            // las .so del JVM se cargan por ruta, sin compresión.
            useLegacyPackaging = true
            pickFirsts += listOf("**/libbytehook.so")
        }
    }
}

// Copia al build solo el JRE que el juego necesita (patron FilterJreAssets de FCL).
val filtrarJreAssets = tasks.register<Sync>("filtrarJreAssets") {
    from("../FCL/src/main/jreAssets")
    include("app_runtime/java/jre21/**")
    into(layout.buildDirectory.dir("jreAssetsFiltrados"))
}
tasks.named("preBuild") { dependsOn(filtrarJreAssets) }

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // ── El motor, como dependencia ──────────────────────────────────────────
    implementation(project(":FCLCore"))      // versiones, loaders, cuentas, descargas
    implementation(project(":FCLauncher"))   // JVM por JNI, renderers, superficie de juego

    // ── UI propia (Jetpack Compose) ─────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.gson) // parseo del manifest de Lucerion

    // ── Nativos del motor (renderers y audio; mismos .aar que usa FCL) ──────
    implementation(files("../FCL/libs/kopper-zink-release.aar"))      // Zink (GL 4.6 sobre Vulkan)
    implementation(files("../FCL/libs/NG-GL4ES-release.aar"))
    implementation(files("../FCL/libs/openal-soft-release.aar"))
    implementation(files("../FCL/libs/spirv-cross-natives.aar"))
    implementation(files("../FCL/libs/lwjgl-3.3.3-natives-release.aar"))
    implementation(files("../FCL/libs/lwjgl-3.4.1-natives-release.aar"))

    // ── Descompresion de los runtimes (tar.xz de los JRE) ───────────────────
    implementation(libs.commons.compress)
    implementation(libs.xz)
}
