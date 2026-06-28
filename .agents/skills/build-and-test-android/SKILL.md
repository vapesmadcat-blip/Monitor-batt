---
name: build-and-test-android
description: How to build the debug APK and run/test the Monitor-batt Android app on an emulator. Use when building the app or visually testing UI changes (e.g. the main screen, themes).
---

# Build & emulator-test Monitor-batt

Android (Java) app, package `com.vapesmadcat.monitorbatt`. Gradle wrapper, JDK 17, compile/target SDK 34.

## Build
The org blueprint installs the Android SDK to `$HOME/android-sdk` and sets `ANDROID_HOME`.
If building manually, ensure these are set, then:
```bash
export ANDROID_HOME=$HOME/android-sdk ANDROID_SDK_ROOT=$HOME/android-sdk
./gradlew assembleDebug   # APK -> app/build/outputs/apk/debug/app-debug.apk
```
CI (`.github/workflows/build.yml`) runs `gradle assembleDebug` on push to main only — not on PRs.

## Emulator setup (for UI testing)
```bash
sdk=$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager
yes | $sdk "emulator" "system-images;android-34;google_apis;x86_64"
echo no | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n test34 \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_5 --force
sudo chmod 666 /dev/kvm   # current user is not in the kvm group by default
DISPLAY=:0 $ANDROID_HOME/emulator/emulator -avd test34 -gpu swiftshader_indirect -no-snapshot -no-boot-anim &
```
Wait for boot, then install and launch:
```bash
export PATH=$PATH:$ANDROID_HOME/platform-tools
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != 1 ]; do sleep 3; done
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.vapesmadcat.monitorbatt -c android.intent.category.LAUNCHER 1
adb exec-out screencap -p > /tmp/shot.png   # capture; retry if PNG is truncated mid-render
```

## UI notes
- Main screen: top-left is the STATUS/charging-gauge card; top-right is the Light/Dark theme switch (toggling it recreates the Activity to apply night colors).
- After a theme toggle the rate/time briefly show `--` (rate sampler needs two battery readings) — expected.
- The emulator window is portrait; on the 1024x768 desktop it can't be enlarged much, so use `adb exec-out screencap` for clean full-res screenshots.
