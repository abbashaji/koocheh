// Familiar-strangers module: pure Kotlin/JVM, no network code.
// Storage is reached through the EncounterStore port, because :modules:storage is an Android library.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":modules:core-model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
