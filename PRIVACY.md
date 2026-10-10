# GeoStamp privacy

GeoStamp has no advertising, analytics, crash reporting or account SDKs. Photos and videos are processed on the device and saved to `Pictures/GeoStamp` and `Movies/GeoStamp`. Nothing is uploaded automatically, and app data is excluded from cloud backup and device transfer.

## Permissions

| Permission | Why | When |
|---|---|---|
| Camera | Take photos and videos | Required for the camera screen |
| Location (precise or approximate) | Coordinates, accuracy and true-north heading on stamps | Optional. Foreground only, while the camera screen is open. No background location |
| Microphone | Sound in videos | Optional. Requested when switching to Video. Videos record silently without it, and the app says so while recording |
| Internet | Only for the optional online features below | Never used unless you turn a feature on |

## Optional online features (all off by default)

| Feature | Sent to | Data sent |
|---|---|---|
| Address lookup | Your phone's platform geocoder (on most phones, Google) | Current coordinates |
| Map thumbnails | OpenStreetMap tile servers (`tile.openstreetmap.org`) | The map tiles around your position, which reveal approximate location. Tiles are cached on the device for 7 days |
| Weather | Open-Meteo (`api.open-meteo.com`) | Coordinates rounded to 3 decimals (about 100 m) |

Results are fetched in the background and only used for photos taken near the place they were fetched for. The shutter never waits for the network, and missing data is left off the stamp rather than guessed.

## Metadata

- Visible stamps can include precise coordinates and time. Review photos before sharing.
- Writing GPS coordinates into EXIF metadata is a separate setting and is **off** by default. When it is off, GeoStamp removes GPS tags from the photos it saves.
- "Keep unstamped original" saves a second, unstamped copy. It follows the same EXIF setting.

This document describes the source implementation; it is not an independent security audit.

## Video, addresses and diagnostics

- Videos are stamped on the phone. The raw recording stays in app-private storage until the stamped copy is checked and saved, and is then deleted unless "Keep unstamped original" is on.
- A typed address is held in memory only, for the next capture or until the app closes. It is never sent anywhere. Photos with a typed address are marked as such in their EXIF comment.
- The Camera diagnostics report stays on the phone unless you choose Share.

