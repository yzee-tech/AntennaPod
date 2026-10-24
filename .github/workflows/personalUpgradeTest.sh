#!/bin/bash
set -euxo pipefail
PKG=de.danoeh.antennapod.personal
adb install old/app-play-debug.apk
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 20
adb shell am force-stop $PKG
adb shell "run-as $PKG ls databases" | grep Antennapod.db
adb shell "run-as $PKG sh -c 'mkdir -p files && echo kept-across-upgrade > files/upgrade-marker'"
adb shell "run-as $PKG cat files/upgrade-marker"
adb install -r new/app-play-debug.apk
adb shell "run-as $PKG cat files/upgrade-marker" | grep kept-across-upgrade
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 20
adb shell pidof $PKG
adb shell am force-stop $PKG
adb shell "run-as $PKG ls databases" | grep Antennapod.db
adb shell dumpsys package $PKG | grep -E "versionName|firstInstallTime|lastUpdateTime"
echo "Upgrade test passed"
