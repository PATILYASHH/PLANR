#!/usr/bin/env bash
# Creates the ExoSynk Labs release key for PLANR and prints what to paste into
# GitHub (repo → Settings → Secrets and variables → Actions). Run it on YOUR
# computer, once. Needs a JDK (keytool) and openssl.
#
#   ./scripts/create-release-key.sh            # writes ./planr-release.jks
#   ./scripts/create-release-key.sh my.jks     # custom path
#
# Keep the .jks file and the password safe and backed up OFFLINE: if you lose
# them, no future PLANR update can be installed over the current one.
set -euo pipefail

OUT="${1:-planr-release.jks}"
ALIAS="exosynk-planr"
DNAME="${PLANR_KEY_DNAME:-CN=ExoSynk Labs, O=ExoSynk Labs, C=IN}"

if [ -e "$OUT" ]; then
  echo "error: $OUT already exists — refusing to overwrite a signing key." >&2
  exit 1
fi
command -v keytool >/dev/null || { echo "error: keytool not found (install a JDK)." >&2; exit 1; }
command -v openssl >/dev/null || { echo "error: openssl not found." >&2; exit 1; }

# PKCS12 keystores use one password for the store and the key.
PASS="$(openssl rand -base64 33 | tr -d '/+=\n' | cut -c1-32)"

keytool -genkeypair -v \
  -keystore "$OUT" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "$DNAME" >/dev/null 2>&1

B64_FILE="$OUT.base64.txt"
base64 < "$OUT" | tr -d '\n' > "$B64_FILE"
SHA256="$(keytool -list -v -keystore "$OUT" -storepass "$PASS" -alias "$ALIAS" | awk -F': ' '/SHA256:/ {print $2; exit}')"

cat <<MSG

✅ Created $OUT (RSA 4096, valid 30 years)

Add these 4 repository secrets in GitHub → Settings → Secrets and variables → Actions:

  PLANR_KEYSTORE_BASE64   = contents of $B64_FILE
  PLANR_KEYSTORE_PASSWORD = $PASS
  PLANR_KEY_ALIAS         = $ALIAS
  PLANR_KEY_PASSWORD      = $PASS

Certificate SHA-256 (record it; XPENC/FITNS/CONECTR will need it for ExoSynk Link):
  $SHA256

Then: back up $OUT and the password offline, and DELETE $B64_FILE.
MSG
