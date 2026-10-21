#!/bin/bash
set -uxo pipefail
OLD=de.danoeh.antennapod.debug
NEW=de.danoeh.antennapod.personal
OEXT=/sdcard/Android/data/$OLD/files
NEXT=/sdcard/Android/data/$NEW/files
MEDIA="media/Test Feed/episode1.mp3"
OLDPATH="/storage/emulated/0/Android/data/$OLD/files/$MEDIA"
NEWPATH="/storage/emulated/0/Android/data/$NEW/files/$MEDIA"

result() {
  echo "RESULT: $*"
  echo "$*" >> migration-results.txt
}

check() {
  if "$@"; then
    result "ok: $*"
  else
    result "FAILED: $*"
    exit 1
  fi
}

pull_db() {
  rm -rf "$2" && mkdir -p "$2"
  for f in Antennapod.db Antennapod.db-wal Antennapod.db-shm; do
    if adb shell "run-as $1 ls databases/$f" > /dev/null 2>&1; then
      adb exec-out "run-as $1 cat databases/$f" > "$2/$f"
    fi
  done
  sqlite3 "$2/Antennapod.db" "PRAGMA wal_checkpoint(TRUNCATE);"
}

: > migration-results.txt
result "Android $(adb shell getprop ro.build.version.release | tr -d '\r') API $(adb shell getprop ro.build.version.sdk | tr -d '\r')"

check adb install old/app-play-debug.apk
adb shell monkey -p $OLD -c android.intent.category.LAUNCHER 1
sleep 25
adb shell am force-stop $OLD

pull_db $OLD olddb
sqlite3 olddb/Antennapod.db <<EOF
INSERT INTO Feeds (id, title, download_url, downloaded) VALUES (900, 'Test Feed', 'http://example.com/feed.xml', 1);
INSERT INTO FeedItems (id, title, feed, item_identifier, pubDate, read) VALUES (900, 'Episode 1', 900, 'ep1', 1700000000000, 0);
INSERT INTO FeedMedia (id, file_url, download_url, downloaded, filesize, mime_type, feeditem, duration, position)
  VALUES (900, '$OLDPATH', 'http://example.com/ep1.mp3', 1700000000000, 3000000, 'audio/mpeg', 900, 60000, 12345);
INSERT INTO FeedItems (id, title, feed, item_identifier, pubDate, read) VALUES (901, 'Episode 2', 900, 'ep2', 1700000000000, 0);
INSERT INTO FeedMedia (id, file_url, download_url, downloaded, filesize, mime_type, feeditem, duration, position)
  VALUES (901, '/storage/emulated/0/Android/data/$OLD/files/media/Test Feed/missing.mp3', 'http://example.com/ep2.mp3', 1700000000000, 3000000, 'audio/mpeg', 901, 60000, 0);
EOF
result "old db version $(sqlite3 olddb/Antennapod.db 'PRAGMA user_version;')"
adb push olddb/Antennapod.db /data/local/tmp/seed.db
adb shell "run-as $OLD rm -f databases/Antennapod.db-wal databases/Antennapod.db-shm"
adb shell "cat /data/local/tmp/seed.db | run-as $OLD sh -c 'cat > databases/Antennapod.db'"
adb shell rm /data/local/tmp/seed.db
result "old app settings files: $(adb shell "run-as $OLD ls shared_prefs" | tr -d '\r' | tr '\n' ' ')"
adb exec-out "run-as $OLD cat shared_prefs/${OLD}_preferences.xml" > oldprefs.xml || true
grep -q "</map>" oldprefs.xml || printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>\n" > oldprefs.xml
sed -i 's#</map>#    <string name="prefMigrationMarker">kept</string>\n</map>#' oldprefs.xml
adb push oldprefs.xml /data/local/tmp/oldprefs.xml
adb shell "run-as $OLD mkdir -p shared_prefs"
adb shell "cat /data/local/tmp/oldprefs.xml | run-as $OLD sh -c 'cat > shared_prefs/${OLD}_preferences.xml'"
adb shell rm /data/local/tmp/oldprefs.xml
check adb shell "run-as $OLD grep -q prefMigrationMarker shared_prefs/${OLD}_preferences.xml"
adb shell "mkdir -p '$OEXT/media/Test Feed' && head -c 3000000 /dev/urandom > '$OEXT/$MEDIA'"
adb shell "ls -lnZ $OEXT $OEXT/media '$OEXT/media/Test Feed'"

