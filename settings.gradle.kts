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

// The part all three Obsi apps share (github.com/Virus7976/obsi-core). GitHub builds use the copy
// pinned in core/. On this PC the folder next to this repo is used, so one edit reaches all apps.
include(":core")
val sharedCore = file("../obsi-core")
project(":core").projectDir =
    if (System.getenv("CI") == null && sharedCore.resolve("build.gradle.kts").exists()) sharedCore else file("core")
