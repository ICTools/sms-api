#!/usr/bin/env bash
# Build a signed release APK for the internal SMS Gateway tool.
#
# Required env vars (or gradle properties of the same name):
#   SMS_GATEWAY_KEYSTORE          absolute path to .keystore
#   SMS_GATEWAY_KEYSTORE_PASSWORD store password
#   SMS_GATEWAY_KEY_ALIAS         key alias inside the store
#   SMS_GATEWAY_KEY_PASSWORD      key password
#
# Output: app/build/outputs/apk/release/app-release.apk

set -euo pipefail
cd "$(dirname "$0")/.."

missing=()
for var in SMS_GATEWAY_KEYSTORE SMS_GATEWAY_KEYSTORE_PASSWORD SMS_GATEWAY_KEY_ALIAS SMS_GATEWAY_KEY_PASSWORD; do
    if [[ -z "${!var:-}" ]]; then
        missing+=("$var")
    fi
done

if (( ${#missing[@]} > 0 )); then
    echo "ERROR: missing env vars:" >&2
    printf '  %s\n' "${missing[@]}" >&2
    echo >&2
    echo "Run scripts/generate-keystore.sh first or export them manually." >&2
    exit 1
fi

if [[ ! -f "$SMS_GATEWAY_KEYSTORE" ]]; then
    echo "ERROR: keystore not found: $SMS_GATEWAY_KEYSTORE" >&2
    exit 1
fi

./gradlew :app:assembleRelease "$@"

APK="app/build/outputs/apk/release/app-release.apk"
if [[ -f "$APK" ]]; then
    echo
    echo "Signed APK ready: $APK"
    ls -lh "$APK"
else
    echo "ERROR: expected APK not found at $APK" >&2
    exit 1
fi
