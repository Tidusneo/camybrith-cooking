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
include(":core", ":commun", ":pilote", ":app", ":moteur-secours")
// Banc d'essai sur émulateur (spec 007) : outil de test, jamais publié.
include(":banc:fausse-lidl")
