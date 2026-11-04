plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":modules:core-model"))
    // [UNVERIFIED] lazysodium-android ships as an AAR. Resolving it from this JVM module,
    // and running its native library in unit tests, has not been checked. Tests in this
    // module use a JDK-backed CryptoBackend and never load the native library.
    implementation(libs.lazysodium.android)
    implementation(libs.jna)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
