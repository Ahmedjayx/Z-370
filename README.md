# Z370 Launcher

An OEM+ home screen for the GT6 (Android 13, 1080x1920 portrait) head unit in a 2020 Nissan 370Z.

## Install on the head unit
1. Connect the unit to Wi-Fi (phone hotspot is fine).
2. In the unit's browser open: `https://github.com/Ahmedjayx/Z-370/releases/latest/download/Z370-Launcher-latest.apk`
3. Install, allow installs from the browser if asked.
4. Press Home and choose **Z370 Launcher**, set as default (Settings > Apps > Default apps > Home app).

Updates install over the top with the same link.

## What it does
- Three-pod row: GPS heading, GPS speed, analog clock (no OBD needed)
- Navigation card opens your maps app
- Media card shows and controls what's playing (needs media access once)
- Dock: CarPlay, Maps, Phone, Music, Camera, All apps. Long-press a tile to choose its app
- Illumination colour: tap the settings icon top right

## Go back to the stock launcher
Settings > Apps > Default apps > Home app, pick the original. Or uninstall this app.

## Build
GitHub Actions builds and publishes a release on every push to `main`.
Signing uses `app/z370.jks` (sideload key, kept in the repo so updates install over each other).
