#!/system/bin/sh
# Sourced by customize.sh after all hardware/app checks and package checksums pass.
GMS_TARGET=264032035
gms_before=$(package_version com.google.android.gms)
case "$gms_before" in
    ''|*[!0-9]*) abort "Cannot read GMS version. Enable Google basic services first." ;;
esac
ui_print "Google Play services: detected $gms_before; target $GMS_TARGET"
pm list packages -e --user 0 com.google.android.gms 2>/dev/null | grep -qx 'package:com.google.android.gms' || abort "Google Play services is disabled for user 0. Enable Google basic services first."
if [ "$gms_before" -gt "$GMS_TARGET" ]; then
    abort "Installed GMS is newer. No downgrade/clear-data will be performed; adapt the bridge first."
fi
if [ "$gms_before" -eq "$GMS_TARGET" ]; then
    ui_print "GMS version already matches; keeping its existing split APKs."
else
    ui_print "Upgrading GMS with all 12 original Google-signed split APKs."
    ui_print "Keep this installer open. This step may take a few minutes."
    gms_dir="$MODPATH/payload/gms"
    apk_count=0
    total_size=0
    for apk in "$gms_dir"/*.apk; do
        [ -f "$apk" ] || abort "Missing GMS split APKs."
        size=$(wc -c < "$apk" | tr -d '[:space:]')
        total_size=$((total_size+size))
        apk_count=$((apk_count+1))
    done
    [ "$apk_count" = 12 ] || abort "Expected all 12 GMS APKs."
    session_result=$(pm install-create -r --user 0 -S "$total_size" </dev/null 2>&1)
    session_exit=$?
    ui_print "$session_result"
    [ "$session_exit" = 0 ] || abort "Cannot create GMS install session. Existing installation was retained."
    GMS_SESSION=$(printf '%s\n' "$session_result" | sed -n 's/.*\[\([0-9][0-9]*\)\].*/\1/p' | tail -n 1)
    case "$GMS_SESSION" in
        ''|*[!0-9]*) abort "Cannot read the GMS session ID; no APKs were written." ;;
    esac
    abandon_gms() {
        [ -z "$GMS_SESSION" ] || pm install-abandon "$GMS_SESSION" </dev/null >/dev/null 2>&1
    }
    for apk in "$gms_dir"/*.apk; do
        split_name=${apk##*/}
        size=$(wc -c < "$apk" | tr -d '[:space:]')
        ui_print "Writing $split_name"
        # Stream through a pipe: system_server cannot use a private module-file FD as stdin.
        write_result=$(cat "$apk" | pm install-write -S "$size" "$GMS_SESSION" "$split_name" - 2>&1)
        if [ "$?" != 0 ]; then
            abandon_gms
            GMS_SESSION=
            abort "GMS split write failed: $write_result"
        fi
    done
    commit_result=$(pm install-commit "$GMS_SESSION" </dev/null 2>&1)
    commit_exit=$?
    if [ "$commit_exit" != 0 ] || ! printf '%s\n' "$commit_result" | grep -q '^Success'; then
        abandon_gms
        GMS_SESSION=
        abort "GMS upgrade failed: $commit_result"
    fi
    GMS_SESSION=
    [ "$(package_version com.google.android.gms)" = "$GMS_TARGET" ] || abort "GMS upgrade returned success but version verification failed. Save this log."
    ui_print "GMS upgraded successfully. App data was retained."
    ui_print "If a later step fails, the successful GMS update remains installed."
fi
