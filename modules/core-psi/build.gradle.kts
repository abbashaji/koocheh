// core-psi: private set intersection SEAM (spec 5.3). No cryptography lives here.
// ASSUMPTIONS (not verified against the repo; see final cell report):
//  - the C-003 model module is reachable as ":modules:core-model"
//  - plugin versions are declared at the root, so none are given here
//  - compileSdk/minSdk and test library versions below are placeholders
// There is deliberately no INTERNET permission and no network dependency.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.koocheh.psi"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        // FakePsiEngine reads BuildConfig.DEBUG (by reflection) to refuse release builds.
        buildConfig = true
    }

    testFixtures {
        enable = true
    }
}

dependencies {
    // InterestTag is part of this module's public API, hence api().
    api(project(":modules:core-model"))

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
