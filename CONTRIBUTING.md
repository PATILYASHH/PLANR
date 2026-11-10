# Contributing to PLANR

Thanks for helping! PLANR is an open-source [ExoSynk Labs](https://exosynk.com/exosynk-labs) project,
licensed under the [Apache License 2.0](LICENSE).

## Report bugs and ideas
Open an [issue](https://github.com/PATILYASHH/PLANR/issues). For bugs, include your phone model,
Android version, PLANR version (Settings → About PLANR) and the steps to reproduce.

## Build
Android Studio (latest), JDK 21 and Android SDK 36.

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/
```

## Pull requests
- Keep each PR focused on one change, and add or update unit tests for logic
  (time math, parsing and rules are all pure Kotlin and easy to test).
- Run `./gradlew testDebugUnitTest lintDebug` before opening the PR.
- Database changes need a Room `Migration` (never a destructive fallback) and the
  exported schema in `app/schemas/`.
- PLANR stays offline: no internet permission, no analytics, no accounts.

By contributing you agree that your contribution is licensed under the Apache License 2.0.
