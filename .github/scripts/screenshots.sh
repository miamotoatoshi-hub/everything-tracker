#!/usr/bin/env bash
# Runs the screenshot tour on the connected emulator and prints every image as base64
# between "=== SCREEN <name>" / "=== END" markers, so the UI can be reviewed from the job log.
set -u
APP=com.example.trackly
./gradlew --console=plain installDebug installDebugAndroidTest
adb shell run-as "$APP" rm -rf files/screens || true
adb shell am instrument -w -e package com.example.trackly.screens \
  "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tail -n 40
for f in $(adb shell run-as "$APP" ls files/screens | tr -d '\r'); do
  echo "=== SCREEN $f"
  adb exec-out run-as "$APP" cat "files/screens/$f" | base64 -w 76
  echo "=== END"
done
exit 0
