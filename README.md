# StandBy clock for Android (optimized for Android 9)

iPhone StandBy-style clock: vertical clock in portrait, horizontal clock in landscape.

## Build the APK (GitHub)
1. Create a new GitHub repository and upload everything in this folder (the `.github` folder must be at the repo root).
2. Open the **Actions** tab -> **Build APK** -> wait for the green check (or press *Run workflow*).
3. Download the artifact **StandBy-debug-apk**, unzip, copy `app-debug.apk` to the tablet and install it.

## Using it
- Tap the screen -> settings gear appears (bottom right).
- Swipe up/down = brightness, swipe left/right = change clock style.
- Settings -> Fonts -> *Import fonts*: pick .ttf/.otf from the USB drive (menu -> choose the USB drive). Fonts are copied into the app, so you can unplug the USB.
- Settings -> Background pictures -> *Add pictures*: originals are copied untouched and shown at full screen resolution.
