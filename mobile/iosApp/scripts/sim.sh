#!/bin/sh
# Build, run and screenshot the iOS app on the simulator (docs/IOS-REDESIGN-PLAN.md verification loop).
#
#   scripts/sim.sh build                 xcodebuild Debug for the simulator; prints errors only
#   scripts/sim.sh run [launch args…]    install + relaunch, e.g. `run -gallery` or `run -parity A1`
#   scripts/sim.sh shot <file.png>       screenshot the booted simulator
#
# DEVICE overrides the simulator (default "iPhone 17 Pro"). Run from anywhere.
set -eu
cd "$(dirname "$0")/.."
DEVICE="${DEVICE:-iPhone 17 Pro}"
APP=build/Build/Products/Debug-iphonesimulator/iosApp.app
LOG="${TMPDIR:-/tmp}/crichere-iosbuild.log"

case "${1:-}" in
build)
    # Regenerate first so new Swift files join the target (safe: Info.plist comes from project.yml).
    /opt/homebrew/bin/xcodegen generate --quiet
    xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator \
        -destination "platform=iOS Simulator,name=$DEVICE" -derivedDataPath build \
        CODE_SIGNING_ALLOWED=NO -skipMacroValidation build >"$LOG" 2>&1 && status=0 || status=$?
    grep -E "error:" "$LOG" | sort -u | head -40 || true
    grep -E "BUILD SUCCEEDED|BUILD FAILED" "$LOG" || echo "(no result line; see $LOG)"
    exit "$status"
    ;;
run)
    shift
    xcrun simctl boot "$DEVICE" 2>/dev/null || true
    xcrun simctl bootstatus "$DEVICE" -b >/dev/null
    xcrun simctl install "$DEVICE" "$APP"
    xcrun simctl terminate "$DEVICE" com.crichere.app 2>/dev/null || true
    xcrun simctl launch "$DEVICE" com.crichere.app "$@"
    ;;
shot)
    xcrun simctl io "$DEVICE" screenshot "${2:?file.png}" >/dev/null 2>&1
    echo "${2}"
    ;;
*)
    sed -n '2,8p' "$0"
    exit 2
    ;;
esac
