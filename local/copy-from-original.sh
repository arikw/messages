# Runs on the phone as root:  adb shell "su -c sh -s list" < local/copy-from-original.sh   (only lists, changes nothing)
#                             adb shell "su -c sh -s copy" < local/copy-from-original.sh
# Copies the original Fossify Messages' settings (shared_prefs) and local database (databases) into net.wzmn.messages.
# Only reads the original app's folder; writes only inside the new app's folder. The messages themselves are in
# Android's shared message store and need no copying.
SRC=/data/data/org.fossify.messages
DST=/data/data/net.wzmn.messages
MODE="$1"
[ -d "$SRC" ] || { echo "ORIGINAL APP DATA NOT FOUND: $SRC"; exit 1; }
[ -d "$DST" ] || { echo "NEW APP NOT INSTALLED: $DST"; exit 1; }

echo "== original app's files (names and sizes only)"
find "$SRC" -type f ! -path "*/cache/*" ! -path "*/code_cache/*" -exec ls -ln {} \; | awk '{print $5, $NF}'
[ "$MODE" = copy ] || exit 0

am force-stop net.wzmn.messages
U=$(stat -c %u "$DST"); G=$(stat -c %g "$DST")
for d in shared_prefs databases; do
  [ -d "$SRC/$d" ] || continue
  rm -rf "${DST:?}/$d"
  cp -a "$SRC/$d" "$DST/$d" || { echo "COPY FAILED: $d"; exit 1; }
  for f in "$SRC/$d"/*; do
    n=$(basename "$f")
    h1=$(sha256sum "$f" | cut -d' ' -f1); h2=$(sha256sum "$DST/$d/$n" | cut -d' ' -f1)
    [ "$h1" = "$h2" ] || { echo "MISMATCH: $d/$n"; exit 1; }
  done
  # files named after the package (e.g. <package>_preferences.xml) must carry the new name
  for f in "$DST/$d"/org.fossify.messages*; do
    [ -e "$f" ] && mv "$f" "$DST/$d/net.wzmn.messages${f##*/org.fossify.messages}"
  done
done
chown -R "$U:$G" "$DST/shared_prefs" "$DST/databases"
# restorecon would drop the app's private SELinux categories (s0:cNNN,...), so copy the folder's own label
chcon -R "$(ls -Zd "$DST" | cut -d' ' -f1)" "$DST/shared_prefs" "$DST/databases"
echo "== copied into the new app"
ls -ln "$DST/shared_prefs" "$DST/databases" | awk 'NF>2 {print $3":"$4, $5, $NF}'
