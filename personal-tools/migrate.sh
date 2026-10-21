#!/bin/bash
# Copies AntennaPod Debug's complete data (database, settings, downloaded episodes) to a computer
# and restores it into a newly installed build, so that a build with a different signing key can
# replace the installed one without losing anything.
#   bash migrate.sh backup  <backup-folder>
#   bash migrate.sh restore <backup-folder> <new-apk>
set -euo pipefail
PKG=de.danoeh.antennapod.debug
EXT=/sdcard/Android/data/$PKG/files
INTERNAL_TAR=/data/local/tmp/antennapod-appdata.tar

cmd=${1:-}
dir=${2:-}
if [ -z "$cmd" ] || [ -z "$dir" ]; then
  echo "Usage: bash migrate.sh backup <folder>   or   bash migrate.sh restore <folder> <new-apk>"
  exit 1
fi

case "$cmd" in
  backup)
    mkdir -p "$dir"
    adb get-state > /dev/null
    adb shell pm path $PKG > /dev/null
    adb shell am force-stop $PKG
    echo "Copying database and settings..."
    adb exec-out "run-as $PKG sh -c 'cd /data/data/$PKG && tar cf - \$(ls | grep -v -e ^cache\$ -e ^code_cache\$ -e ^lib\$)'" > "$dir/appdata.tar"
    tar tf "$dir/appdata.tar" | grep -q "^databases/Antennapod.db$"
    tar tf "$dir/appdata.tar" | grep -q "^shared_prefs/"
    echo "Copying downloaded episodes (this can take a while)..."
    rm -rf "$dir/external"
    adb pull "$EXT" "$dir/external"
    echo "Backup complete:"
    du -sh "$dir/appdata.tar" "$dir/external"
    ;;
  restore)
    apk=${3:-}
    if [ -z "$apk" ] || [ ! -f "$apk" ]; then
      echo "Usage: bash migrate.sh restore <folder> <new-apk>"
      exit 1
    fi
    tar tf "$dir/appdata.tar" | grep -q "^databases/Antennapod.db$"
    [ -d "$dir/external" ]
    if [ "${YES:-}" != "1" ]; then
      read -r -p "This uninstalls AntennaPod Debug from the phone and restores the backup in '$dir'. Type yes to continue: " answer
      [ "$answer" = "yes" ]
    fi
    adb uninstall $PKG || true
    adb install "$apk"
    adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > /dev/null
    sleep 15
    adb shell am force-stop $PKG
    echo "Restoring downloaded episodes..."
    for f in "$dir/external"/*; do
      [ -e "$f" ] && adb push "$f" "$EXT/"
    done
    echo "Restoring database and settings..."
    adb push "$dir/appdata.tar" $INTERNAL_TAR
    adb shell "run-as $PKG sh -c 'cd /data/data/$PKG && rm -rf databases shared_prefs files && tar xf $INTERNAL_TAR'"
    adb shell rm -f $INTERNAL_TAR
    adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 > /dev/null
    echo "Restore complete. AntennaPod Debug has been started."
    ;;
  *)
    echo "Unknown command: $cmd"
    exit 1
    ;;
esac
