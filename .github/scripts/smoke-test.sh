#!/usr/bin/env bash
# Installs Harbour on an emulated tablet and checks it actually works before release:
# the start screen appears, Choose folder opens Harbour's browser, and nothing crashes.
set -euo pipefail

PKG=com.harbour.reader

fail() {
  echo "::error::Smoke test failed: $1"
  echo "----- screen -----"; cat ui.xml 2>/dev/null || true
  echo "----- crash log -----"; adb logcat -d -b crash || true
  echo "----- app log -----"; adb logcat -d | grep -iE "harbour|AndroidRuntime" | tail -150 || true
  exit 1
}

dump_screen() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1 || true
}

# Waits up to $2 seconds for text $1 to be on screen.
wait_for() {
  for _ in $(seq 1 "$2"); do
    dump_screen
    if grep -q "text=\"$1\"" ui.xml 2>/dev/null; then return 0; fi
    sleep 1
  done
  fail "\"$1\" never appeared on screen"
}

# Taps the centre of the element whose text is $1.
tap() {
  local bounds
  bounds=$(grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" ui.xml | grep -o 'bounds="[^"]*"' | head -1)
  [ -n "$bounds" ] || fail "couldn't find \"$1\" to tap"
  local n=($(echo "$bounds" | grep -o '[0-9]\+'))
  adb shell input tap $(( (n[0] + n[2]) / 2 )) $(( (n[1] + n[3]) / 2 ))
}

assert_alive() {
  adb shell pidof "$PKG" >/dev/null || fail "Harbour is not running ($1)"
  if adb logcat -d -b crash | grep -q "$PKG"; then fail "Harbour crashed ($1)"; fi
}

adb install -r Harbour.apk
adb logcat -c || true

echo "Opening Harbour"
adb shell am start -W -n "$PKG/.MainActivity"
wait_for "Choose folder" 30
assert_alive "after launch"

echo "Tapping Choose folder"
tap "Choose folder"
wait_for "Choose a folder" 15
assert_alive "after opening the folder browser"

echo "Going back to the library"
adb shell input keyevent KEYCODE_BACK
wait_for "Choose folder" 15
assert_alive "after going back"

echo "Smoke test passed"
