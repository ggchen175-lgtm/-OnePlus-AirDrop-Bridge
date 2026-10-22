#!/system/bin/sh
MODDIR=${0%/*}
echo 'Checking installation. Details: /data/adb/cmt/oneclick/setup.log'
/system/bin/sh "$MODDIR/setup.sh"
result=$?
cat /data/adb/cmt/oneclick/status.txt 2>/dev/null
[ "$result" = 0 ] || exit "$result"
echo 'Verify LSPosed scopes first: GMS / Quick Share extension / OnePlus Share.'
echo 'Complete Google permissions, press Back, then share an image using OnePlus Share.'
am start --user 0 -n com.google.android.gms/.nearby.sharing.main.MainActivity
