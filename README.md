# NessoDroid

Native Kotlin/Jetpack Compose Android controller for Nesso N1 LoRa exerciser boards. Sends firmware commands over HTTP or Bluetooth Low Energy, shows HTTP device state, and keeps the latest 100 activity entries.

## Build Requirements

### Toolchain

| Component | Requirement |
| --- | --- |
| JDK | JDK 25 for local builds and CI; Java and Kotlin source/bytecode target 17 |
| Gradle | Use the checked-in Gradle 9.5 wrapper; a global Gradle installation is not required |
| Android build | Android Gradle Plugin 9.3.2 and Kotlin/Compose plugin 2.2.10 |
| Android SDK | Platform 35 with accepted licenses; compile and target SDK are 35 |
| Android SDK tools | Build Tools supply `apksigner`; platform-tools supply `adb` for installation and launch |
| Network | Required on the first build for Maven dependencies, missing SDK components, and Robolectric runtimes |

Set `JAVA_HOME` to an installed JDK 25 and either set `ANDROID_HOME` to the Android SDK directory or define `sdk.dir` in an untracked `local.properties` file. The wrapper downloads Gradle automatically. PowerShell examples below target Windows; use `bash ./gradlew` on macOS or Linux.

[Gradle daemon criteria](gradle/gradle-daemon-jvm.properties) pin the build JVM to Java 25 for terminal and IDE builds. This takes precedence over `JAVA_HOME` and `org.gradle.java.home` when selecting the daemon. Gradle must be able to discover an installed JDK 25; the project does not provision a JDK automatically. `JAVA_HOME` still selects the wrapper/client JVM, so setting it to JDK 25 keeps both processes aligned.

The JVM test stack uses Mockito 5.24.0 (Byte Buddy 1.17.7) and Robolectric 4.16.1 (ASM 9.8), which support Java 25 class files. Robolectric 4.17 is deliberately not used: its Bluetooth shadow references `BluetoothDevice.BluetoothAddress`, which breaks the API 28/33 BLE tests. Using JDK 25 does not change the app's Java/Kotlin 17 target or Android compatibility.

### Runtime and test targets

- The app requires Android 8.0 (API 26) or later.
- JVM unit tests and lint require no emulator or physical Android device.
- Native `connectedDebugAndroidTest` requires a connected API 26+ device or emulator; the validated reference AVD uses API 35.
- A Nesso N1 running the matching NessoN1LoRaExerciser firmware is required only for real HTTP/BLE integration testing.

## Build and Run

Open [NessoDroid.code-workspace](NessoDroid.code-workspace). The shared workspace does not override machine-specific JDK or Android SDK paths.

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

With an Android device or emulator connected through ADB:

```powershell
.\gradlew.bat installDebug
adb shell am start -n org.miguelcaldas.nessodroid/.MainActivity
```

On macOS/Linux, run `bash ./gradlew` in place of `.\gradlew.bat`. The debug APK is for development and laboratory use.

### Build types

| Build | Signing requirements | Result |
| --- | --- | --- |
| `assembleDebug` | None; Android uses the machine's standard debug keystore | Debuggable APK for development and tests |
| `assembleRelease` | Production keystore path, store password, alias, and key password | Non-debuggable APK signed for installation and updates |
| `assembleDebugAndroidTest` | None beyond debug signing | Instrumentation APK used with a device or emulator |

Debug and release signatures intentionally differ. With the same application ID, Android will not install one as an update over the other; uninstall the existing variant first or use separate application IDs if side-by-side installation becomes necessary.

## Signed Releases

