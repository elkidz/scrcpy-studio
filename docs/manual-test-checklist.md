# Manual test checklist

Run these checks with a physical Android device or emulator and the target
Android Studio build.

## Tool discovery

- [ ] Configure the scrcpy executable by selecting the executable file.
- [ ] Configure the scrcpy executable by selecting its containing directory.
- [ ] Confirm adb is discovered from the scrcpy directory.
- [ ] Confirm adb is discovered from Android SDK `platform-tools`.
- [ ] Confirm the Test configuration action reports missing or invalid tools.

## Devices and sessions

- [ ] Connect one USB-debugging device and refresh the list.
- [ ] Connect one wireless-debugging device and refresh the list.
- [ ] Show unauthorized, offline, and no-permissions states without enabling Start.
- [ ] Start and stop one mirror session.
- [ ] Start sessions for two different devices.
- [ ] Close the tool window and confirm the child scrcpy processes are cleaned up.
- [ ] Close an external scrcpy window and confirm the session state updates.

## Embedded protocol client

- [ ] Configure the scrcpy executable from an official distribution containing
      `scrcpy-server`.
- [ ] Verify the matching server is pushed and the device screen appears inside
      the session tab.
- [ ] Resize the tool window and verify the decoded video scales without opening
      an SDL window.
- [ ] Click and drag inside the embedded video and verify touch input reaches
      the device.
- [ ] Press Escape inside the video and verify Android Back is sent.
- [ ] Force the reverse tunnel or decoder to fail and verify the managed external
      scrcpy fallback remains usable.
- [ ] Stop a session and confirm the ADB reverse mapping is removed.
- [ ] Start two sessions and confirm each uses an independent SCID and tunnel.
- [ ] Close the tool window and confirm server processes, sockets, and reverse
      mappings are cleaned up.

## Recording follow-up

- [ ] Validate the selected output path and generated MP4 filename.
- [ ] Start and stop recording from an active mirror.
- [ ] Open the resulting MP4 and seek through it.
- [ ] Stop the IDE while recording and confirm the output behavior is documented.

Recording checks are intentionally deferred from the current implementation
pass.
