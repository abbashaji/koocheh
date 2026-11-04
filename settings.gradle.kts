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

rootProject.name = "koocheh"

// Include :app only once it exists.
if (file("app/build.gradle.kts").exists()) {
    include(":app")
}

// Auto-include every modules/<name> directory that has a build.gradle.kts
// as project ":modules:<name>", so later cells add modules without editing this file.
file("modules").listFiles()
    ?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    ?.sortedBy { it.name }
    ?.forEach { dir ->
        include(":modules:${dir.name}")
        project(":modules:${dir.name}").projectDir = dir
    }
