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
# avdmanager and the emulator disagree on where AVDs live by default; pin it.
export ANDROID_AVD_HOME="$HOME/.android/avd"
mkdir -p "$ANDROID_AVD_HOME"

# Whatever goes wrong, show the emulator's own account of it.
trap 'status=$?; if [ $status -ne 0 ]; then echo "--- emulator.log (tail)"; tail -60 "$out/emulator.log" 2>/dev/null || true; fi' EXIT

echo "Installing $image"
# `yes` dies of SIGPIPE once sdkmanager stops reading, which pipefail would
# otherwise count as the install failing.
{ yes || true; } | "$sdk/cmdline-tools/latest/bin/sdkmanager" --install "$image" emulator platform-tools > "$out/sdkmanager.log"
echo no | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd --force -n smoke -k "$image"
echo "AVDs: $(emulator -list-avds | tr '\n' ' ')"

echo "Starting the emulator"
emulator -avd smoke -no-window -no-audio -no-boot-anim -no-snapshot -no-metrics -gpu swiftshader_indirect \
  -camera-back none -camera-front none > "$out/emulator.log" 2>&1 &
emu=$!

# Every wait is bounded: an emulator that never comes up should fail the job
# in minutes, not hang it.
deadline=$((SECONDS + 600))
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
  if ! kill -0 "$emu" 2>/dev/null; then
    echo "::error::The emulator exited before it finished booting"
    exit 1
  fi
  if [ $SECONDS -ge $deadline ]; then
    echo "::error::The emulator didn't finish booting within 10 minutes"
    exit 1
  fi
  sleep 5
done
adb shell input keyevent 82 || true   # wake and unlock
echo "Booted Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r')), page size $(adb shell getconf PAGE_SIZE | tr -d '\r')"

timeout 120 adb install -r "$apk"
adb logcat -c
timeout 60 adb shell am start -W -n "$pkg/.MainActivity"
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
