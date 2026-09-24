# GitHub Copilot Instructions

- [x] Verify project instructions
  This file exists at `.github/copilot-instructions.md`.
- [x] Clarify requirements
  Native Kotlin/Compose Android controller with HTTP and BLE transports, package `org.miguelcaldas.nessodroid`, API 35, minimum API 26, Java 17, tests, documentation, GitHub, and a dedicated VS Code workspace.
- [x] Scaffold project
  Gradle Kotlin DSL, Gradle 9.5 wrapper, Android resources, manifest, and Compose entry point are present. Project evaluation passes under JDK 21.
- [x] Customize project
  HTTP command/status transport, bounded BLE GATT transport, long-command framing, ViewModel state, permission flow, controller UI, activity log, and tests are implemented.
- [x] Install required extensions
  No extension installation was required. The workspace recommends Java, Gradle, and Kotlin support.
- [x] Compile project
  `assembleDebug`, `testDebugUnitTest`, and `lintDebug` pass; Android lint reports no issues.
- [x] Create and run tasks
  `.vscode/tasks.json` defines build, test, and lint tasks; their Gradle commands pass.
- [x] Launch project
  Deferred until an emulator or Android device is selected; this setup request only requires opening the dedicated workspace.
- [x] Ensure documentation is complete
  `README.md`, Unlicense `LICENSE`, workspace configuration, and build/run instructions are current.

## Project Rules

- Keep HTTP and BLE operations asynchronous and deadline-bounded; never block the Android main thread.
- BLE command responses are ingress/queue statuses, not the firmware serial output stream.
- Preserve API 35, minimum API 26, and Java/Kotlin 17 unless a migration is explicitly requested.
- Use `master` as the default branch.
- Keep production keystores and credentials outside the repository; never print signing secret values in logs.
- Verify production APK signatures with `apksigner` before publication.
- Run `assembleDebug`, `testDebugUnitTest`, and `lintDebug` before committing transport or UI changes.
