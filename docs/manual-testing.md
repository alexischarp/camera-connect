# Manual testing on the camera

The unit tests cover the parsers only. Everything that talks to a camera needs a real one. Run these procedures after you modify the Wi-Fi, discovery, gallery or download code.

The procedures use the Sony A7R IV (ILCE-7RM4). For another model, replace the camera menu path with the one on its page in [cameras/](cameras/).

Turn off any VPN on the phone first. While a VPN is active, Android refuses to bind sockets to the camera network, and the home screen shows "A VPN is probably active" instead of finding the camera.

## Gallery, preview and download

1. Put the camera in "Send to Smartphone" → "Select on Smartphone" mode. Connect the phone from the home screen.
2. Within a few seconds, the status card shows "ILCE-7RM4 · SonyImagingDevice". The "Open gallery" button becomes enabled. For an unlisted model, the card also shows "Untested camera model".
3. Open the gallery. The grid fills with the 50 most recent photos in about 3 s. It loads older pages as you scroll.
4. Pull the list down to refresh it. RAW-only photos carry a "RAW" badge.
5. Tap a thumbnail. Pinch to zoom. Double-tap to return to the initial size. The bottom of the screen shows the resolution and the size of the original, or the RAW warning.
6. Tap "Download". A progress bar appears, then the message "Photo saved to Pictures/CameraConnect/ILCE-7RM4". Check the photo in the phone's Photos or Files app.
7. Post the photo from another app without disconnecting from the camera. Mobile data stays active for the rest of the phone.
8. Move the camera's power switch to OFF. The app must return to the home screen by itself, with the message "Connection lost".

Measure and note in your issue or pull request:

- the time to show the first page,
- the scrolling smoothness (the camera generates the `JPEG_SM` thumbnails on demand),
- the download time of a 10 to 15 MB original,
- every error message.

Logs stay available in the debug screen (long-press the title) and in logcat. See the end of the next section.

## Discovery (debug screen)

1. On the camera: MENU → Network tab → "Send to Smartphone Func." → "Send to Smartphone" → "Select on Smartphone". Or choose "Select on This Device" and pick a few photos. The camera shows a `DIRECT-xxxx:ILCE-7RM4` SSID and a password.
2. In the app, enter the SSID and the password. Tap "Connect". Grant the requested permission. Accept the Android "Device to connect" dialog.
3. The status card switches to "Connected". It shows the interface, the phone's IP and the camera's likely hosts (DHCP server, gateway, DNS).
4. Long-press the "Camera Connect" title in the top bar to open the debug screen.
5. Tap "Full discovery". The app runs this sequence:
   - SSDP M-SEARCH for 4 s, repeated every second, multicast and unicast to the likely hosts, while it holds a `MulticastLock`. It also listens on port 1900 for the camera's NOTIFY advertisements.
   - Download and parse the description XML of every `LOCATION` received.
   - Download the SCPD of the ContentDirectory service (the list of actions).
   - `Browse` the root object `0` (BrowseMetadata, then BrowseDirectChildren). Then walk the containers breadth-first, depth 3, 12 calls at most, with the `<res>` elements of each item.
   - If no ContentDirectory answered: probe PTP/IP (port 15740) on every likely host.
6. Discovered containers appear as chips. Tap one to browse its content.
7. If SSDP returns nothing, repeat the test with the camera in "Ctrl w/ Smartphone" or "PC Remote" mode ("PTP/IP only"). Note which mode you used.
8. Share the logs with the "Share" icon (text file) or the "Copy logs" button. The app also writes the file to its storage:

```bash
adb pull /sdcard/Android/data/app.cameraconnect/files/logs/
```

The same lines appear in logcat:

```bash
adb logcat -s "CameraConnect/Wifi" "CameraConnect/Discovery" "CameraConnect/Ssdp" "CameraConnect/ContentDirectory" "CameraConnect/PtpIp" "CameraConnect/Http"
```

The "UPnP description URL (manual)" field loads a description from a URL that you know by other means, for example from a network capture. The app hardcodes nothing.
