#!/usr/bin/env bash
# Generate a self-signed keystore for signing release APKs of the internal
# SMS Gateway tool. Output: keystore/sms-gateway.keystore
#
# Usage: scripts/generate-keystore.sh [keystore_path] [alias]
#
# Prompts for store and key passwords interactively.

set -euo pipefail

KEYSTORE="${1:-$(pwd)/keystore/sms-gateway.keystore}"
ALIAS="${2:-sms-gateway}"

mkdir -p "$(dirname "$KEYSTORE")"

if [[ -f "$KEYSTORE" ]]; then
    echo "ERROR: keystore already exists at $KEYSTORE" >&2
    echo "Refusing to overwrite. Move it aside or pass a different path." >&2
    exit 1
fi

echo "Generating keystore at: $KEYSTORE"
echo "Alias: $ALIAS"
echo "Validity: 10000 days"
echo

keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storetype PKCS12

echo
echo "Done. Save the passwords in a password manager."
echo "Set these env vars (or add to ~/.gradle/gradle.properties) before running build-release.sh:"
echo "  export SMS_GATEWAY_KEYSTORE=\"$KEYSTORE\""
echo "  export SMS_GATEWAY_KEYSTORE_PASSWORD=..."
echo "  export SMS_GATEWAY_KEY_ALIAS=\"$ALIAS\""
echo "  export SMS_GATEWAY_KEY_PASSWORD=..."
