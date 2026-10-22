# 本地构建

需要 Python 3、JDK 17、Android SDK platform android-36、包含 D8 和 apksigner 的 Android build-tools、Xposed API 82 编译依赖，以及 Android NDK。
工具路径通过参数传入，没有作者电脑的绝对路径。

## 1. 编译自有模块

```sh
python tools/build.py --android-jar /path/to/android-36/android.jar --d8-jar /path/to/build-tools/lib/d8.jar --xposed-api /path/to/xposed-api-82.jar
```

Windows 可增加 `--java C:/path/to/jdk/bin/java.exe` 并将上述参数换成自己的 SDK 路径。
输出 `dist/bridge-unsigned.apk`、`dist/vpn-unsigned.apk`、`dist/compat-components.dex`。
若需要 JPEG-2000 接收前预览，增加 `--jp2-so /path/to/arm64-v8a/libopenjpeg.so`。
该 JNI 必须与 `dev.keiji.jp2:jp2-android:1.0.4` Java 接口匹配，并携带 `licenses/` 下的许可证。
本仓库不自动联网下载依赖，也不包含原项目的本地预览测试图片。

## 2. 自行签名

创建自己的测试密钥，然后用 Android SDK 的 apksigner 签名。示例会交互询问密码，不要把密码或密钥提交到仓库。

```sh
keytool -genkeypair -keystore local.jks -alias local -keyalg RSA -keysize 2048 -validity 3650
apksigner sign --ks local.jks --out dist/bridge.apk dist/bridge-unsigned.apk
apksigner sign --ks local.jks --out dist/vpn.apk dist/vpn-unsigned.apk
apksigner verify --verbose dist/bridge.apk
apksigner verify --verbose dist/vpn.apk
```

新签名不能覆盖使用原私有签名的测试 APK。组包工具只复制指定文件并计算哈希，不替你验证签名；先完成上面的验证。

## 3. 编译进程内 Binder 库

使用 NDK 的 arm64 Android clang；替换本机 NDK 路径。示例为 Linux 主机，Windows 使用对应的 `.cmd` 编译器入口。

```sh
/path/to/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android36-clang -shared -fPIC -nostdlib -O2 native/mosey_local_binder.c -o dist/libmosey_local_binder.so
```

## 4. 准备私有依赖

仅在本地 `private-inputs/` 准备下列文件，路径与原模块一致。仓库不提供它们，也不提供完整固件或 Google APK 下载镜像。

- `native/mosey_server`：本地服务二进制。原基线将其中唯一的 `/odm/lib64/libmosey_daemon_ffi.so` 字符串替换为以 `/` 补齐相同字节长度的 `/data/adb/cmt/ffi_mosey.so`；这属于对用户自己固件副本的本地准备。
- `native/libmosey_daemon_ffi.so`。
- `system/priv-app/MoseyApp/MoseyApp.apk`。
- `system/etc/permissions/privapp-permissions-com.google.android.mosey.xml`。
- `system/etc/default-permissions/default-permissions-com.google.android.mosey.xml`。
- `payload/gms/` 下全部 12 个 APK，路径与 `reference/files.json` 一致。
- `cn-google-features.xml`：匹配设备的 CN Google features 文件副本，仅移除 `cn.google.services` feature；不要复制不匹配设备的配置。

`reference/proprietary-sha256.json` 给出基线校验值。服务的哈希是原模块中已经完成路径替换的版本，不是原固件未修改版本。
整个私有输入目录及所有编译产物都已列入 `.gitignore`。

## 5. 本地组包

```sh
python tools/assemble.py --inputs private-inputs --bridge-apk dist/bridge.apk --vpn-apk dist/vpn.apk --binder-so dist/libmosey_local_binder.so --helper-dex dist/compat-components.dex
```

输出 `dist/OnePlus-AirDrop-Bridge-0.6-local.zip`。会检查私有依赖哈希、生成 APK 哈希与整包校验表；任何缺失或基线不同的输入会停止组包。
这是重新构建的本地实验包，未声称与原 0.6 ZIP 完全一致，也未经过新设备上的传输验证。
**输出包含私有组件，只供本地测试，不要上传 GitHub 或附到 Issue / Release。**
