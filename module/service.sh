#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/common.sh"
STATE=/data/adb/cmt
mkdir -p "$STATE"
chmod 700 "$STATE"
i=0
while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$i" -lt 90 ]; do
    sleep 1
    i=$((i+1))
done
[ -f "$MODDIR/disable" ] && exit 0
check_baseline || { echo "$CHECK_ERROR" > "$STATE/native.log"; exit 1; }
cp "$MODDIR/native/libmosey_daemon_ffi.so" "$STATE/ffi_mosey.so"
chmod 644 "$STATE/ffi_mosey.so"
chmod 755 "$MODDIR/native/mosey_server"
export LD_PRELOAD="$MODDIR/native/libmosey_local_binder.so"
"$MODDIR/native/mosey_server" > "$STATE/native.log" 2>&1 &
echo $! > "$STATE/native.pid"
# This runs after the original native-service launch; do not preload into pm/app_process.
unset LD_PRELOAD
i=0
while ! user_unlocked && [ "$i" -lt 180 ]; do
    [ -f "$MODDIR/disable" ] && exit 0
    sleep 2
    i=$((i+1))
done
# Allow the native Binder server to register before checking it.
i=0
while ! service check com.google.android.moseyservice.IMoseyService/default 2>/dev/null | grep -q ': found'; do
    [ "$i" -ge 15 ] && break
    sleep 2
    i=$((i+1))
done
/system/bin/sh "$MODDIR/setup.sh"
