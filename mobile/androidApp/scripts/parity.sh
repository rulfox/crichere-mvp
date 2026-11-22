#!/bin/bash
# Android side of the iOS parity captures (docs/IOS-REDESIGN-PLAN.md §5).
#   androidApp/scripts/parity.sh [board,board,...]   capture those boards (default: all) to docs/ios-parity/android/
#
# Everything heavy lives on PARITY_TOOLS (default: the external SSD): the Android SDK + emulator, the AVD,
# Gradle's cache, and a mirror of mobile/ that Gradle builds in, so the internal disk doesn't fill.
set -euo pipefail
cd "$(dirname "$0")/../.."           # mobile/
REPO=$(cd .. && pwd)
TOOLS=${PARITY_TOOLS:-/Volumes/Aswin_SSD/crichere-android-tools}
export ANDROID_HOME=${ANDROID_HOME:-$TOOLS/sdk}
export ANDROID_USER_HOME=$TOOLS/user-home ANDROID_AVD_HOME=$TOOLS/avd GRADLE_USER_HOME=$TOOLS/gradle-home
ADB=$ANDROID_HOME/platform-tools/adb
AVD=crichere-parity
MIRROR=$TOOLS/worktree/mobile
OUT=$REPO/docs/ios-parity/android

if [ ! -d "$ANDROID_AVD_HOME/$AVD.avd" ]; then
    mkdir -p "$ANDROID_AVD_HOME"
    echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" \
        -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_9_pro >/dev/null
fi
if ! "$ADB" devices | grep -q "emulator-"; then
    "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-snapshot -no-audio -no-boot-anim -no-window -gpu host >"$TOOLS/emulator.log" 2>&1 &
    "$ADB" wait-for-device
    until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
    "$ADB" shell settings put global window_animation_scale 0
    "$ADB" shell settings put global transition_animation_scale 0
    "$ADB" shell settings put global animator_duration_scale 0
    "$ADB" shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
    "$ADB" shell "settings put system time_12_24 12" || true
fi

rsync -a --delete --exclude build --exclude .gradle --exclude .kotlin --exclude iosApp --exclude local.properties ./ "$MIRROR/"
echo "sdk.dir=$ANDROID_HOME" > "$MIRROR/local.properties"
(cd "$MIRROR" && ./gradlew -q :androidApp:installDebug :androidApp:installDebugAndroidTest)

args=(-w -e class com.crichere.app.parity.ParityCaptureTest)
[ $# -gt 0 ] && args+=(-e boards "$1")
"$ADB" shell am instrument "${args[@]}" com.crichere.app.test/androidx.test.runner.AndroidJUnitRunner | grep -E "FAIL|Error|OK \(|Tests run" || true

mkdir -p "$OUT"
REMOTE=/sdcard/Android/data/com.crichere.app/files/parity
for f in $("$ADB" shell ls "$REMOTE" | tr -d '\r'); do
    "$ADB" pull -q "$REMOTE/$f" "$OUT/$f"
    sips -Z 1311 "$OUT/$f" >/dev/null   # same scale as the iOS captures
    echo "$OUT/$f"
done
"$ADB" shell rm -rf "$REMOTE"
