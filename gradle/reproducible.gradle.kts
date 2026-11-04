// gradle/reproducible.gradle.kts
//
// Apply to the app module (done later by the integration cell, not here):
//     apply(from = rootProject.file("gradle/reproducible.gradle.kts"))
//
// STATUS: nothing in this file has been built or measured. Every setting is
// marked [UNVERIFIED] until two independent builds on different machines have
// been compared byte for byte and found identical. Reproducibility has NOT
// been achieved or claimed.

// [UNVERIFIED] Archive settings: drop file timestamps and use a fixed file
// order so the same inputs give the same archive bytes. Applies to every
// archive task (jar, zip, aar and similar) in this module.
tasks.withType<AbstractArchiveTask>().configureEach {
    // [UNVERIFIED] Do not store file modification times in archives.
    isPreserveFileTimestamps = false
    // [UNVERIFIED] Sort archive entries in a stable order.
    isReproducibleFileOrder = true
}

// [UNVERIFIED] Android-specific settings. Run only when the Android
// application plugin is present. The exact property names depend on the
// Android Gradle Plugin version in use and must be checked against it.
pluginManager.withPlugin("com.android.application") {
    extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
        // [UNVERIFIED] Remove the dependency metadata block that the Android
        // Gradle Plugin adds to the APK; it is signed with a key only the
        // Play Store holds and is not needed for outside distribution.
        dependenciesInfo {
            includeInApk = false
            includeInBundle = false
        }

        buildTypes.configureEach {
            // [UNVERIFIED] Do not embed version control (git) information
            // in the build output; it can differ between checkouts.
            vcsInfo.include = false
        }
    }
}

// [UNVERIFIED] Rules for the rest of the build, to be enforced by review and
// not by this script:
//   - Do not put the build time, hostname, username or absolute paths into
//     BuildConfig, resources or manifest values.
//   - Pin the Gradle version, Android Gradle Plugin version, JDK version and
//     Android build tools version, and record them with each release.
//   - Use locked dependency versions (no dynamic versions such as "1.+").
//   - Signing happens after the build; the signature block differs between
//     signers, so compare unsigned APK contents, not signed files. See
//     docs/distribution/reproducible-builds.md for the limits of this.
