plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.koocheh.invites"
    compileSdk = 34

    defaultConfig {
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
    // C-003 lives at :modules:core-model (org.koocheh.model.ContextId, Clock).
    implementation(project(":modules:core-model"))
    implementation(libs.zxing.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
