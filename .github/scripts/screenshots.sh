#!/usr/bin/env bash
# Runs the screenshot tour on the connected emulator and prints every image as base64
# between "=== SCREEN <name>" / "=== END" markers, so the UI can be reviewed from the job log.
set -u
APP=com.example.trackly
./gradlew --console=plain installDebug installDebugAndroidTest
adb shell run-as "$APP" rm -rf files/screens || true
# A slow CI emulator can show "Pixel Launcher isn't responding" over the app: hide such system
# error dialogs for the whole run (emulator only) and close any that is already open.
adb shell settings put global hide_error_dialogs 1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
# Clean status bar for store screenshots (System UI demo mode): 9:41, full battery and signal, no icons.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4
demo notifications -e visible false
adb shell am instrument -w -e package com.example.trackly.screens \
  "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tail -n 40
demo exit
for f in $(adb shell run-as "$APP" ls files/screens | tr -d '\r'); do
  echo "=== SCREEN $f"
  adb exec-out run-as "$APP" cat "files/screens/$f" | base64 -w 76
  echo "=== END"
done
exit 0
