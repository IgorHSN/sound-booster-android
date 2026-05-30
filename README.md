# Loudify — Sound Booster

> Amplify your audio beyond the system maximum. No root required.

A minimalist Android volume booster that applies software gain on top of your device's hardware volume ceiling using Android's native `LoudnessEnhancer` API.

---

## Features

- Boost audio from **0 to +60 dB** above the system maximum
- Flat dark UI — clean, minimal, distraction-free
- Runs as a persistent **foreground service** in the background
- Real-time slider control with no audio interruptions
- Watchdog that keeps the effect alive automatically
- Compatible with **Android 5.0+ (API 21)**

---

## How it works

Loudify uses `android.media.audiofx.LoudnessEnhancer` on Android's global audio session (session 0), which processes all audio passing through the software mixer.

**Activation flow:**
1. `LoudnessEnhancer(0)` is created and attached to the global output mix
2. `setTargetGain(gainMb)` applies the boost in millibels (0–6000 mB)
3. `AudioManager` dispatches a single pause→play event to force the active media player to reconnect to the effect chain
4. A watchdog timer checks every 3 seconds that the effect is still alive, recreating it silently if needed

**Slider changes** call `setTargetGain()` directly — no interruption, real-time.

---

## Project structure

```
loudify/
├── app/src/main/
│   ├── java/com/soundbooster/app/
│   │   ├── MainActivity.kt          # UI — flat dark, slider, toggle
│   │   ├── AudioBoosterService.kt   # Foreground service (START_STICKY)
│   │   └── AudioBoosterManager.kt   # LoudnessEnhancer logic + watchdog
│   ├── res/layout/activity_main.xml
│   └── AndroidManifest.xml
├── store-listing/
│   ├── descriptions/                # pt-BR and en-US Play Store copy
│   ├── graphics/                    # Icon 512×512 and Feature Graphic 1024×500
│   └── privacy-policy/index.html   # Hostable privacy policy
├── app/build.gradle                 # Release signing config
└── gradle.properties                # Keystore placeholders
```

---

## Build

**Debug (for testing):**
```
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

**Release (for Play Store):**
1. Generate a keystore: Android Studio → **Build → Generate Signed Bundle / APK**
2. Fill in `gradle.properties`:
```properties
KEYSTORE_PATH=../loudify-keystore.jks
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=loudify
KEY_PASSWORD=your_password
```
3. Run:
```
./gradlew assembleRelease
```

---

## Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog 2023.1.1+ |
| Android SDK | 34 |
| Kotlin | 1.9.x |
| Gradle | 8.4 |
| Min Android | 5.0 (API 21) |

---

## Permissions

| Permission | Why |
|---|---|
| `MODIFY_AUDIO_SETTINGS` | Apply volume boost and set stream to max |
| `FOREGROUND_SERVICE` | Keep boost active in background |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required for media foreground service on API 29+ |
| `POST_NOTIFICATIONS` | Show persistent notification on Android 13+ |

---

## Notes

- Works best with apps that use Android's software audio mixer (YouTube, browser media, local players)
- A single brief pause/play is dispatched on activation so the active player picks up the effect
- Tested on **Motorola Moto G84 5G** running Android 13

---

## License

MIT
