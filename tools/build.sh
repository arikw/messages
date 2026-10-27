#!/bin/sh
# Builds the release APK (foss flavor) in a one-shot podman container. Usage: tools/build.sh [gradle tasks...]
cd "$(dirname "$0")/.." || exit 1
[ $# -eq 0 ] && set -- assembleFossRelease
exec podman run --rm -v "$PWD:/app:Z" -v fossify-messages-gradle:/root/.gradle \
  localhost/fossify-messages-build sh -c 'echo "sdk.dir=/opt/android-sdk" > local.properties && ./gradlew --no-daemon -q "$@"' sh "$@"
