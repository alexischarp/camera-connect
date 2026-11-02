# Sony A7R IV (ILCE-7RM4)

Status: supported. Measured on a real ILCE-7RM4 in "Send to Smartphone" mode on 2026-11-02. The files `app/src/test/resources/fixtures/sony_a7riv_*` are real captures of the responses below, with the device identifiers (UDN, storage id, photo numbers) replaced. The unit tests use them as reference.

## Enable the camera's Wi-Fi

1. Press MENU. Open the Network tab (globe icon), page 1.
2. Select "Send to Smartphone Func.", then "Send to Smartphone".
3. Choose "Select on Smartphone" to browse every photo from the phone, or "Select on This Device" to send only a selection.
4. The camera shows an SSID (`DIRECT-xxxx:ILCE-7RM4`) and a password. Leave that screen open.

The French menu labels in the app match the camera's menus. The English labels follow Sony's Help Guide. No one checked them on a camera set to English.

## Network

- The camera opens an access point named `DIRECT-xxxx:ILCE-7RM4`.
- The camera is the DHCP server, the gateway and the DNS server, at `192.168.122.1`.
- The phone gets an address in `192.168.0.x/16`.

## UPnP MediaServer

The camera announces a UPnP/DLNA MediaServer over SSDP. Its description is at `http://192.168.122.1:64321/dd.xml` and contains `av:photoRoot=PhotoRoot`. The description lists three services:

- ContentDirectory:1, with the actions `Browse`, `GetSearchCapabilities`, `GetSortCapabilities` and `GetSystemUpdateID`. There is no `Search` action.
- ConnectionManager:1.
- A Sony `DigitalImaging:1` service.

The device description reports `ILCE-7RM4` as friendly name and `SonyImagingDevice` as model name. The app recognises the model from the friendly name.

## Content hierarchy

`0` → `PhotoRoot` → "Date" container → one container per day → photo items.

- A day container has a title such as `2026-11-1`. Its `childCount` is the number of photos of that day.
- A photo is an `object.item.imageItem.photo` item with a `dc:date`.
- `Browse` returns at most 50 items per call, whatever `RequestedCount` says. A page takes about 2.5 s.
- The camera lists the items of a day from oldest to newest.

## Renditions

Each photo offers three transcoded JPEG renditions, served on port 60151: `JPEG_TN` (thumbnail), `JPEG_SM` (small) and `JPEG_LRG` (about 2 Mpx). The camera advertises no size and no resolution for them.

JPEG photos also offer the original: `image/jpeg`, 9504×6336, with a known size.

RAW-only photos (`DSC0xxxx.ARW`) offer only the three JPEG renditions. **The camera does not transfer the .ARW file.** The app marks these photos with a "RAW" badge and a notice in the preview, and downloads `JPEG_LRG` for them.

Rendition URLs carry an opaque query string. The app uses them as they are.

## What the app derives from this

- Thumbnails: `JPEG_SM`.
- Preview: `JPEG_LRG`.
- Download: the original when it exists, otherwise `JPEG_LRG`.
- The app walks the days from newest to oldest. It reads each day from its end (`StartingIndex = total − 50`), so the latest photos appear first.
- Downloads go to `Pictures/CameraConnect/ILCE-7RM4` on the phone.
