#!/bin/bash
set -euxo pipefail
PKG=de.danoeh.antennapod.personal
adb install old/app-play-debug.apk
adb shell "run-as $PKG sh -c 'mkdir -p files && echo kept-across-upgrade > files/upgrade-marker'"
adb shell "run-as $PKG cat files/upgrade-marker"
adb install -r new/app-play-debug.apk
adb shell "run-as $PKG cat files/upgrade-marker" | grep kept-across-upgrade
adb shell dumpsys package $PKG | grep -E "versionName|firstInstallTime|lastUpdateTime"
echo "Upgrade test passed"
