#!/system/bin/sh
MODDIR=${0%/*}
STATE=/data/adb/cmt/oneclick
mkdir -p "$STATE"
chmod 700 "$STATE"
exec >> "$STATE/setup.log" 2>&1
echo "--- setup $(date) ---"
. "$MODDIR/common.sh"
fail() { echo "$*" > "$STATE/status.txt"; echo "$*"; exit 1; }
[ -f "$MODDIR/disable" ] && exit 0
check_baseline || fail "$CHECK_ERROR"
user_unlocked || fail 'Unlock the phone once, then tap Action to retry.'
# Serialize automatic setup and manual retries; an interrupted boot releases /dev locks.
LOCK=/dev/codex_mosey_setup.lock
mkdir "$LOCK" 2>/dev/null || fail 'Setup is already running; retry shortly.'
trap 'rmdir "$LOCK" 2>/dev/null' EXIT
if [ -z "$(pm path dev.codex.moseycompat 2>/dev/null)" ]; then
    [ -f "$STATE/hook-before.txt" ] || echo absent > "$STATE/hook-before.txt"
else
    [ -f "$STATE/hook-before.txt" ] || echo present > "$STATE/hook-before.txt"
fi
APK="$MODDIR/payload/Mosey-OShare-cloud-test.apk"
EXPECTED=$(cat "$MODDIR/payload/hook.sha256")
ACTUAL=$(pm path dev.codex.moseycompat 2>/dev/null | sed -n 's/^package://p' | head -n 1)
CURRENT=
[ -n "$ACTUAL" ] && CURRENT=$(sha256sum "$ACTUAL" 2>/dev/null | cut -d ' ' -f 1)
if [ "$CURRENT" != "$EXPECTED" ]; then
    # Installer stages in /data/local/tmp, readable by Android's package installer.
    TMPAPK=/data/local/tmp/codex-mosey-hook-install.apk
    cp "$APK" "$TMPAPK" || fail 'Cannot stage bridge APK.'
    chmod 644 "$TMPAPK"
    # Do not pass adb_data_file log FDs to system_server's shell-command Binder call.
    install_output=$(pm install -r --user 0 "$TMPAPK" </dev/null 2>&1)
    install_result=$?
    printf '%s\n' "$install_output" > "$STATE/apk-install.log"
    echo "PM_EXIT_CODE=$install_result" >> "$STATE/apk-install.log"
    rm -f "$TMPAPK"
    installed_after=$(pm path dev.codex.moseycompat 2>/dev/null | sed -n 's/^package://p' | head -n 1)
    installed_after_hash=
    [ -z "$installed_after" ] || installed_after_hash=$(sha256sum "$installed_after" 2>/dev/null | cut -d ' ' -f 1)
    echo "EXPECTED_SHA256=$EXPECTED" >> "$STATE/apk-install.log"
    echo "INSTALLED_SHA256=${installed_after_hash:-unreadable}" >> "$STATE/apk-install.log"
    [ "$installed_after_hash" = "$EXPECTED" ] || fail 'Bridge APK install failed or file verification failed. See apk-install.log; nothing is uninstalled automatically.'
    echo 'Bridge APK verified against the intended payload.'
fi
[ "$(package_version com.google.android.mosey)" = 13120 ] || fail 'Extension is missing; verify system mounts and reboot.'
dumpsys package com.google.android.mosey | grep -q 'pkgFlags=\[.*SYSTEM' || fail 'Extension has no SYSTEM identity. Check the mounting framework.'
service check com.google.android.moseyservice.IMoseyService/default | grep -q ': found' || fail 'Native Binder service is missing; see /data/adb/cmt/native.log.'
grep -q 'name="cn.google.services"' /my_product/etc/permissions/oplus_google_cn_gms_features.xml && fail 'CN feature mount is not active; reboot and retry.'
VPN_NOTE=
for vpn_client in com.sakura.app com.github.metacubex.clash.meta com.github.kr328.clash; do
    if [ -n "$(pm path "$vpn_client" 2>/dev/null)" ]; then
        VPN_NOTE='Optional VPN bypass APK is available. Enable AirDrop VPN bypass in LSPosed, scoped ONLY to your VPN client, then restart the client.'
        expected_vpn=$(cat "$MODDIR/payload/vpn.sha256")
        installed_vpn=$(pm path dev.codex.moseyvpn 2>/dev/null | sed -n 's/^package://p' | head -n 1)
        current_vpn=
        [ -z "$installed_vpn" ] || current_vpn=$(sha256sum "$installed_vpn" 2>/dev/null | cut -d ' ' -f 1)
        if [ "$current_vpn" != "$expected_vpn" ]; then
            vpn_tmp=/data/local/tmp/codex-mosey-vpn-install.apk
            if cp "$MODDIR/payload/AirDrop-VPN-bypass-test.apk" "$vpn_tmp" && chmod 644 "$vpn_tmp"; then
                vpn_output=$(pm install -r --user 0 "$vpn_tmp" </dev/null 2>&1)
                vpn_result=$?
                printf '%s\n' "$vpn_output" > "$STATE/vpn-apk-install.log"
                echo "PM_EXIT_CODE=$vpn_result" >> "$STATE/vpn-apk-install.log"
                rm -f "$vpn_tmp"
                if [ "$vpn_result" != 0 ] || ! grep -q '^Success' "$STATE/vpn-apk-install.log"; then
                    VPN_NOTE='Optional VPN bypass APK install failed; see vpn-apk-install.log. AirDrop setup continues; install the optional APK manually if needed.'
                fi
            else
                VPN_NOTE='Cannot stage optional VPN bypass APK. Install it manually if needed.'
            fi
        fi
        echo "$VPN_NOTE"
        break
    fi
done
helper() { CLASSPATH="$MODDIR/support/compat-components.dex" app_process /system/bin CompatComponents "$@"; }
if [ ! -f "$STATE/components-before.txt" ]; then
    helper snapshot > "$STATE/components-before.tmp" 2> "$STATE/helper.log" || fail 'Cannot read original GMS states; no components changed.'
    [ "$(grep -c '^com\.google\.android\.gms[^=]*=[0-4]$' "$STATE/components-before.tmp")" = 4 ] || fail 'Invalid component snapshot; no components changed.'
    mv "$STATE/components-before.tmp" "$STATE/components-before.txt" || fail 'Cannot preserve component backup.'
fi
# Do not re-enable on every boot after the successful one-time initialization.
if [ ! -f "$STATE/components-enabled" ]; then
    helper enable > "$STATE/helper.log" 2>&1 || fail 'GMS initialization failed; retain backup for rollback.'
    touch "$STATE/components-enabled"
fi
echo 'Setup complete. Enable the three LSPosed scopes, reboot, then tap Action to finish Google permissions. Transfer has not been tested on this phone.' > "$STATE/status.txt"
[ -z "$VPN_NOTE" ] || echo "$VPN_NOTE" >> "$STATE/status.txt"
cat "$STATE/status.txt"
