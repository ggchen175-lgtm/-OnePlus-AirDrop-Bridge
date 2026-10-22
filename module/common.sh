#!/system/bin/sh
package_version() {
    dumpsys package "$1" 2>/dev/null | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1
}
user_unlocked() {
    [ "$(am get-started-user-state 0 2>/dev/null)" = RUNNING_UNLOCKED ]
}
check_platform() {
    CHECK_ERROR=
    [ "$(getprop ro.product.model)" = PLK110 ] || CHECK_ERROR="Only CN OnePlus 15 PLK110 is supported."
    [ -z "$CHECK_ERROR" ] || return 1
    [ "$(getprop ro.build.version.sdk)" = 37 ] || CHECK_ERROR="Requires Android 17 / SDK 37."
    [ -z "$CHECK_ERROR" ] || return 1
    [ "$(getconf PAGESIZE)" = 4096 ] || CHECK_ERROR="Requires 4096-byte pages."
    [ -z "$CHECK_ERROR" ] || return 1
    [ "$(uname -r)" = '6.12.69-android16-6-g242e1a07f878-ab15865446-4k' ] || CHECK_ERROR="Kernel differs from the tested baseline."
    [ -z "$CHECK_ERROR" ] || return 1
    [ "$(package_version com.coloros.oshare)" = 17002036 ] || CHECK_ERROR="Requires OnePlus Share 17.2.36 (17002036)."
    [ -z "$CHECK_ERROR" ] || return 1
    feature_file=/my_product/etc/permissions/oplus_google_cn_gms_features.xml
    [ -r "$feature_file" ] || { CHECK_ERROR="CN Google feature file is missing."; return 1; }
    feature_names=$(sed -n 's/.*<feature[[:space:]][^>]*name="\([^"]*\)".*/\1/p' "$feature_file")
    [ -n "$feature_names" ] || { CHECK_ERROR="Cannot recognize CN feature XML."; return 1; }
    for feature_name in $feature_names; do
        case "$feature_name" in
            cn.google.services|com.google.android.feature.services_updater) ;;
            *) CHECK_ERROR="Unexpected CN Google features; adapt before installation."; return 1 ;;
        esac
    done
    return 0
}
check_baseline() {
    check_platform || return 1
    gms_version=$(package_version com.google.android.gms)
    [ "$gms_version" = 264032035 ] || { CHECK_ERROR="Requires GMS 264032035; detected ${gms_version:-unreadable}."; return 1; }
    return 0
}
