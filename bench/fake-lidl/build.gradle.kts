import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Test bench (spec 007, research.md D11): FAKE Lidl app for the emulator, same package name as
// the Smart's app, a home activity and a "motor service" that does nothing. Never published,
// never installed on the robot.
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
        versionName = "bench"
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
