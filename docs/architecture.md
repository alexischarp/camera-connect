# Architecture

```
app/src/main/kotlin/app/cameraconnect/
├── AppContainer.kt            # hand-wired dependencies (no DI framework)
├── data/ConnectionPreferences # SSID / password / PTP GUID (DataStore)
├── locale/AppLocales          # UI language choice (LocaleManager on 13+, SharedPreferences below)
├── wifi/                      # WifiNetworkSpecifier + requestNetwork, link info (CameraLink)
├── network/                   # OkHttp client bound to the camera network, "local addresses only" policy
├── camera/
│   ├── CameraSessionManager   # Wi-Fi link → session (short SSDP + UPnP description)
│   ├── SupportedCamera        # tested models: name, model code, SSID example
│   ├── CameraLibrary          # finds the photo containers, reads pages of items
│   ├── PhotoPager             # walks from newest to oldest, 50 items per page
│   ├── CameraPhoto            # photo model + rendition choice (thumbnail, preview, original)
│   └── GalleryRepository      # list shared between the gallery and the preview
├── download/PhotoDownloader   # MediaStore save (Pictures/CameraConnect/<model>) with progress
├── discovery/
│   ├── DiscoveryOrchestrator  # debug screen: SSDP → description → SCPD → Browse → PTP/IP
│   ├── DiscoveryLogger        # in-memory journal + logcat
│   ├── ssdp/                  # M-SEARCH, response parsing, MulticastLock
│   ├── upnp/                  # device description, SCPD, SOAP Browse, DIDL-Lite (DOM)
│   └── ptpip/                 # PTP/IP codec and probe (InitCommand, InitEvent, GetDeviceInfo)
└── ui/                        # Compose: home, gallery (Coil), preview, hidden debug screen
```

## Key choices

- The camera network is never the process default network. Only the dedicated OkHttp client and explicitly bound sockets use it. The client resolves `network.socketFactory` when it creates each socket. Bound sockets use `network.bindSocket`. The rest of the phone keeps mobile data. Coil loads images through that same client, with a 256 MB disk cache.
- `network_security_config.xml` cannot express "local network", because it only accepts host names. The code must not hardcode the camera's IP. The app therefore allows cleartext HTTP globally and enforces the restriction in code. `LocalAddressPolicy` rejects every address that is not private (RFC 1918) or link-local.
- Runtime permissions: `NEARBY_WIFI_DEVICES` (Android 13+) or location (Android 10 to 12), plus `ACCESS_LOCAL_NETWORK` on Android 17+. Without the last one, the system drops every packet to the local network (`sendto failed: EPERM`). A VPN active on the phone has the same effect: `Network.bindSocket` fails with `EPERM` and the app shows a dedicated "VPN probably active" message instead of "the camera does not answer".
- The code hardcodes nothing camera-specific. The IP comes from the DHCP link. The URLs come from SSDP, then from the description. The service types and actions come from the description and the SCPD. Only the ports and identifiers that the protocols define appear in the code (SSDP 1900, PTP/IP 15740, UPnP root `0`).
- `SupportedCamera` lists the tested models, but the app treats an unlisted camera the same way. The list only drives the home screen tutorials, the SSID example, the "untested model" notice and the name of the download folder.
- The `fixtures/sony_a7riv_*` test fixtures are real captures with the device identifiers replaced. The other fixtures are synthetic.

## Languages

The default resources (`res/values/strings.xml`) are English. The French translation is in `res/values-fr/strings.xml`. Every new string goes in both files, and `res/xml/locales_config.xml` declares both locales.

`locale/AppLocales` stores the language choice. On Android 13+ the system stores it (`LocaleManager`). It is the same setting as Settings → Apps → Camera Connect → Language. On Android 10 to 12 the app keeps the choice in SharedPreferences and applies it to the activity in `attachBaseContext`.
