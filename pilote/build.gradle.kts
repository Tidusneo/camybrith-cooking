import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pilote moteur : appli à part, interprétée (vmSafeMode), que l'interface commande par messages.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.toque.pilote"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.toque.pilote"
        minSdk = 23
        targetSdk = 28
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        // Même clé que l'interface : la permission « signature » ne laisse que l'interface piloter.
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

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    // Assets de l'interface (fichiers de langue : messages de sécurité traduits).
    sourceSets["main"].assets.srcDirs("../app/src/main/assets")

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
    implementation(project(":commun"))
}
