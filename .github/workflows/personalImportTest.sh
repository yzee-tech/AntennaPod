#!/bin/bash
set -uxo pipefail
OLD=de.danoeh.antennapod.debug
NEW=de.danoeh.antennapod.personal
OLDDIR="/storage/emulated/0/Android/data/$OLD/files/media/Test Feed"

result() {
  echo "RESULT: $*"
  echo "$*" >> import-results.txt
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
  sqlite3 "$2/Antennapod.db" "PRAGMA wal_checkpoint(TRUNCATE);" > /dev/null
}

q() {
  sqlite3 newdb/Antennapod.db "$1"
}

: > import-results.txt
result "Android $(adb shell getprop ro.build.version.release | tr -d '\r') API $(adb shell getprop ro.build.version.sdk | tr -d '\r')"

mkdir -p server
head -c 400000 /dev/urandom > server/ep1.mp3
head -c 500000 /dev/urandom > server/ep2.mp3
(cd server && python3 -m http.server 8000 > ../server.log 2>&1) &
sleep 2

check adb install old/app-play-debug.apk
adb shell monkey -p $OLD -c android.intent.category.LAUNCHER 1
sleep 25
adb shell am force-stop $OLD

pull_db $OLD olddb
sqlite3 olddb/Antennapod.db <<EOF
INSERT INTO Feeds (id, title, download_url, downloaded) VALUES (900, 'Test Feed', 'http://10.0.2.2:8000/feed.xml', 1);
INSERT INTO FeedItems (id, title, feed, item_identifier, pubDate, read) VALUES (900, 'Episode 1', 900, 'ep1', 1700000000000, 0);
INSERT INTO FeedItems (id, title, feed, item_identifier, pubDate, read) VALUES (901, 'Episode 2', 900, 'ep2', 1700000001000, 0);
INSERT INTO FeedMedia (id, file_url, download_url, downloaded, filesize, mime_type, feeditem, duration, position)
  VALUES (900, '$OLDDIR/ep1.mp3', 'http://10.0.2.2:8000/ep1.mp3', 1700000000000, 400000, 'audio/mpeg', 900, 600000, 12345);
INSERT INTO FeedMedia (id, file_url, download_url, downloaded, filesize, mime_type, feeditem, duration, position)
  VALUES (901, '$OLDDIR/ep2.mp3', 'http://10.0.2.2:8000/ep2.mp3', 1700000000000, 500000, 'audio/mpeg', 901, 600000, 23456);
EOF
adb push olddb/Antennapod.db /data/local/tmp/seed.db
adb shell "run-as $OLD rm -f databases/Antennapod.db-wal databases/Antennapod.db-shm"
adb shell "cat /data/local/tmp/seed.db | run-as $OLD sh -c 'cat > databases/Antennapod.db'"
adb shell rm /data/local/tmp/seed.db
adb shell "mkdir -p '$OLDDIR' && head -c 400000 /dev/urandom > '$OLDDIR/ep1.mp3' && head -c 500000 /dev/urandom > '$OLDDIR/ep2.mp3'"
OLD_DB_MD5=$(adb shell "run-as $OLD md5sum databases/Antennapod.db" | cut -d' ' -f1)

result "Database export from the old app: copy of its database file (same bytes the export writes)"
adb exec-out "run-as $OLD cat databases/Antennapod.db" > exported.db
result "exported database version $(sqlite3 exported.db 'PRAGMA user_version;')"

check adb install new/app-play-debug.apk
adb shell monkey -p $NEW -c android.intent.category.LAUNCHER 1
sleep 25
adb shell am force-stop $NEW

cat > newprefs.xml <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <set name="prefMobileUpdateTypes">
        <string>images</string>
        <string>episode_download</string>
    </set>
</map>
EOF
adb push newprefs.xml /data/local/tmp/newprefs.xml
adb shell "cat /data/local/tmp/newprefs.xml | run-as $NEW sh -c 'cat > shared_prefs/${NEW}_preferences.xml'"
adb shell rm /data/local/tmp/newprefs.xml

result "Database import into the new app: same steps as the import function (replace database, delete -wal/-shm/-journal)"
adb push exported.db /data/local/tmp/import.db
adb shell "run-as $NEW rm -f databases/Antennapod.db databases/Antennapod.db-wal databases/Antennapod.db-shm databases/Antennapod.db-journal"
adb shell "cat /data/local/tmp/import.db | run-as $NEW sh -c 'cat > databases/Antennapod.db'"
adb shell rm /data/local/tmp/import.db

adb shell monkey -p $NEW -c android.intent.category.LAUNCHER 1
FOUND=0
for i in $(seq 1 30); do
  sleep 5
  FOUND=$(adb shell "find /sdcard/Android/data/$NEW/files/media -type f 2> /dev/null" | tr -d '\r' | grep -c . || true)
  [ "$FOUND" -ge 2 ] && break
done
sleep 10
result "files in the new app's media folder: $FOUND"
result "requests to the test server: $(grep -c 'GET /ep' server.log || true)"
adb shell "ls -lR /sdcard/Android/data/$NEW/files/media" | tr -d '\r' | sed 's/^/  /' | tee -a import-results.txt
adb shell am force-stop $NEW

pull_db $NEW newdb
check [ "$(q 'PRAGMA user_version;')" = "3130002" ]
check [ "$(q 'SELECT position FROM FeedMedia WHERE id = 900;')" = "12345" ]
check [ "$(q 'SELECT position FROM FeedMedia WHERE id = 901;')" = "23456" ]
for id in 900 901; do
  URL=$(q "SELECT file_url FROM FeedMedia WHERE id = $id;")
  result "episode $id: downloaded=$(q "SELECT downloaded FROM FeedMedia WHERE id = $id;") file=$URL"
  check [ "$(q "SELECT downloaded > 0 FROM FeedMedia WHERE id = $id;")" = "1" ]
  check grep -q "/Android/data/$NEW/" <<< "$URL"
  check adb shell "ls '$URL'"
done
check [ "$(adb shell "stat -c %s '$(q 'SELECT file_url FROM FeedMedia WHERE id = 900;')'" | tr -d '\r')" = "400000" ]
check [ "$(adb shell "stat -c %s '$(q 'SELECT file_url FROM FeedMedia WHERE id = 901;')'" | tr -d '\r')" = "500000" ]
check [ "$OLD_DB_MD5" = "$(adb shell "run-as $OLD md5sum databases/Antennapod.db" | cut -d' ' -f1)" ]
check adb shell "ls '$OLDDIR/ep1.mp3' '$OLDDIR/ep2.mp3'"
result "Import test passed"
