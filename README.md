# Camera Connect

Android app that connects your phone to a camera over Wi-Fi, without the camera maker's app.

Kotlin, Jetpack Compose, Material 3. The app is in early development. Photo download is its only feature for now, and more will follow. It speaks plain UPnP, so every camera that exposes a UPnP MediaServer is a candidate, but each model needs testing: see [Supported cameras](#supported-cameras).

## Features

- Connects to the camera's own Wi-Fi access point. The rest of the phone keeps mobile data.
- Finds the camera's UPnP MediaServer without any hardcoded address.
- Shows the photo grid, newest first, and loads older pages as you scroll.
- Previews a photo full screen, with pinch to zoom.
- Saves the original to `Pictures/CameraConnect/<model>`, for example `Pictures/CameraConnect/ILCE-7RM4`.
- English and French UI, with a language menu on the home screen.
- A hidden debug screen (long-press the title) that logs each discovery step.

## Supported cameras

| Camera | Model code | Status | Notes |
| --- | --- | --- | --- |
| Sony A7R IV | ILCE-7RM4 | Supported | [docs/cameras/sony-a7r-iv.md](docs/cameras/sony-a7r-iv.md) |

A camera that is not in this table can still work when it exposes a UPnP MediaServer with a ContentDirectory service. The home screen then shows an "Untested camera model" notice. To get a model into the table, follow [docs/adding-a-camera.md](docs/adding-a-camera.md).

## Limitations

- RAW photos: when the camera offers no original file, the app downloads the largest JPEG rendition and marks the photo with a "RAW" badge. The Sony A7R IV never offers the original of a RAW-only photo.
- The app is not on a store yet. You build the APK yourself.

## Requirements

- A phone on Android 10 (API 29) or newer.
- A camera from the table above, or an untested camera with a UPnP MediaServer.

## Install

Build the debug APK and install it with `adb` or Android Studio: see [docs/install.md](docs/install.md).

## Usage

1. Enable the camera's smartphone transfer mode. The home screen shows the menu path for each supported camera.
2. In the app, enter the SSID and the password that the camera shows. Tap "Connect". Accept the Android "Device to connect" dialog.
3. When the status card shows the camera name, tap "Open gallery".
4. Tap a photo to preview it. Tap "Download" to save it to the phone gallery.

The EN / FR button in the top bar switches the language.

## How it works

The camera runs a UPnP/DLNA MediaServer on its access point. The app finds it with SSDP, reads its description and browses the ContentDirectory service. Each photo offers one or more JPEG renditions and, for some photos, the original file.

[docs/architecture.md](docs/architecture.md) describes the code. Each page in [docs/cameras/](docs/cameras/) lists what one camera model exposes.

## Development

Build the debug APK:

```bash
./gradlew assembleDebug
```

Run the unit tests:

```bash
./gradlew testDebugUnitTest
```

The unit tests cover the parsers: SSDP, UPnP description, SCPD, SOAP Browse, DIDL-Lite and PTP/IP packets. The HTML report is in `app/build/reports/tests/testDebugUnitTest/index.html`.

Everything that talks to a camera needs a real one: [docs/manual-testing.md](docs/manual-testing.md) lists the procedures.

## Contributing

Issues and pull requests are welcome.

- Run the unit tests. Run the manual tests when you modify the Wi-Fi, discovery, gallery or download code.
- Write commit messages in the Conventional Commits format (`feat:`, `fix:`, `docs:`).
- Add every new UI string to both `strings.xml` files.
- Write docs in Simplified Technical English with the skill in `.claude/skills/asd-ste100`. Its linter must report zero hard violations:

```bash
python3 .claude/skills/asd-ste100/scripts/ste-lint.py README.md docs/*.md docs/cameras/*.md
```

- Reports from untested cameras are welcome, with the logs from the debug screen. [docs/adding-a-camera.md](docs/adding-a-camera.md) explains what to capture.

## License

The license of Camera Connect is the [GNU General Public License, version 3 or later](LICENSE) (GPL-3.0-or-later). In short:

- You can use, modify and share the app and its source code, also for commercial purposes.
- If you share the app or a modified version, you must share its full source code under this same license.
- A modified version must keep the copyright notices. It must also carry a prominent notice with the modification and its date.

This notice applies to every file in this repository:

```
Camera Connect - connects a phone to a camera over Wi-Fi.
Copyright (C) 2026  Alexis Charp

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
