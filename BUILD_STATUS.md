# Build and verification status

## Source-level checks completed in this environment

- Compiled the platform-independent organization rule, conflict resolver, and scan-diff code with the installed Kotlin compiler.
- Ran a standalone smoke test for extension matching, keyword matching, unique-name conflicts, skip behavior, initial scan baseline, new-file detection, and invalid-rule validation. Result: `Domain smoke tests passed`.
- Parsed the Gradle version catalog, Android XML resources/manifest, and GitHub Actions YAML successfully.
- Checked the bootstrap shell script with `sh -n`.
- Reviewed storage operations for content-URI/SAF use, conflict policies, copy digest verification, cross-provider move fallback, ZIP path traversal prevention, extraction limits, and partial-output cleanup.

## Checks not completed

A full Android Gradle build, `ktlintCheck`, Android lint, JUnit via Gradle, instrumented tests, API 29 device testing, and APK generation have **not** been completed in this environment. The Gradle distribution download failed because DNS/network access to `services.gradle.org` is unavailable. There is no Android SDK platform installed here to compile the Android modules locally.

No successful build and no installable APK are being claimed. The project includes `.github/workflows/build-apk.yml`, which attempts the format/check/test/lint/build tasks and uploads `app/build/outputs/apk/debug/app-debug.apk` as `file-navigator-lite-debug-apk`. That CI run is the next required verification step.

## Known verification risks

- Kotlin/Compose API and Android lint compatibility still need confirmation by the actual CI build.
- Storage providers differ; especially validate move fallback, persisted grant revocation, rename/delete semantics, and ZIP output on-device.
- Undo depends on the provider retaining the relevant URIs and supporting the reverse operation.
- Automatic scan inspects direct children of enabled source folders only and depends on WorkManager scheduling.
