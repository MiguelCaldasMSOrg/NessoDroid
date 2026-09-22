# NessoDroid

Native Android controller for Nesso N1 LoRa exerciser boards. NessoDroid sends firmware commands over HTTP or Bluetooth Low Energy and displays the responses exposed by each transport.

## Requirements

- Android 8.0 (API 26) or later
- JDK 21 for local builds; source and bytecode target Java 17
- Android SDK 35
- A Nesso N1 running the matching exerciser firmware

## Build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
.\gradlew.bat assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## HTTP

Connect the Android device to the Nesso recovery network (`Nesso-<node-id>`) or place both devices on the configured station network. The default address is `http://192.168.4.1`.

Commands are posted as `text/plain` to `/command`. Accepted commands return the firmware queue status. Device state is read from `/status` and includes radio, peer, battery, charger, visual-output, and exercise state.

Cleartext HTTP is enabled because the Nesso laboratory recovery access point does not provide TLS. Use it only on a trusted network.

## Bluetooth Low Energy

The BLE tab scans for devices named `Nesso-*`, requests the Android nearby-device permissions, and connects to the firmware service:

| Purpose | UUID |
| --- | --- |
| Service | `7bbf0001-6ba5-4e35-9f1f-8d36a7f34c01` |
| Command | `7bbf0002-6ba5-4e35-9f1f-8d36a7f34c01` |
| Ingress status | `7bbf0003-6ba5-4e35-9f1f-8d36a7f34c01` |

Commands up to 512 UTF-8 bytes use one characteristic write. Longer commands use the firmware's `@begin`, `@data`, and `@end` chunk protocol. BLE responses report command validation and queue acceptance; detailed command output remains available over serial, while HTTP `/status` provides structured device state.

## Validation

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

Unit tests cover current firmware status parsing, nullable battery fields, UTF-8 byte limits, and long BLE command reconstruction.

## License

Released under the Unlicense. See `LICENSE`.