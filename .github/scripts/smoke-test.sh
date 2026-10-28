#!/usr/bin/env bash
# Boot an emulator, install the APK, open the app and check it stays up.
#
# Robolectric runs the debug build on the JVM; this is the release APK, after
# R8, on real Android. Leaves a screenshot and the logcat in the output dir.
#
# Usage: smoke-test.sh <system image> <apk> <output dir>
#   e.g. smoke-test.sh "system-images;android-37.0;google_apis;x86_64" app.apk out/
set -euo pipefail
image="$1"
apk="$2"
out="$3"
pkg=com.nwbort.workouts
mkdir -p "$out"

sdk="${ANDROID_HOME:?}"
export PATH="$sdk/platform-tools:$sdk/emulator:$PATH"
yes | "$sdk/cmdline-tools/latest/bin/sdkmanager" --install "$image" emulator platform-tools > /dev/null
echo no | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd --force -n smoke -k "$image"

emulator -avd smoke -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect \
  -camera-back none -camera-front none > "$out/emulator.log" 2>&1 &
adb wait-for-device
timeout 900 bash -c 'until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = 1 ]; do sleep 5; done'
adb shell input keyevent 82 || true   # wake and unlock
echo "Booted $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r')), page size $(adb shell getconf PAGE_SIZE | tr -d '\r')"

adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity"
sleep 20
adb exec-out screencap -p > "$out/launch.png"
adb logcat -d > "$out/logcat.txt"

pid="$(adb shell pidof "$pkg" | tr -d '\r' || true)"
if grep -q "FATAL EXCEPTION" "$out/logcat.txt" || [ -z "$pid" ]; then
  echo "::error::The app crashed or exited on launch"
  grep -B2 -A60 "FATAL EXCEPTION" "$out/logcat.txt" || grep -iE "$pkg|AndroidRuntime" "$out/logcat.txt" | tail -80
  exit 1
fi
echo "Running as pid $pid after 20 s"
