import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Banc d'essai (spec 007, research.md D11) : FAUSSE appli Lidl pour l'émulateur, même nom de
// paquet que l'appli du Smart, une activité d'accueil et un « service moteur » qui ne fait rien.
// Jamais publiée, jamais installée sur le robot.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tecpal.device.mc30"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tecpal.device.mc30"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "banc"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}