OLD_DB_MD5=$(adb shell "run-as $OLD md5sum databases/Antennapod.db" | cut -d' ' -f1)
OLD_PREFS_MD5=$(adb shell "run-as $OLD md5sum shared_prefs/${OLD}_preferences.xml" | cut -d' ' -f1)
OLD_MEDIA_MD5=$(adb shell "md5sum '$OEXT/$MEDIA'" | cut -d' ' -f1)

check adb install new/app-play-debug.apk

adb push personal-tools/migrate-antennapod.sh /data/local/tmp/migrate-antennapod.sh
adb shell sh /data/local/tmp/migrate-antennapod.sh 2>&1 | tee migrate-output.txt
STATUS=${PIPESTATUS[0]}
result "migration script exit code $STATUS, last line: $(tail -n 1 migrate-output.txt)"
check [ "$STATUS" = "0" ]
check grep -q "^DONE" migrate-output.txt

check [ "$OLD_DB_MD5" = "$(adb shell "run-as $OLD md5sum databases/Antennapod.db" | cut -d' ' -f1)" ]
check [ "$OLD_PREFS_MD5" = "$(adb shell "run-as $OLD md5sum shared_prefs/${OLD}_preferences.xml" | cut -d' ' -f1)" ]
check [ "$OLD_MEDIA_MD5" = "$(adb shell "md5sum '$OEXT/$MEDIA'" | cut -d' ' -f1)" ]

sleep 25
check adb shell pidof $NEW
adb shell am force-stop $NEW

pull_db $NEW newdb
check [ "$(sqlite3 newdb/Antennapod.db 'PRAGMA user_version;')" = "3130002" ]
check [ "$(sqlite3 newdb/Antennapod.db 'SELECT file_url FROM FeedMedia WHERE id = 900;')" = "$NEWPATH" ]
check [ "$(sqlite3 newdb/Antennapod.db 'SELECT position FROM FeedMedia WHERE id = 900;')" = "12345" ]
check [ "$(sqlite3 newdb/Antennapod.db 'SELECT downloaded > 0 FROM FeedMedia WHERE id = 900;')" = "1" ]
check [ "$(sqlite3 newdb/Antennapod.db 'SELECT downloaded FROM FeedMedia WHERE id = 901;')" = "0" ]
check [ "$(sqlite3 newdb/Antennapod.db 'SELECT file_url IS NULL FROM FeedMedia WHERE id = 901;')" = "1" ]
check [ "$(sqlite3 newdb/Antennapod.db "SELECT COUNT(*) FROM FeedMedia WHERE file_url LIKE '%antennapod.debug%';")" = "0" ]
check adb shell "run-as $NEW grep -q prefMigrationMarker shared_prefs/${NEW}_preferences.xml"
check [ "$OLD_MEDIA_MD5" = "$(adb shell "md5sum '$NEXT/$MEDIA'" | cut -d' ' -f1)" ]

adb shell "ls -lnZ $NEXT $NEXT/media '$NEXT/media/Test Feed'"
if adb shell "run-as $NEW md5sum '$NEWPATH'"; then
  result "run-as $NEW can read the copied episode"
else
  result "run-as $NEW cannot read the copied episode path (may be expected, run-as has no storage mount)"
fi
if adb root && sleep 5 && adb wait-for-device; then
  L=/data/media/0/Android/data/$NEW/files
  result "lower fs, app-created .nomedia: $(adb shell "stat -c '%u:%g %a %C' $L/.nomedia" | tr -d '\r')"
  result "lower fs, copied media dir:    $(adb shell "stat -c '%u:%g %a %C' $L/media" | tr -d '\r')"
  result "lower fs, copied episode:      $(adb shell "stat -c '%u:%g %a %C' '$L/$MEDIA'" | tr -d '\r')"
  NOMEDIA_UID=$(adb shell "stat -c %u $L/.nomedia" | tr -d '\r')
  check [ "$NOMEDIA_UID" = "$(adb shell "stat -c %u '$L/$MEDIA'" | tr -d '\r')" ]
  adb unroot
  sleep 5
  adb wait-for-device
else
  result "adb root not available, ownership not checked on the lower file system"
fi
result "Migration test passed"
