# 安装与排查

这是实验源码仓库，没有可直接刷入的公开完整包。先按 BUILD.md 在本地构建并准备依赖。

## 前提

核对 README 的完整基线；启用系统 Google 基础服务，准备 Root、system 覆盖挂载与已激活的 LSPosed。
不能只安装 extension APK 就认为获得系统权限。挂载生效后应在其包信息中出现 SYSTEM 和 PRIVILEGED。
GMS 必须是完整分包安装，单独安装 base.apk 会出现 INSTALL_FAILED_MISSING_SPLIT。

## 原始 0.6 流程

1. 在 Root 管理器安装本地构建的模块 ZIP，检查输出后重启。
2. 首次解锁后等待桥接 APK 安装。
3. 在 LSPosed 启用 Mosey compatibility test，只勾选 Google Play 服务、Quick Share extension 和一加互传。
4. 再次重启、解锁，点 Root 模块的 Action 完成 Google 分享所需设置。
5. 在扩展的应用权限中允许附近设备及系统要求的本地网络访问。保持 Wi-Fi、蓝牙开启。
6. iPhone 解锁并将 AirDrop 设为“所有人，10 分钟”，先用无隐私小图片双向测试。
7. 初次排查先完全断开 VPN 连接。独立 VPN 补丁需要在 LSPosed 中仅勾选受支持的 VPN 客户端并重启客户端。

0.6 的启动脚本可能因开机时包管理服务暂不可读而提前退出；后续 0.6.1 修正不包含在本次原始脚本导出中。
新构建 APK 使用自己的签名，不能覆盖原测试签名；不要为了覆盖失败自动卸载用户已有应用。

## 日志

安装失败重点看 `/data/adb/cmt/oneclick/apk-install.log`、`setup.log`。
服务问题看 `/data/adb/cmt/native.log`，Hook 问题导出 LSPosed 日志并检查作用域。
`Setup complete` 只表示初始化步骤通过，不表示已验证双向传输。
`Receive provider BLE scanning: true` 也不等于 iPhone 已能发现手机。

可选：将 `tools/collect-airdrop.sh` 放到设备，通过已授权的 Root shell 运行，输出位于
`/sdcard/Download/ColorOS-AirDrop-diagnostics.txt`。收集器不修改传输配置，但会读取应用及运行日志，分享前请脱敏。

## 回退

先停用本模块及对应 LSPosed 模块，再重启。卸载脚本会尝试恢复保存的组件状态。
GMS 安装更新不会随模块自动回退；不要清除 GMS 数据或跨版本强制降级来试错。
