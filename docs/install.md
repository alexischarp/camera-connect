# Install Camera Connect on a phone

Camera Connect is not on an app store. You build the debug APK on a computer, then you install it on the phone with `adb` or with Android Studio.

## Requirements

On the computer:

- JDK 17 or newer. Android Studio bundles one.
- Android SDK with platform 37 (`platforms;android-37.0`) and build-tools 37.0.0. Android Studio installs them on demand. From the command line: `sdkmanager "platforms;android-37.0" "build-tools;37.0.0"`.
- `adb`, from the SDK package `platform-tools` or from Homebrew (`brew install android-platform-tools`).

On the phone: Android 10 (API 29) or newer.

The build uses Gradle 9.6 (the wrapper downloads it), AGP 9.5 with its built-in Kotlin support, and Kotlin 2.4.21. Current Jetpack Compose releases need AGP 9.1 or newer and compileSdk 37, so do not downgrade them.

Tell Gradle where the SDK is, either with the `ANDROID_HOME` environment variable or with a `local.properties` file at the repository root. Git ignores this file.

```
sdk.dir=/Users/<you>/Library/Android/sdk
```

## Build the APK

From the repository root:

```bash
./gradlew assembleDebug
```

Gradle writes the APK to `app/build/outputs/apk/debug/app-debug.apk`.

Install the debug build. The release build type has no signing configuration, so Android refuses its APK.

## Prepare the phone

1. Open Settings → About phone. Tap "Build number" seven times to enable Developer options.
2. Open Developer options (under Settings → System on stock Android). Enable "USB debugging".
3. Connect the phone to the computer with a USB cable.
4. On the phone, accept the "Allow USB debugging?" dialog.
5. On the computer, check that `adb` sees the phone:

```bash
adb devices
```

The phone appears with the state `device`. The state `unauthorized` means that the dialog of step 4 still waits on the phone.

On Android 11 or newer, wireless debugging replaces steps 2 to 4:

1. Connect the phone and the computer to the same Wi-Fi network.
2. Open Developer options → Wireless debugging. Enable it.
3. Tap "Pair device with pairing code". The phone shows a code and an address.
4. Run `adb pair <address>`. Enter the code.
5. Run `adb connect <address>` with the address on the main Wireless debugging screen. Its port differs from the pairing port.

## Install the APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `-r` flag replaces an installed version and keeps its data. The same command updates the app after a new build.

With Android Studio: open the project, select the phone in the device list, and run the `app` configuration. Android Studio builds and installs the APK in one step.

## First launch

1. Open Camera Connect. The UI follows the phone language, English or French. The EN / FR button in the top bar switches it.
2. Enable the camera's smartphone transfer mode. The home screen shows the menu path for each supported camera.
3. Enter the SSID and the password that the camera shows. Tap "Connect".
4. Grant the permission that Android requests. The app uses it only to join the camera's Wi-Fi:
   - Android 13 and newer: "Nearby devices".
   - Android 10 to 12: location.
   - Android 17 and newer: also the local network permission.
5. Accept the "Device to connect" dialog. Android joins the camera's Wi-Fi and keeps mobile data for everything else.

## Problems

- `adb: no devices/emulators found`: the phone is not connected, or USB debugging is off. Repeat "Prepare the phone".
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: the installed app has a different debug signature, for example a build from another computer. Uninstall it first. This deletes the saved SSID and password.

```bash
adb uninstall app.cameraconnect
```

- `INSTALL_FAILED_OLDER_SDK`: the phone runs an Android version older than 10.
- `SDK location not found` during the build: set `ANDROID_HOME` or create `local.properties` (see Requirements).
