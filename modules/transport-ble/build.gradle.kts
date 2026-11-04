plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "org.koocheh.transport"
    compileSdk = 34

    defaultConfig {
        // Coded PHY query APIs need API 26.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Assumed module path for C-003 (org.koocheh.model).
    implementation(project(":modules:model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
