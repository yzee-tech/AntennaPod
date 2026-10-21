#!/system/bin/sh
# Copies AntennaPod Debug (de.danoeh.antennapod.debug) data to AntennaPod Personal (de.danoeh.antennapod.personal).
# Runs on the phone through "adb shell". Never changes or deletes anything of the old app.
OLD=de.danoeh.antennapod.debug
NEW=de.danoeh.antennapod.personal
TMP=/data/local/tmp/antennapod-migration
EXT=/sdcard/Android/data

fail() {
    echo
    echo "STOPPED: $*"
    echo "The old app was not changed. You can keep using it."
    exit 1
}

step() {
    echo
    echo "== $*"
}

step "1/8 Checking both apps"
pm path $OLD > /dev/null 2>&1 || fail "AntennaPod Debug ($OLD) is not installed."
pm path $NEW > /dev/null 2>&1 || fail "AntennaPod Personal ($NEW) is not installed. Install it first."
run-as $OLD true || fail "Cannot read the old app's data (run-as $OLD failed)."
run-as $NEW true || fail "Cannot write the new app's data (run-as $NEW failed)."
run-as $OLD ls databases/Antennapod.db > /dev/null 2>&1 || fail "The old app has no database."
echo "OK"

step "2/8 Stopping both apps"
am force-stop $OLD
am force-stop $NEW
if [ ! -d $EXT/$NEW/files ]; then
    echo "Opening AntennaPod Personal once so that Android creates its storage folder..."
    monkey -p $NEW -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
    sleep 10
    am force-stop $NEW
fi
[ -d $EXT/$NEW/files ] || fail "AntennaPod Personal has no storage folder. Open it once, then run this again."
echo "OK"

step "3/8 Checking free space"
mkdir -p $TMP || fail "Cannot create $TMP"
NEED=0
if [ -d $EXT/$OLD/files ]; then
    set -- $(du -sk $EXT/$OLD/files)
    NEED=$1
fi
set -- $(df -k /sdcard | tail -n 1)
FREE=$4
echo "Episodes to copy: $((NEED / 1024)) MB, free space: $((FREE / 1024)) MB"
[ $((NEED + 512000)) -lt "$FREE" ] || fail "Not enough free space (need the episode size plus 500 MB)."

step "4/8 Backing up current data of both apps to $TMP"
STAMP=$(date +%Y%m%d-%H%M%S)
run-as $OLD tar cf - databases shared_prefs > $TMP/old-app-$STAMP.tar || fail "Backup of the old app failed."
run-as $NEW sh -c 'tar cf - databases shared_prefs 2> /dev/null; true' > $TMP/new-app-before-$STAMP.tar
ls -l $TMP

step "5/8 Copying database and settings"
PREFS=$(run-as $OLD ls shared_prefs | grep '\.xml$' | grep -v '^androidx\.work' | sed 's#^#shared_prefs/#')
DBS=$(run-as $OLD ls databases | grep '^Antennapod\.db' | sed 's#^#databases/#')
run-as $NEW sh -c 'rm -f databases/Antennapod.db databases/Antennapod.db-wal databases/Antennapod.db-shm databases/Antennapod.db-journal'
run-as $NEW mkdir -p databases shared_prefs
run-as $OLD tar cf - $DBS $PREFS | run-as $NEW tar xf - || fail "Copying the database failed."
run-as $NEW sh -c "mv shared_prefs/${OLD}_preferences.xml shared_prefs/${NEW}_preferences.xml 2> /dev/null; sed -i 's#/$OLD/#/$NEW/#g' shared_prefs/*.xml" \
    || fail "Updating the settings failed."
OLDSUM=$(run-as $OLD md5sum databases/Antennapod.db | cut -d' ' -f1)
NEWSUM=$(run-as $NEW md5sum databases/Antennapod.db | cut -d' ' -f1)
[ "$OLDSUM" = "$NEWSUM" ] || fail "The copied database differs from the original."
run-as $NEW ls -l databases shared_prefs

step "6/8 Copying downloaded episodes (this can take a while)"
if [ -d $EXT/$OLD/files ]; then
    cp -r $EXT/$OLD/files/. $EXT/$NEW/files/ || fail "Copying episodes failed."
fi

step "7/8 Verifying every copied episode file"
if [ -d $EXT/$OLD/files ]; then
    (cd $EXT/$OLD/files && find . -type f ! -name .nomedia -exec md5sum {} + | sort) > $TMP/old-files.md5
    (cd $EXT/$NEW/files && md5sum -c $TMP/old-files.md5 > $TMP/check.txt 2>&1) \
        || fail "Some copied files differ, see $TMP/check.txt"
    echo "$(wc -l < $TMP/old-files.md5) files copied and verified"
    REF=$(stat -c %u:%g $EXT/$NEW/files)
    BAD=$(find $EXT/$NEW/files ! -user "${REF%:*}" | wc -l)
    echo "Owner of the new app's folder: $REF, files with a different owner: $BAD"
    [ "$BAD" = "0" ] || fail "Some copied files have the wrong owner."
else
    echo "The old app has no downloaded episodes."
fi

step "8/8 Starting AntennaPod Personal"
monkey -p $NEW -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
echo
echo "DONE. Check AntennaPod Personal: subscriptions, queue, downloaded episodes, settings."
echo "The old app (AntennaPod Debug) and its files were not changed."
echo "Backups are in $TMP on the phone."
