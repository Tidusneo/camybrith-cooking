import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Moteur de la commande `camybrith` du module de secours (spec 007, research.md D16) : le code de
// core/backup empaqueté en APK, jamais installé, lancé en root par app_process depuis
// modules/secours/camybrith. Il marche donc même si l'interface est cassée ou désinstallée.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.toque.secours"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.toque.secours"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "1"
    }

    signingConfigs {
        create("toque") {
            storeFile = file("../app/signature/toque.p12")
            storeType = "PKCS12"
            storePassword = "toque-public"
            keyAlias = "toque"
            keyPassword = "toque-public"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("toque") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("toque")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":core"))
}

// Copie l'APK dans le module Magisk (modules/secours/camybrith.apk), prêt à empaqueter.
tasks.register<Copy>("versModule") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/moteur-secours-release.apk"))
    into(rootProject.file("modules/secours"))
    rename { "camybrith.apk" }
}
