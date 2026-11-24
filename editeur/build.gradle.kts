import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Éditeur de recettes web : l'interface (Compose HTML, compilée en JavaScript) et le serveur
// (Ktor) qui la sert sur le réseau local. Sans Android : l'appli fournit un EditorHost.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

val ktor = "3.1.3"

// Compose ne sert qu'à l'interface web : le serveur (jvm) n'en a pas besoin.
composeCompiler {
    targetKotlinPlatforms.set(setOf(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.js))
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    js(IR) {
        browser {
            commonWebpackConfig { outputFileName = "editeur.js" }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        }
        jvmMain.dependencies {
            implementation(project(":core"))
            implementation("io.ktor:ktor-server-core:$ktor")
            implementation("io.ktor:ktor-server-cio:$ktor")
            implementation("io.ktor:ktor-server-content-negotiation:$ktor")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("junit:junit:4.13.2")
            implementation("io.ktor:ktor-server-test-host:$ktor")
        }
        jsMain.dependencies {
            implementation(compose.html.core)
            implementation(compose.runtime)
        }
    }
}

// L'interface compilée (editeur.js, index.html, styles) part dans les ressources du serveur,
// sous web/ : l'appli Android l'emporte ainsi dans son APK.
val webBundle = tasks.register<Sync>("webBundle") {
    val production = providers.gradleProperty("editeurDev").map { it != "true" }.getOrElse(true)
    from(tasks.named(if (production) "jsBrowserDistribution" else "jsBrowserDevelopmentExecutableDistribution"))
    into(layout.buildDirectory.dir("generated/web/web"))
    exclude("*.map")
}
kotlin.sourceSets.named("jvmMain") {
    resources.srcDir(webBundle.map { it.destinationDir.parentFile })
}
