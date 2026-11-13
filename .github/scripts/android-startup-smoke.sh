#!/usr/bin/env bash
set -euo pipefail

# Run on a dedicated emulator, never uninstall or clear an existing installation.
APK=${1:?Usage: android-startup-smoke.sh /absolute/path/to/release.apk}
ADB=${ADB:-${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}/platform-tools/adb}
PACKAGE=com.qq.knitushka

"$ADB" install -r "$APK"
"$ADB" shell am force-stop "$PACKAGE"
launch_output=$("$ADB" shell am start -W -n "$PACKAGE/.MainActivity")
printf '%s\n' "$launch_output"
if grep -Eq 'Error:|Exception|Status: timeout' <<< "$launch_output"; then
  echo 'Release APK failed to launch.' >&2
  exit 1
fi

# UI Automator waits for UI idle, allowing asynchronous initialization to complete.
"$ADB" shell uiautomator dump /data/local/tmp/knitushka-startup.xml
"$ADB" shell cat /data/local/tmp/knitushka-startup.xml > "${RUNNER_TEMP:-/tmp}/knitushka-startup.xml"
if ! grep -q 'package="com.qq.knitushka"' "${RUNNER_TEMP:-/tmp}/knitushka-startup.xml"; then
  echo 'Release APK did not display its own UI (possible startup crash).' >&2
  exit 1
fi
"$ADB" shell pidof "$PACKAGE"
echo 'Release APK startup smoke test passed.'
