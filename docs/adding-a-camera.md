# Adding a camera

The app speaks plain UPnP and hardcodes nothing about a camera model. To add a model: capture what the camera exposes, check that the gallery works, then add it to the supported list.

## What the app needs from a camera

- A Wi-Fi access point that the phone can join with a password (WPA2).
- A UPnP MediaServer announced over SSDP, with a ContentDirectory service that answers `Browse`.
- Photo items (`object.item.imageItem.photo`) with at least one JPEG `<res>` element. The app recognises the DLNA profiles `JPEG_TN`, `JPEG_SM`, `JPEG_MED` and `JPEG_LRG`. A `<res>` without a profile counts as the original file.

A camera that only speaks PTP/IP (for example in a "PC Remote" mode) does not work. The debug screen can only probe the PTP/IP port.

## Capture what the camera exposes

1. Connect the phone to the camera from the home screen. For an unlisted model, the session card shows "Untested camera model".
2. Open the debug screen (long-press the title). Tap "Full discovery". The discovery procedure in [manual-testing.md](manual-testing.md) describes the sequence.
3. Share the logs. They contain the SSDP answers, the device description, the SCPD and the first `Browse` responses.
4. Open the gallery. Test the preview and the download with the gallery procedure in [manual-testing.md](manual-testing.md).

Open an issue with the logs, the camera model and what worked. This is useful even when you do not change the code.

## Add the model to the app

1. Add an entry to `SupportedCamera` in `app/src/main/kotlin/app/cameraconnect/camera/SupportedCamera.kt`. It needs the name shown to the user, the model code the camera reports and its SSID example. The device description in the logs gives the model code.
2. Add the camera's menu path as `tutorial_<model>_step_N` strings in `res/values/strings.xml` and `res/values-fr/strings.xml`. Map them in `tutorialSteps` in `ui/home/HomeScreen.kt`.
3. Save the captured device description, SCPD and a DIDL-Lite excerpt under `app/src/test/resources/fixtures/`. Add a parser test like `SonyFixturesTest`.
4. Write `docs/cameras/<model>.md` after the [Sony A7R IV page](cameras/sony-a7r-iv.md). Add the model to the table in the README.
