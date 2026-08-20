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
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.VERSION_NAME se muestra en la Home
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
}
