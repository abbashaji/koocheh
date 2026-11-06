// Familiar-strangers module: pure Kotlin/JVM, no network code.
// [UNVERIFIED] Assumes :modules:model is a JVM module (it was not present on this branch).
// Storage is reached through the EncounterStore port, because :modules:storage is an Android library.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":modules:model"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
