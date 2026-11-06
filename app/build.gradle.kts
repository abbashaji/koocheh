plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

android {
    namespace = "org.koocheh"
    compileSdk = 34

    defaultConfig {
        applicationId = prop("koocheh.applicationId")
        minSdk = prop("koocheh.minSdk").toInt()
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        resValue("string", "app_label", prop("koocheh.appLabel"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            ndk { debugSymbolLevel = "NONE" }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

apply(from = "../gradle/reproducible.gradle.kts")

dependencies {
    implementation(project(":modules:core-model"))
    implementation(project(":modules:core-crypto"))
    implementation(project(":modules:core-psi"))
    implementation(project(":modules:storage"))
    implementation(project(":modules:transport-ble"))
    implementation(project(":modules:disclosure"))
    implementation(project(":modules:familiar"))
    implementation(project(":modules:security"))
    implementation(project(":modules:invites"))
    implementation(project(":modules:chat"))
    implementation(project(":modules:pilot"))

    // Insecure FakePsiEngine is never packaged in release builds.
    debugImplementation(testFixtures(project(":modules:core-psi")))

    testImplementation(testFixtures(project(":modules:core-psi")))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
