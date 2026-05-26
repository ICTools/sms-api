#!/usr/bin/env bash
# Install the signed release APK on a connected Android device via adb.
#
# Usage: scripts/install.sh [device_serial]
#
# If no serial is given, installs on the first attached device.
# Builds the APK first if it's missing.

set -euo pipefail
cd "$(dirname "$0")/.."

APK="app/build/outputs/apk/release/app-release.apk"
SERIAL="${1:-}"

if [[ ! -f "$APK" ]]; then
    echo "APK missing — running build-release.sh first."
    scripts/build-release.sh
fi

devices=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ -z "$devices" ]]; then
    echo "ERROR: no Android device connected via adb." >&2
    echo "Plug in the phone with USB debugging enabled, or connect via 'adb connect <ip>:5555'." >&2
    exit 1
fi

if [[ -z "$SERIAL" ]]; then
    SERIAL=$(echo "$devices" | head -n1)
fi

echo "Installing $APK on device: $SERIAL"
adb -s "$SERIAL" install -r -d "$APK"

echo
echo "Done. Launch the app from the home drawer and grant SMS + notification permissions."
