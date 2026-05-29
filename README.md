# Sound Booster for Android

An Android application that boosts system audio volume beyond the default hardware maximum using Android's `LoudnessEnhancer` audio effect.

## Features

- Boost audio gain by up to +10 dB (1000 millibels) above the system maximum
- Slider control: 0% to 200% (0 dB to +10 dB)
- Live display of current system volume and boost percentage
- Runs as a persistent foreground service
- Material Design 3 dark theme UI
- Notification with quick-toggle action
- Supports Android 5.0 (API 21) and above

## Technical approach

### LoudnessEnhancer

The app uses `android.media.audiofx.LoudnessEnhancer`, an Android framework class that applies a gain to a specific audio session. The key steps are:

1. A silent `AudioTrack` is created to obtain a unique `audioSessionId`.
2. `LoudnessEnhancer(sessionId)` is attached to that session.
3. An attempt is made to also attach `LoudnessEnhancer(0)` (the global mix session), which works on many devices to provide system-wide boosting.
4. `setTargetGain(gainMb)` sets the boost in millibels. 1000 mB = 10 dB.

### System volume maximisation

When the boost is enabled, `AudioManager.setStreamVolume(STREAM_MUSIC, max, 0)` is called to push the media stream to its hardware ceiling before the software gain is applied on top.

### Foreground Service

`AudioBoosterService` runs as a foreground service with `foregroundServiceType="mediaPlayback"`, keeping the boost active when the app is in the background.

## Project structure

```
sound-booster/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/soundbooster/app/
│       │   ├── MainActivity.kt          # UI controller
│       │   ├── AudioBoosterService.kt   # Foreground service
│       │   └── AudioBoosterManager.kt   # Core audio logic
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/strings.xml
│           ├── values/colors.xml
│           ├── values/themes.xml
│           └── drawable/
├── build.gradle
├── app/build.gradle
├── settings.gradle
└── gradle.properties
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- Kotlin 1.9.x
- Gradle 8.4

## Build instructions

1. Open the project in Android Studio.
2. Wait for Gradle sync to complete.
3. Connect an Android device (API 21+) or start an emulator.
4. Click **Run** or use `./gradlew assembleDebug`.

## Permissions used

| Permission | Purpose |
|---|---|
| `FOREGROUND_SERVICE` | Keep service alive in background |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required for `foregroundServiceType="mediaPlayback"` on API 29+ |
| `MODIFY_AUDIO_SETTINGS` | Set stream volume to maximum |
| `RECORD_AUDIO` | Required on some devices for audio effect attachment |
| `POST_NOTIFICATIONS` | Show foreground service notification on API 33+ |

## Limitations

- The `LoudnessEnhancer` on session 0 (global mix) may be ignored by some device manufacturers.
- Gain beyond +10 dB is not achievable via the standard Android audio effects API without root.
- Excessive boost may cause audio clipping/distortion depending on the source content and hardware.
- Effects are applied to the Android software mix; hardware volume limits still apply at the DAC level.

## License

MIT License. See source files for details.
