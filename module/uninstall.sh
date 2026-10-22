#!/system/bin/sh
MODDIR=${0%/*}
STATE=/data/adb/cmt/oneclick
if [ -f "$STATE/components-before.txt" ]; then
    states=$(sed -n 's/^com\.google\.android\.gms[^=]*=\([0-4]\)$/\1/p' "$STATE/components-before.txt")
    count=$(printf '%s\n' "$states" | grep -c '^[0-4]$')
    if [ "$count" = 4 ]; then
        csv=$(printf '%s\n' "$states" | paste -sd ',' -)
        CLASSPATH="$MODDIR/support/compat-components.dex" app_process /system/bin CompatComponents restore "$csv" > "$STATE/restore.log" 2>&1
    fi
fi
# Preserve backups/logs. Leave the hook APK for manual removal after disabling it in LSPosed.
#!/system/bin/sh
STATE=/data/adb/cmt
if [ -f "$STATE/native.pid" ]; then
    p=$(cat "$STATE/native.pid")
    case "$p" in
      ''|*[!0-9]*) ;;
      *)
        if [ -r "/proc/$p/cmdline" ] && grep -q codex_mosey_local "/proc/$p/cmdline"; then
            kill "$p" 2>/dev/null
        fi
        ;;
    esac
fi
rm -f "$STATE/ffi_mosey.so" "$STATE/native.pid" "$STATE/native.log"
rmdir "$STATE" 2>/dev/null
