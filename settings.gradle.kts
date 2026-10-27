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
        // NewPipe Extractor is published on JitPack only. Nothing else is taken from here.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.TeamNewPipe") ; includeGroup("com.github.TeamNewPipe.NewPipeExtractor"); includeGroup("com.github.MuntashirAkon"); includeGroup("com.github.MuntashirAkon.spake2-java") }
        }
    }
}

rootProject.name = "Marginalia"
include(":app")
