# IdleSkull 0.2.19-beta

- 修复更新交互：发现新版本后不再只提供“打开 GitHub Release”，主按钮改为“下载并安装”。
- APK 直接使用最新 Release 的 `latest.json` 中 `apk.url` 下载，并校验文件大小和 SHA-256。
- 下载完成后直接唤起 Android 系统安装程序；不会后台自动下载或静默安装。
- 新版本系统通知点击后也直接指向 Release 中的 APK 下载地址。
- 版本更新为 0.2.19-beta（versionCode 22）。
