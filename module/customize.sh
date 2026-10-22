#!/system/bin/sh
. "$MODPATH/common.sh"
ui_print "OnePlus 15 AirDrop combined test installer 0.6 (verified APK installation)"
(cd "$MODPATH" && sha256sum -c SHA256SUMS.txt >/dev/null 2>&1) || abort "Package checksum failed; download/copy the complete ZIP again."
check_platform || abort "$CHECK_ERROR"
ui_print "Model/kernel/OShare baseline passed."
ui_print "Requires active LSPosed + system mounting support."
ui_print "After reboot: unlock, enable Mosey compatibility test"
ui_print "Scope ONLY: GMS / Quick Share extension / OnePlus Share."
set_perm_recursive "$MODPATH" 0 0 0755 0644
for script_file in "$MODPATH"/*.sh; do
    set_perm "$script_file" 0 0 0755
done
set_perm "$MODPATH/native/mosey_server" 0 0 0755
. "$MODPATH/install-gms.sh"
check_baseline || abort "$CHECK_ERROR"
ui_print "Bridge APK installs automatically after first unlock."
ui_print "Then set LSPosed scope, reboot, tap this module's Action."
ui_print "Read README-zh.md before testing. No reboot is triggered."
