# Scrcpy Studio

Scrcpy Studio is an Android Studio plugin for mirroring and controlling
[`scrcpy`](https://github.com/Genymobile/scrcpy) devices in a tool window. It
provides a Running Devices-style device list, one session tab per device, and
an external-window fallback when the embedded protocol client cannot start.

## Requirements

- Android Studio Quail 2 (`2026.1.2`) or a compatible `261.*` build.
- Java 17 for plugin development.
- `scrcpy` and `adb` installed locally.
- USB debugging or wireless debugging enabled on the Android device.

The plugin does not download or bundle scrcpy or adb. Configure the scrcpy
executable in **Settings | Tools | Scrcpy Studio**. The plugin expects the
matching `scrcpy-server` file from the same scrcpy distribution; it is pushed
to the device for the embedded protocol session and removed by scrcpy's normal
cleanup flow. The plugin can discover adb from the same directory, the Android
SDK platform-tools directory, or `PATH`. The **Test configuration** button runs
`scrcpy --version` and `adb devices`.

The local Windows scrcpy distribution can be configured by selecting either
`scrcpy.exe` or its containing directory.

## Development

Use the Gradle wrapper from this directory:

```text
gradlew.bat test
gradlew.bat runIde
gradlew.bat buildPlugin
gradlew.bat verifyPlugin
```

Inside the development IDE, open **Tools | Scrcpy Studio**, select a connected
device, and click **Start mirroring**. The plugin starts a matching
`scrcpy-server` over an ADB reverse tunnel, decodes the H.264 stream in-process,
and paints it in the session tab. Mouse touch events and Escape/back are sent
back through scrcpy's control socket. If the server, tunnel, or decoder cannot
be started, the plugin falls back to a managed external scrcpy window.

The protocol used by scrcpy is internal and version-coupled. The plugin reads
the installed client version and starts the sibling server with that exact
version. The current embedded client targets scrcpy 4.x and H.264 video; other
versions or codecs use the external fallback.

## Recording status

The MP4 recording controls and command path are present, but recording stop and
container finalization are intentionally not validated in this implementation
pass. Do not treat recording as production-ready until that follow-up test is
completed.

## Architecture

The plugin uses Kotlin, IntelliJ services, MVVM, repositories, and `StateFlow`.
Process execution uses IntelliJ's `GeneralCommandLine` and
`OSProcessHandler`; user-provided paths and arguments are passed as separate
process arguments and are never assembled into a shell command. The embedded
video path uses a reverse ADB tunnel, scrcpy's 12-byte video packet framing,
JCodec's pure-Java H.264 decoder, and a Swing renderer.

## Licensing

Scrcpy is distributed under the Apache License 2.0. This plugin expects an
external scrcpy installation and does not redistribute its binaries. Review
the licenses of any external scrcpy/adb distribution before redistributing
the plugin.
