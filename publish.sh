#!/usr/bin/env bash
#
# Build (and optionally publish) the signed plugin for the JetBrains Marketplace.
#
#   ./publish.sh           build the SIGNED zip (use this for the first, manual upload)
#   ./publish.sh publish   sign + upload via the Marketplace API (for later updates)
#
# Secrets (passphrase + token) are read from ./secrets.properties (git-ignored) if
# present, otherwise you are prompted. The signing key/cert are read from the
# private.pem / chain.crt files in this directory.
#
set -euo pipefail
cd "$(dirname "$0")"

# Load PRIVATE_KEY_PASSWORD / PUBLISH_TOKEN from a local, git-ignored file (if any).
if [[ -f secrets.properties ]]; then
  set -a; . ./secrets.properties; set +a
fi

[[ -f private.pem ]] || { echo "ERROR: private.pem not found — generate it with openssl first." >&2; exit 1; }
[[ -f chain.crt   ]] || { echo "ERROR: chain.crt not found — generate it with openssl first."   >&2; exit 1; }

# The Gradle signing config reads the key/cert *content* from these env vars.
export PRIVATE_KEY="$(cat private.pem)"
export CERTIFICATE_CHAIN="$(cat chain.crt)"

if [[ -z "${PRIVATE_KEY_PASSWORD:-}" ]]; then
  read -rsp "Private key passphrase: " PRIVATE_KEY_PASSWORD; echo
  export PRIVATE_KEY_PASSWORD
fi

action="${1:-sign}"
case "$action" in
  sign)
    ./gradlew clean signPlugin
    echo
    echo "✓ Signed plugin: $(ls build/distributions/*.zip)"
    echo "  First time: upload it manually at https://plugins.jetbrains.com/plugin/add"
    ;;
  publish)
    if [[ -z "${PUBLISH_TOKEN:-}" ]]; then
      read -rsp "Marketplace publish token: " PUBLISH_TOKEN; echo
      export PUBLISH_TOKEN
    fi
    ./gradlew clean publishPlugin
    echo "✓ Published to the JetBrains Marketplace."
    ;;
  *)
    echo "usage: $0 [sign|publish]" >&2; exit 2;;
esac
