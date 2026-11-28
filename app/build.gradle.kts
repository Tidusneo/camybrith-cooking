import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.toque"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.toque"
        minSdk = 23          // Android 6.0, celui du Monsieur Cuisine Connect
        targetSdk = 28
        // Pour publier une mise à jour : augmenter les deux valeurs puis pousser sur GitHub
        // (la version est publiée automatiquement, voir .github/workflows/release.yml).
        // Version prudente (tout interprété, -Pprudent=true) : numéro au-dessus de la rapide, pour
        // pouvoir l'installer par-dessus en cas d'anomalie du compilateur.
        val prudent = (project.findProperty("prudent") as String?) == "true"
        versionCode = if (prudent) 68 else 67
        versionName = if (prudent) "1.4.16-prudente" else "1.4.16-rapide"
        manifestPlaceholders["safeMode"] = prudent.toString()
    }

    signingConfigs {
        // Clé du projet, publique exprès : toutes les compilations (PC, GitHub) portent la même
        // signature, donc chaque mise à jour s'installe par-dessus la précédente.
        create("toque") {
            storeFile = file("signature/toque.p12")
            storeType = "PKCS12"
            storePassword = "toque-public"
            keyAlias = "toque"
            keyPassword = "toque-public"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("toque")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("toque")
        }
    }

    // Éditeur de recettes (Ktor) : ses dépendances emportent des fichiers inutiles sur Android
    // (bibliothèques natives Windows et Mac de jansi, sondes de débogage des coroutines).
    packaging {
        resources.excludes += listOf(
            "org/fusesource/**",
            "DebugProbesKt.bin",
            "META-INF/*.version",
            "META-INF/INDEX.LIST",
            "META-INF/io.netty.versions.properties",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")

    // Captures d'écran de l'interface sans le robot (Robolectric, rendu natif) : ./gradlew :app:testDebugUnitTest
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("captures.dir", layout.buildDirectory.dir("captures").get().asFile.path)
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
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
    implementation(project(":commun"))
    implementation(project(":editeur"))
    // QR code de l'éditeur de recettes (MIT, sans dépendance, ~30 Ko).
    implementation("io.nayuki:qrcodegen:1.8.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
