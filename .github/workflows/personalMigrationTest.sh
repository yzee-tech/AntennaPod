#!/bin/bash
set -euxo pipefail
PKG=de.danoeh.antennapod.debug
EXT=/sdcard/Android/data/$PKG/files
adb install old/app-play-debug.apk
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 20
adb shell am force-stop $PKG
adb shell "run-as $PKG sh -c 'mkdir -p $EXT/media/TestFeed && head -c 3000000 /dev/urandom > $EXT/media/TestFeed/episode.mp3'"
adb shell "run-as $PKG sh -c 'echo \"<map><string name=\\\"marker\\\">kept</string></map>\" > shared_prefs/migration_marker.xml'"
BEFORE_MEDIA=$(adb shell "run-as $PKG md5sum $EXT/media/TestFeed/episode.mp3" | awk '{print $1}')
BEFORE_DB=$(adb shell "run-as $PKG md5sum databases/Antennapod.db" | awk '{print $1}')
BEFORE_PREFS=$(adb shell "run-as $PKG ls shared_prefs" | tr -d '\r' | sort | tr '\n' ' ')
if adb install -r new/app-play-debug.apk; then
  echo "Expected the new build to be refused as an update of the old build"
  exit 1
fi
bash personal-tools/migrate.sh backup bk
YES=1 bash personal-tools/migrate.sh restore bk new/app-play-debug.apk
sleep 15
adb shell pidof $PKG
adb shell am force-stop $PKG
AFTER_MEDIA=$(adb shell "run-as $PKG md5sum $EXT/media/TestFeed/episode.mp3" | awk '{print $1}')
AFTER_PREFS=$(adb shell "run-as $PKG ls shared_prefs" | tr -d '\r' | sort | tr '\n' ' ')
adb shell "run-as $PKG cat shared_prefs/migration_marker.xml" | grep kept
adb shell "run-as $PKG ls databases" | grep Antennapod.db
adb shell "run-as $PKG sh -c 'echo playback-check >> $EXT/media/TestFeed/episode.mp3'"
[ "$BEFORE_MEDIA" = "$AFTER_MEDIA" ]
for p in $BEFORE_PREFS; do echo " $AFTER_PREFS " | grep -q " $p "; done
echo "BEFORE_DB=$BEFORE_DB"
echo "Migration test passed"
