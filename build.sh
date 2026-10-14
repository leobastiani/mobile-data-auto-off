#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -z "${ANDROID_HOME:-}" ] && [ -d "${HOME}/Library/Android/sdk" ]; then
  export ANDROID_HOME="${HOME}/Library/Android/sdk"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "${HOME}/Android/Sdk" ]; then
  export ANDROID_HOME="${HOME}/Android/Sdk"
fi
if [ "$#" -eq 0 ]; then set -- assembleDebug; fi
exec ./gradlew "$@"
