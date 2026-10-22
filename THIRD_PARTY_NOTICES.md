# Third-party components

## JP2ForAndroid / jp2-android

`bridge/src/com/gemalto/jp2/JP2Decoder.java` originates from the source archive of
`dev.keiji.jp2:jp2-android:1.0.4`:
https://repo.maven.apache.org/maven2/dev/keiji/jp2/jp2-android/1.0.4/

Upstream project: https://github.com/ThalesGroup/JP2ForAndroid

License: BSD 2-clause, Copyright (c) 2018 Gemalto s.r.o.
Full terms: `licenses/LICENSE-JP2ForAndroid.txt`.
Local modifications remove AndroidX annotations and load the JNI library through
the bridge module's native library path. This code is not covered by this repository's MIT license.

The JNI `libopenjpeg.so` is not committed. If supplied locally for a build, its
JP2ForAndroid and OpenJPEG licenses must accompany the resulting APK. OpenJPEG
terms are retained in `licenses/LICENSE-OpenJPEG.txt`.

## Compile-time dependencies

Android SDK / build tools and Xposed API 82 are obtained separately, not committed.
Xposed API reference repository: https://github.com/rovo89/XposedBridge

## Proprietary runtime dependencies

Google Play services, Quick Share extension (`com.google.android.mosey`),
OEM `mosey_server`, `libmosey_daemon_ffi.so` and OEM permission XML are not distributed.
Users must supply compatible files from their own authorized local environment.
Checksums identify the experimental baseline; they are not a license to redistribute.
No decompiled Google or ColorOS application source is included.
