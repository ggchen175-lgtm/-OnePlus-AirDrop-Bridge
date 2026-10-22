#!/system/bin/sh
MODDIR=${0%/*}
[ "$(getprop ro.product.model)" = PLK110 ] || exit 0
[ "$(getprop ro.build.version.sdk)" = 37 ] || exit 0
[ "$(uname -r)" = '6.12.69-android16-6-g242e1a07f878-ab15865446-4k' ] || exit 0
TARGET=/my_product/etc/permissions/oplus_google_cn_gms_features.xml
if [ -f "$TARGET" ] && [ -f "$MODDIR/cn-google-features.xml" ]; then
    mount -o bind "$MODDIR/cn-google-features.xml" "$TARGET"
fi
