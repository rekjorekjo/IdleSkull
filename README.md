<div align="center">

# ☠️ IdleSkull

### 今天你又摆烂了？

**一个像素风的 Android 摆烂计时器。**  
记录每一次摆烂，把时间变成看得见的日志。

[![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#技术栈)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#技术栈)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](#技术栈)
[![Gradle](https://img.shields.io/badge/Gradle-9.1.0-02303A?style=for-the-badge&logo=gradle&logoColor=white)](#开发与构建)
[![GitHub](https://img.shields.io/badge/GitHub-Releases-181717?style=for-the-badge&logo=github&logoColor=white)](#更新机制)

![Version](https://img.shields.io/badge/version-0.2.3--beta-F2B134?style=flat-square)
![versionCode](https://img.shields.io/badge/versionCode-6-555555?style=flat-square)
![Status](https://img.shields.io/badge/status-Beta-E67E22?style=flat-square)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?style=flat-square)
![targetSdk](https://img.shields.io/badge/targetSdk-36-3DDC84?style=flat-square)

</div>

> [!IMPORTANT]
> IdleSkull 当前处于 **Beta 测试阶段**。现阶段优先验证长时间计时、暂停恢复、跨日统计、小组件同步以及更新提醒的稳定性。

## 功能概览

| 模块 | 能力 |
| --- | --- |
| ⏱️ 正计时 | 开始、暂停、继续、结束 |
| ⌛ 倒计时 | 自定义时 / 分 / 秒，并记住上一次设置 |
| 🧾 摆烂记录 | 日 / 周 / 月 / 年统计，日记录支持长按重命名 |
| 🖥️ 桌面小组件 | 在桌面直接开始、暂停、继续、结束 |
| 🌓 外观 | 深色 / 浅色两套主题与独立骷髅视觉素材 |
| 🔔 更新提醒 | 启动时静默检查；发现新版本仅发送系统通知 |
| 💾 本地数据 | 不需要账号，不内置演示数据，核心记录保存在设备本地 |

## 产品交互

### 计时

首页保持单一计时入口，通过同宽模式选择器切换：

- `正计时`
- `倒计时`

选择倒计时后输入：

```text
__ 时  __ 分  __ 秒
```

计时开始后模式选择器会锁定，必须结束当前计时后才能切换模式。计时状态会持久化，App 进程结束后仍可恢复；暂停区间不会计入有效摆烂时间。

### 摆烂记录

底部导航名称为 **「日志」**，页面标题为 **「摆烂记录」**。

- **日**：总时长、次数、最长一次以及具体记录
- **周**：最近 7 天汇总
- **月**：像素热力图
- **年**：12 个月汇总

记录默认名称为 **「未命名」**。在日视图长按记录可以重新命名；统计层会按日期边界拆分跨午夜的有效计时区间。

## 视觉与字体

IdleSkull 采用黑白骷髅 + 像素 UI 的视觉语言：

- 浅色模式使用独立的高对比黑色骷髅素材
- 深色模式使用独立的白色骷髅素材
- 不使用红色装饰作为常规视觉元素
- 按钮、选择器、卡片等几何控件采用硬边 / 像素化处理

界面中文主要使用 **Fusion Pixel Font / 缝合像素字体**：

- Upstream: `TakWolf/fusion-pixel-font`
- Version: `2026.08.11`
- Variant: `12px Proportional`
- Glyph set: 优先 `zh_hans`
- License: `SIL Open Font License 1.1`

字体二进制**不会提交到仓库**。AGP 构建任务会从固定版本的官方 GitHub Release 下载字体，并把字体与 OFL 许可作为生成资源加入当前 Variant。缓存位置：

```text
.gradle/idleskull-fonts/
```

因此第一次构建需要能够访问 GitHub；后续会复用本地缓存。

## 桌面小组件

「计时器」小组件与 App 共用同一套计时状态，支持：

- 开始
- 暂停
- 继续
- 结束
- 深色 / 浅色外观
- Fusion Pixel 字体

小组件只是计时状态的操作入口，不维护第二套独立计时数据，避免 App 与桌面状态不同步。

## 关于页面

`设置 → 关于` 提供：

- 当前版本
- 手动检查更新
- 更新说明
- 使用说明
- 隐私说明
- 开源组件
- Fusion Pixel Font 字体许可
- GitHub 仓库入口

长文本内容使用独立、可滚动页面展示，而不是弹窗。

## 更新机制

IdleSkull 采用轻量的 GitHub Release 更新机制，原则是 **只提醒，不自动下载**。

```text
App 启动
   ↓
后台静默读取 main/latest.json
   ↓
比较 versionCode
   ↓
发现更高版本
   ↓
发送系统通知
   ↓
用户主动打开对应 GitHub Release
```

自动检查不会修改关于页的手动检查状态。用户点击「检查更新」时，会发起一次新的请求并显示：

- 正在检查
- 已是最新版本
- 发现新版本
- 检查失败

默认更新清单：

```text
https://raw.githubusercontent.com/rekjorekjo/IdleSkull/main/latest.json
```

请求使用 cache-busting 参数以及 `no-cache / no-store`，降低 GitHub Raw / CDN 返回旧清单的风险。

### 发布说明

根目录：

```text
release-notes.md
```

是发布说明的唯一人工维护来源。每次 Android 构建前会自动转换为：

```text
app/src/main/res/raw/update_notes.txt
```

### 打包发布

准备好签名 APK 后：

```bash
python scripts/release.py --apk /path/to/IdleSkull-release.apk
```

脚本生成：

```text
dist/IdleSkull-v<version>.apk
dist/latest.json
latest.json
```

APK 发布到对应 GitHub Release 后，再将根目录 `latest.json` 提交到 `main`。这样 Beta Release 即使标记为 **Pre-release**，仍然可以被 App 检查到。

## 版本规则

版本名称支持：

```text
X.Y.Z-beta
X.Y.Z
```

当前版本：

```properties
versionCode=6
versionName=0.2.3-beta
```

更新判断只比较递增的 **`versionCode`**；`versionName` 负责用户可读的版本展示。

## 技术栈

| 技术 | 用途 |
| --- | --- |
| ![Android](https://img.shields.io/badge/-Android-3DDC84?logo=android&logoColor=white) | Android 平台，minSdk 26 / targetSdk 36 |
| ![Kotlin](https://img.shields.io/badge/-Kotlin-7F52FF?logo=kotlin&logoColor=white) | 主要开发语言 |
| ![Compose](https://img.shields.io/badge/-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white) | App UI 与状态驱动界面 |
| ![AndroidX](https://img.shields.io/badge/-AndroidX-3DDC84?logo=android&logoColor=white) | Activity、Lifecycle 等基础组件 |
| ![Gradle](https://img.shields.io/badge/-Gradle-02303A?logo=gradle&logoColor=white) | 构建、生成资源与发布说明同步 |
| ![Python](https://img.shields.io/badge/-Python-3776AB?logo=python&logoColor=white) | Release APK / `latest.json` 打包脚本 |
| ![GitHub](https://img.shields.io/badge/-GitHub-181717?logo=github&logoColor=white) | 源码、Release 与更新清单托管 |

### 构建基线

```text
JDK          17
AGP          9.0.0
Gradle       9.1.0
compileSdk   36
targetSdk    36
minSdk       26
Compose BOM  2026.06.00
```

AGP 9 下，字体等任务生成的 Android 资源通过 Variant API 的 `addGeneratedSourceDirectory()` 接入，而不是把 Gradle `Provider` 直接添加到旧 `sourceSets.res.srcDir()`。

## 工程结构

```text
IdleSkull/
├── app/
│   └── src/main/
│       ├── java/com/idleskull/app/
│       │   ├── data/          # 计时与本地记录
│       │   ├── model/         # 领域模型
│       │   ├── ui/            # Compose UI / 统计
│       │   ├── update/        # 更新检查与系统通知
│       │   └── widget/        # 桌面计时器小组件
│       └── res/               # Android 资源
├── scripts/release.py         # Release 打包与 latest.json
├── release-notes.md           # 发布说明唯一人工维护来源
├── version.properties         # versionCode / versionName
└── README.md
```

## 开发与构建

1. 使用 JDK 17 打开工程。
2. 安装 Android SDK 36。
3. Gradle 使用项目 Wrapper。
4. 第一次构建保持网络可访问 GitHub，以下载固定版本 Fusion Pixel Font。
5. 执行 Android Studio `Build → Rebuild Project` 或使用项目 Gradle Wrapper 构建。

> [!NOTE]
> 字体下载任务的输出通过 AGP Variant API 注册为 generated Android resources，因此资源合并会自动依赖对应任务；无需手动给 `merge*Resources` 添加依赖。

## Beta 测试重点

- 长时间计时准确性
- 多次暂停 / 继续后的有效时长
- 跨午夜记录拆分
- 进程结束后的计时恢复
- 倒计时切后台 / 锁屏行为
- App 与桌面小组件的状态一致性
- 深浅色主题与骷髅素材显示
- Fusion Pixel 中文在不同屏幕密度下的可读性
- 日志重命名的保存与恢复
- 自动 / 手动更新检查

## 暂不计划

- 小米 / vivo / OPPO / 荣耀等厂商岛能力
- 自动下载或自动安装 APK
- 账号系统
- 云同步

## 第三方组件

Fusion Pixel Font Copyright © TakWolf and contributors，按 **SIL Open Font License 1.1** 使用。字体许可文本会随构建资源打包进 App，并可在「关于 → 字体许可」中查看。

---

<div align="center">

**IdleSkull · 今天你又摆烂了？**  
*Beta — 先把计时做准，再谈少摆一点。*

</div>
