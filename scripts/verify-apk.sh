#!/bin/sh
# verify-apk.sh - check that an APK is signed by the expected certificate.
#
# Uses apksigner (Android SDK build-tools). No network access.
#
# Exit codes:
#   0  every signer certificate matches the expected SHA-256 fingerprint
#   1  mismatch, or apksigner says the signature is not valid
#   2  usage error, missing file, or apksigner not found

set -u

usage() {
    cat <<'EOF'
Usage: verify-apk.sh <path-to-apk> <expected-sha256-fingerprint>

  <path-to-apk>                  the APK file to check
  <expected-sha256-fingerprint>  the published signing certificate SHA-256
                                 fingerprint, 64 hex digits; colons, spaces
                                 and upper case are accepted

Environment:
  APKSIGNER  full path to apksigner, if it is not on your PATH
             (it lives in <Android SDK>/build-tools/<version>/)

Example:
  verify-apk.sh koocheh.apk AA:BB:CC:...(64 hex digits in total)

The expected fingerprint must come from the maintainers' published page
(release/SIGNING_KEY_FINGERPRINT.md), not from the place you got the APK.
This script does not use the network and cannot fetch it for you.
EOF
}

if [ "$#" -ne 2 ]; then
    usage >&2
    exit 2
fi

apk=$1
expected=$(printf '%s' "$2" | tr -d ': \t' | tr 'A-F' 'a-f')

case "$expected" in
    *[!0-9a-f]*)
        echo "Error: the fingerprint contains characters that are not hex digits." >&2
        usage >&2
        exit 2
        ;;
esac
if [ "${#expected}" -ne 64 ]; then
    echo "Error: a SHA-256 fingerprint has 64 hex digits; got ${#expected}." >&2
    usage >&2
    exit 2
fi

if [ ! -f "$apk" ]; then
    echo "Error: file not found: $apk" >&2
    exit 2
fi

if [ -n "${APKSIGNER:-}" ]; then
    tool=$APKSIGNER
else
    tool=$(command -v apksigner 2>/dev/null || true)
fi
if [ -z "$tool" ] || [ ! -x "$tool" ]; then
    echo "Error: apksigner not found. Install the Android SDK build-tools or set APKSIGNER." >&2
    exit 2
fi

if ! out=$("$tool" verify --print-certs "$apk" 2>&1); then
    echo "FAIL: apksigner could not verify the APK signature." >&2
    printf '%s\n' "$out" >&2
    exit 1
fi

digests=$(printf '%s\n' "$out" \
    | sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: *//p' \
    | tr 'A-F' 'a-f')

if [ -z "$digests" ]; then
    echo "Error: no signer certificate digest found in apksigner output." >&2
    exit 2
fi

status=0
for d in $digests; do
    echo "Signing certificate SHA-256: $d"
    if [ "$d" != "$expected" ]; then
        status=1
    fi
done
echo "Expected:                    $expected"

if [ "$status" -eq 0 ]; then
    echo "OK: signing certificate matches the expected fingerprint."
else
    echo "MISMATCH: do not install this APK." >&2
fi
exit "$status"
