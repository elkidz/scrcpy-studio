# Scrcpy Studio

Scrcpy Studio is an IntelliJ Platform plugin for mirroring and controlling
[`scrcpy`](https://github.com/Genymobile/scrcpy) devices in a tool window. It
provides Running Devices-style tabs, device controls, and an external-window
fallback when the embedded protocol client cannot start.

## Requirements

- IntelliJ IDEA 2025.1+ (`251+`) or Android Studio 2025.1+ (`251+`).
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
device tab, and use the compact icon toolbar to start mirroring, rotate the
display, capture screenshots, send Android navigation keys, record, and switch
between the embedded view and a normal external scrcpy window. Each connected
device receives its own tab. The plugin starts a matching `scrcpy-server` over
an ADB reverse tunnel, decodes the H.264 stream in-process, and paints it in
the tab. Mouse touch events and navigation controls are sent back through
scrcpy's control socket when the embedded mode is active. Screenshots use
`adb exec-out screencap -p`.

The Automation settings can start mirroring for every newly connected device
and reconnect sessions when a device returns. The first device scan establishes
a baseline, so already-connected devices are not started unexpectedly when a
project opens. If the server, tunnel, or decoder cannot be started, the plugin
falls back to a managed external scrcpy window with a standard window frame.

The protocol used by scrcpy is internal and version-coupled. The plugin reads
the installed client version and starts the sibling server with that exact
version. The current embedded client targets scrcpy 4.x and H.264 video; other
versions or codecs use the external fallback.

## Recording

Recording opens a screen recorder options dialog with bitrate, resolution,
touch indicators, and the configured output directory. Recordings are limited
to 30 minutes and are written as MP4 files. Stop recording sends scrcpy a
graceful Ctrl+C/SIGINT request so the MP4 container can be finalized before the
file is reported as complete.

## Architecture

The plugin uses Kotlin, IntelliJ services, MVVM, repositories, and `StateFlow`.
Process execution uses IntelliJ's `GeneralCommandLine` and
`OSProcessHandler`; user-provided paths and arguments are passed as separate
process arguments and are never assembled into a shell command. The embedded
video path uses a reverse ADB tunnel, scrcpy's 12-byte video packet framing,
JCodec's pure-Java H.264 decoder, and a Swing renderer. Binary screenshot
capture uses a separate `ProcessBuilder` path so PNG bytes are not decoded as
text.

## Licensing

Scrcpy is distributed under the Apache License 2.0. This plugin expects an
external scrcpy installation and does not redistribute its binaries. Review
the licenses of any external scrcpy/adb distribution before redistributing
the plugin.