Installable APKs from tagged signed builds and their SHA-256 checksums are published under [GitHub Releases](https://github.com/MiguelCaldasMSOrg/NessoDroid/releases). Release builds use a stable signing certificate and are not debuggable. An installed debug build uses a different certificate and must be uninstalled before installing a release APK; uninstalling clears its app data.

Signing credentials stay outside the repository. The keystore can be JKS or PKCS12, must contain a private key under the configured alias, and must be readable with the configured passwords. For local builds, configure these Gradle properties in the user-level `~/.gradle/gradle.properties` file or provide environment variables with the same names:

- `RELEASE_KEYSTORE_PATH`: absolute path to the existing private keystore
- `RELEASE_KEYSTORE_PASSWORD`: keystore password
- `RELEASE_KEY_ALIAS`: signing key alias
- `RELEASE_KEY_PASSWORD`: signing key password

Gradle properties take precedence over same-named environment variables. `preReleaseBuild` runs `validateReleaseSigning`, so a release fails before packaging when a value is missing or the keystore path is not a file. The base64 GitHub secret is a CI transport format and is not consumed directly by Gradle.

```powershell
.\gradlew.bat assembleRelease testDebugUnitTest lintRelease --no-configuration-cache
```

The signed APK is generated at `app/build/outputs/apk/release/app-release.apk`. Verify its signature with the Android SDK's `apksigner verify --verbose --print-certs` command before publication. Keep the keystore and credentials backed up securely: subsequent releases must retain the same signing identity to update existing installations. Never commit credentials or publish the keystore as a release asset.

### GitHub Actions signing

The `Signed Android Release` workflow runs on `ubuntu-latest` with JDK 25 and the checked-in Gradle wrapper. It uses these encrypted repository secrets:

- `RELEASE_KEYSTORE_BASE64`: base64 encoding of the complete binary keystore
- `RELEASE_KEYSTORE_PASSWORD`: keystore password
- `RELEASE_KEY_ALIAS`: signing key alias
- `RELEASE_KEY_PASSWORD`: signing key password

The runner also requires the standard GNU `base64` and `sha256sum` tools supplied by GitHub's Ubuntu image. The workflow decodes the keystore into the runner's temporary directory, builds the release APK, verifies its certificate with `apksigner`, generates a SHA-256 checksum, uploads both as a 30-day workflow artifact, and removes the temporary keystore. A manual workflow run builds an artifact without creating a GitHub Release. Pushing a `v*` tag also creates or updates the corresponding GitHub Release.

GitHub encrypted secrets are limited to 48 KB each, so the base64 keystore must fit that limit. Set secrets through `gh secret set` or the repository's Actions settings; never place their values in commands that enter shell history, issue comments, workflow files, or chat.

## Controller

The controller has three views:

- **Control**: HTTP/BLE selection, connection controls, a compact node/battery snapshot, the command editor, and the three most recent responses.
- **Device**: HTTP telemetry grouped into radio link, battery/visuals, network, and operations. Telemetry remains explicitly labeled HTTP even while the BLE transport is selected.
- **Activity**: the latest 100 responses with transport, timestamp, error indication, selectable response text, and a clear-history action.

On Control, choose HTTP or BLE, enter a command, then select Send. Ping, Hello, Status, and the visuals icon shortcuts populate the editor without sending. Icon controls have accessible names and long-press/hover tooltips. The Status preset queues the firmware's `s` command; it does not expose serial command output in the app.

Only one send or status refresh runs at a time. A successful queue acknowledgement clears the submitted command; rejection or a transport error preserves it. Status is refreshed manually and after an accepted HTTP command, not polled continuously. Changing the HTTP address clears the previous device's status.

Each view scrolls independently. Presets, send controls, and telemetry columns adapt to compact windows and large system text; refresh and send dismiss the keyboard. The selected view survives activity recreation, and switching views preserves the command draft. Endpoint, draft, device state, and activity history survive activity recreation through the ViewModel but are not persisted across process death or app restarts. Pending BLE permission actions survive activity recreation; a lost scan result requires another scan.

## HTTP

Connect the Android device to the Nesso recovery network (`Nesso-<node-id>`) or place both devices on the configured station network. The default address is `http://192.168.4.1`.

Addresses accept HTTP or HTTPS; omitting the scheme selects HTTP. Embedded credentials, query strings, and fragments are rejected. Commands are posted as UTF-8 `text/plain` to `/command`. An HTTP success code with `{"status":"queued"}` confirms queue acceptance, not execution. The firmware reports `empty`, `too_long`, and `queue_full` for rejected commands.

Device state is read from `/status` and shows node, radio/profile, peer, Wi-Fi, HTTP/BLE readiness, exercise activity, queue depth, battery/charger, and visual-output state. The response must contain a nonblank string node identifier. Missing or malformed battery values remain unavailable instead of becoming zeroes.

OkHttp requests have 5-second connect/read/write timeouts and a 10-second total deadline per request. Responses are limited to 64 KiB, redirects and connection retries are disabled, and coroutine cancellation cancels the call. Sending a command and then refreshing its status are two separate requests.

Cleartext HTTP is enabled because the recovery access point does not provide TLS. The firmware API has no app-managed authentication; use it only on a trusted laboratory network. HTTPS is usable only if the selected endpoint supports a trusted TLS certificate.

## Bluetooth Low Energy

The BLE connection panel scans for devices named `Nesso-*` for up to 8 seconds. Android 12 and later require Nearby devices permissions; Android 8-11 require fine-location permission and normally enabled location services for scanning. Bluetooth must be enabled. Switching transport from BLE to HTTP stops scanning but does not disconnect an established link.

Select a discovered device to connect. Cancel is available during setup, and Disconnect closes an established link. Send is disabled until the connection is ready. The app checks the service and characteristic capabilities:

| Purpose | UUID |
| --- | --- |
| Service | `7bbf0001-6ba5-4e35-9f1f-8d36a7f34c01` |
| Command | `7bbf0002-6ba5-4e35-9f1f-8d36a7f34c01` |
| Ingress status | `7bbf0003-6ba5-4e35-9f1f-8d36a7f34c01` |

Commands are limited to 1,232 UTF-8 bytes. The app requests MTU 517 and sizes writes to the negotiated payload, capped at 512 bytes. If negotiation fails, it uses the default 20-byte payload. Commands fitting that payload use one write; longer commands use the firmware's `@begin`, `@data`, and `@end` chunk protocol with exact progress acknowledgements.

Connection setup is bounded to 12 seconds, each GATT read/write to 5 seconds, and an entire command to 30 seconds. GATT callbacks are delivered on the main thread and commands are serialized. An interrupted, timed-out, or invalid chunk transfer closes the link to prevent stale callbacks from completing later operations; reconnect before retrying.

Only `queued` is command acceptance. Other responses, including `queue_full`, `ready`, and `cancelled`, do not clear the draft. BLE responses report ingress and queue state, not firmware serial output. Detailed output remains on serial; use HTTP `/status` for structured telemetry.

For either transport, losing an acknowledgement does not prove the command was rejected: it may already be queued. The app does not automatically resend it. Check device state before manually retrying a command with side effects.

## Architecture

- [MainActivity](app/src/main/java/org/miguelcaldas/nessodroid/MainActivity.kt) owns runtime permission requests and lifecycle-aware UI state collection.
- [NessoScreen](app/src/main/java/org/miguelcaldas/nessodroid/ui/NessoScreen.kt) renders immutable state and emits actions; it performs no networking.
- [NessoViewModel](app/src/main/java/org/miguelcaldas/nessodroid/ui/NessoViewModel.kt) owns operation state, the bounded activity log, and BLE cleanup. Injected transports support isolated tests. Cancellation is propagated, and endpoint/draft edits are protected from stale results.
- [HttpNessoClient](app/src/main/java/org/miguelcaldas/nessodroid/transport/HttpNessoClient.kt) handles cancellable HTTP calls; [BleNessoClient](app/src/main/java/org/miguelcaldas/nessodroid/transport/BleNessoClient.kt) owns scanning, connection lifecycle, and serialized GATT operations.
- [BleCommandFramer](app/src/main/java/org/miguelcaldas/nessodroid/transport/BleCommandFramer.kt) and [NessoStatusParser](app/src/main/java/org/miguelcaldas/nessodroid/model/NessoStatus.kt) isolate protocol framing and parsing from UI and Android I/O.

## Validation

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

Equivalent build, unit-test, and lint tasks are available in [VS Code tasks](.vscode/tasks.json). [GitHub Actions](.github/workflows/android.yml) runs the same checks and compiles the native test APK on pushes to `master` and pull requests. It does not start an emulator.

CI uses the Android SDK and preaccepted licenses supplied by GitHub's `ubuntu-latest` runner. The Android Gradle Plugin downloads any missing SDK platforms and Build Tools required by the project; separate SDK setup and installation steps are unnecessary. A self-hosted runner must provide an SDK directory and accepted licenses first.

Tests cover firmware status parsing, malformed telemetry, UTF-8 and MTU limits, legacy and Android 13 GATT APIs, connection/command deadlines, stale callbacks, coroutine cancellation, HTTP error/redirect/retry behavior, response bounds, ViewModel concurrency, and compact large-text Compose controls. They use JUnit, MockWebServer, Mockito, Robolectric, and coroutine test dispatchers; no physical board is needed for these tests.

### Emulator Tests

[MainActivityTest](app/src/androidTest/java/org/miguelcaldas/nessodroid/MainActivityTest.kt) exercises the installed app with an in-emulator HTTP stub, native Compose synchronization, and real Android permission dialogs. Android Test Orchestrator clears this app's data between tests to isolate permission state. Use a dedicated emulator, not a device holding app data you want to keep.

With the test emulator running:

```powershell
$env:ANDROID_SERIAL = 'emulator-5556'
.\gradlew.bat connectedDebugAndroidTest
Remove-Item Env:ANDROID_SERIAL
```

On September 22, 2026, all 73 local tests and all 7 native tests passed on an Android 15/API 35 AVD approximating the Galaxy S24 Ultra display: 1440 x 3120 pixels at 505 dpi, capped at 2 CPU cores and 6144 MiB RAM. The native suite covers HTTP status, accepted and queue-full commands, disconnected BLE sending, activity recreation with the keyboard, landscape at 2x font scale, permission denial, and permission grant after rotation with scan cancellation.

The native HTML report is generated under `app/build/reports/androidTests/connected/debug/`. The test runner may uninstall the app afterward; use `installDebug` to leave it available for manual testing.

These tests do not replace testing on actual Android and Nesso hardware. Before a release, check permission denial and rotation, Bluetooth disabled/unavailable, scanning and reconnects, default and high-MTU links, maximum-length commands, queue-full responses, and Wi-Fi/BLE loss during a command. A Galaxy S24 Ultra-sized Android emulator can validate layout and Android behavior, but not Samsung One UI or a real BLE radio link.

## License

Released under the Unlicense. See [LICENSE](LICENSE).

The bundled [Manrope font](https://github.com/google/fonts/tree/main/ofl/manrope) is distributed under the SIL Open Font License 1.1; its [license](app/src/main/assets/licenses/manrope.txt) is included in the app. Interface fonts are loaded locally, with no font download at runtime. Material icons are supplied by the AndroidX Compose icons library.