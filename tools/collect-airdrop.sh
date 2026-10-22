#!/system/bin/sh
DEST=/sdcard/Download/ColorOS-AirDrop-diagnostics.txt
mkdir -p /sdcard/Download || exit 1
CAPTURE=$(mktemp /data/local/tmp/codex-airdrop-report.XXXXXX) || exit 1
trap 'rm -f "$CAPTURE"' EXIT
exec > "$DEST" 2>&1
run() {
    echo
    echo "--- $* ---"
    "$@" </dev/null > "$CAPTURE" 2>&1
    code=$?
    cat "$CAPTURE"
    echo "[exit=$code]"
}
echo 'ColorOS AirDrop diagnostics v3; 2026-10-21'
date
run id
run getprop ro.product.model
run getprop ro.build.display.id
run getprop ro.build.version.sdk
run uname -r
run getconf PAGESIZE
run getenforce
run cmd package list packages --show-versioncode --user 0 com.google.android.gms
run cmd package list packages --show-versioncode --user 0 com.coloros.oshare
run cmd package list packages --show-versioncode --user 0 com.google.android.mosey
run settings get system oshare_state
run service check com.google.android.moseyservice.IMoseyService/default
run dumpsys activity service com.google.android.mosey/.ExternalSharingService
run dumpsys package com.google.android.mosey
run cmd package list packages --show-versioncode --user 0 dev.codex.moseycompat
run cmd appops get com.google.android.mosey
echo
echo '--- Root modules ---'
for prop in /data/adb/modules/*/module.prop; do
    [ -f "$prop" ] || continue
    grep -E '^(id|name|version)=' "$prop"
    [ ! -f "${prop%/*}/disable" ] || echo '[DISABLED]'
done
for log in /data/adb/cmt/native.log /data/adb/cmt/native-preflight.log /data/adb/cmt/oneclick/status.txt /data/adb/cmt/oneclick/setup.log /data/adb/cmt/oneclick/apk-install.log /data/adb/cmt/oneclick/nearby-permissions.log; do
    echo
    echo "--- $log ---"
    if [ -r "$log" ]; then tail -n 80 "$log"; else echo '[not present]'; fi
done
echo
echo '--- Current VPN state ---'
dumpsys vpn_management </dev/null 2>&1 | grep -E 'Active package name:|NetworkCapabilities:|Uids:|OwnerUid:'
echo
echo '--- Recent related logcat ---'
logcat -d -t 5000 </dev/null 2>&1 | grep -E 'CodexOShare|CodexMosey|Mosey\.|MoseyService|mosey_daemon|AndroidRuntime.*(mosey|Exception|Permission)|ClashMetaForAndroid.*mosey' | tail -n 180
echo
echo '--- End of report ---'
