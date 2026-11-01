#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
# Your TV's address with adb debugging on, e.g. TV=192.168.1.20:5555 scripts/install.sh
TV="${TV:?Set TV to your TV's IP:5555}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export GRADLE_USER_HOME="$PWD/.gradle-home"
export ANDROID_USER_HOME="$GRADLE_USER_HOME/android"
mkdir -p "$ANDROID_USER_HOME"

./gradlew assembleRelease
adb connect "$TV" >/dev/null 2>&1 || true
adb -s "$TV" install -r app/build/outputs/apk/release/app-release.apk
# Compile ahead of time with the bundled Compose profiles; JIT-only start is slow on the stick.
adb -s "$TV" shell cmd package compile -m speed-profile -f com.mytvsetup.launcher
