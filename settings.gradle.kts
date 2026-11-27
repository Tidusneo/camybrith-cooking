pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Toque"
include(":core", ":commun", ":pilote", ":app", ":rescue-engine")
// Emulator test bench (spec 007): test tool, never published.
include(":bench:fake-lidl")
