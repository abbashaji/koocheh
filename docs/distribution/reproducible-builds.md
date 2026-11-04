# Reproducible builds: how a second person checks

**Status: not achieved.** The settings in `gradle/reproducible.gradle.kts` are
all marked [UNVERIFIED]. Nobody has yet built the app twice on different
machines and compared the results. Treat this page as a procedure to try, not
as proof that it works.

## What the check is for

If two people build the same source and get the same bytes, then a published
APK that differs from their build points to tampering or to something added
after the source was reviewed.

## How to rebuild and compare

1. Get the exact source: check out the release tag named in the release notes
   (not a branch).
2. Match the toolchain recorded in the release notes: Gradle version, Android
   Gradle Plugin version, JDK version and Android build tools version. A
   different version can change the output.
3. Build the release variant **unsigned** on a clean checkout, with the
   reproducibility script applied.
4. Build it a second time on a different machine, or in a clean container,
   and keep both results.
5. Compare the unsigned APKs byte for byte, for example with `cmp` or by
   comparing SHA-256 hashes of both files.
6. To compare against the published APK, compare everything except the
   signature block (for example by unpacking both and comparing the file
   contents), because only the maintainers' key can produce their signature.

Record the result, including any differences, and share it with the
maintainers.

## Limits of this check

- A match shows that this source produced this APK with this toolchain. It
  does not show that the source is free of mistakes or malicious code; only
  review of the source can do that.
- It does not prove who signed the APK. Use the signing fingerprint check in
  `verification-guide.md` for that.
- Differences can come from toolchain versions, build paths or embedded
  metadata, and not only from tampering. A mismatch needs investigation; it
  is not proof of an attack.
- Until two independent matching builds have been recorded, the project makes
  no claim that its builds are reproducible.
