# OnePlus AirDrop Bridge

国行一加 15 上的 Quick Share / AirDrop 兼容与 ColorOS 互传界面桥接实验。

本仓库公开自编写的 LSPosed 模块、进程内 Binder 兼容库、Root 安装脚本与构建工具。
实际无线传输依赖 Google Quick Share extension 和固件中的私有服务，**不是从零实现的开源 AirDrop 协议栈**。
仓库不提供 Google Play 服务 APK、Quick Share extension APK、厂商传输库、完整刷入包或原项目签名私钥。

## 当前状态

原机已人工验证双向传图：在一加互传列表发现 iPhone、从原界面发送、通过 ColorOS 流体云接受和查看文件、图片预览及绿色完成勾。
另有独立的 VPN 排除补丁，原机在 SakuraCat 上验证过；其他列入代码的客户端尚无同等验证。

**迁移仍是实验状态。** 其他同版本一加 15 已反馈 APK 安装失败、服务启动或双向搜索失败；不能把原机成功视为所有一加 15 均可直接安装使用。
OPPO / ColorOS 16 的后续适配没有完成双向发现验证，不属于这里的 0.6 基线。

| 项目 | 原机基线 |
| --- | --- |
| 型号 | 一加 15 国行 PLK110 |
| 系统 | PLK110_17.0.0.105(CN01)，Android 17 / SDK 37 |
| 内核 | `6.12.69-android16-6-g242e1a07f878-ab15865446-4k` |
| 架构 / 页大小 | arm64 / 4096 |
| 一加互传 | 17.2.36 / 17002036 |
| Google Play 服务 | 26.40.32 / 264032035，完整 12 个 APK 分包 |
| Quick Share extension | 1.0.864840262 / 13120 |
| 环境 | Root、可用 system 挂载、Zygisk、LSPosed |

## 实现方式

1. 将匹配固件的私有扩展和用户态服务放入 Root 模块的系统覆盖层。
2. 自编写的 `mosey_local_binder.c` 仅在服务进程中兼容 Binder stability 标记；扩展侧 Hook 将声明服务查询转到本地 Binder 查询。
3. 在指定 GMS 版本上调整外部分享 provider 的能力判断，并处理扩展临时 TLS 密钥库兼容。
4. 用 LSPosed 连接 GMS provider 与 `com.coloros.oshare`，把发现的设备、发送操作和接收状态接入原互传界面。
5. 使用互传自身流体云卡片显示请求、进度、完成与查看操作；解码 JPEG-2000 缩略图并提供预览 URI。
6. 可选 VPN 模块在受支持客户端建立 VPN 时排除扩展，避免本地传输流量进入 VPN。

这些 Hook 依赖版本相关的内部类名与方法。升级 GMS、互传或系统后需要重新核对，不能靠删除版本检查获得兼容性。

## 仓库结构

- `bridge/src/`：ColorOS 17 桥接模块及预览代码。
- `vpn/src/`：独立 VPN 兼容模块，仅作用于指定代理客户端。
- `native/`：自编写的进程内 Binder 兼容库源码。
- `support/`：读取、保存和恢复 GMS 组件启用状态的辅助工具。
- `module/`：从 0.6 完整包提取的自编写安装、开机、卸载脚本。
- `tools/`：可移植构建、组包、日志收集工具。
- `reference/`：原包哈希、依赖校验值和源码来源记录，无 APK 或设备日志。
- `licenses/`：第三方解码器及 OpenJPEG 许可证。

## 构建与使用

参见 [构建说明](docs/BUILD.md)、[安装与排查](docs/INSTALL.md) 和 [第三方依赖说明](THIRD_PARTY_NOTICES.md)。

此源码导出保留 0.6 安装脚本行为，**不包含后续 0.6.1 的开机重试修正**；Java 来自维护中的源码及 ColorOS 17 适配前备份，未声称与归档 APK 的 DEX 逐字节相同。来源与校验见 `reference/source-origin.json`。
发布的源码构建产物需要重新编译、签名并测试。新签名无法直接覆盖原私有测试签名的 APK。

日志中可能包含设备名、应用列表等信息。提交 Issue 前请自行脱敏，不要上传完整系统日志或账号数据。

## 许可证

自编写代码采用 [MIT](LICENSE)。`JP2Decoder.java` 及 OpenJPEG 适用各自的 BSD 许可证，详见 `licenses/`。
本仓库许可证不授予 Google / OEM 私有二进制的再分发权。AirDrop、Quick Share、OnePlus 等名称属于各自权利人，本项目与其无隶属关系。
