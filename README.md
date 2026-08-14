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

![Version](https://img.shields.io/badge/version-0.2.19--beta-F2B134?style=flat-square)
![versionCode](https://img.shields.io/badge/versionCode-23-555555?style=flat-square)
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
| 🧾 摆烂记录 | 日 / 周 / 月 / 年统计、独立日期游标、长按重命名、PNG 数据图分享 |
| 🖥️ 桌面小组件 | 在桌面直接开始、暂停、继续、结束 |
| 🌓 外观 | 深色 / 浅色两套主题与独立骷髅视觉素材 |
| 🔔 应用更新 | 启动时静默检查；发现新版后可直接下载 APK 并交给系统安装程序 |
| 💾 本地数据 | 不需要账号；Release 不内置测试数据，核心记录保存在设备本地 |

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

- **日**：指定日期的总时长与具体记录
- **周**：指定自然周的 7 日柱状汇总
- **月**：按真实星期排列的像素热力图
- **年**：指定年份的 12 个月汇总

统计范围恢复为四个等宽像素按钮 `日 / 周 / 月 / 年`，切换范围比下拉菜单更直接。四个范围依然拥有**彼此独立的日期游标**：例如月视图停在 6 月时，切回日视图仍会回到日视图上次停留的日期，而不会被同步拖回 6 月。

日期导航的左右按钮仍用于逐日 / 周 / 月 / 年浏览；四个范围的快速跳转方式按粒度分别设计：

- **日 / 周**：点击中间日期打开 IdleSkull 自己的像素风日历面板，面板跟随当前深浅主题并使用 Fusion Pixel 字体。
- **月**：点击中间月份直接输入“年 + 月”，年份与月份采用等宽输入框，不必在完整日历中翻页。
- **年**：点击中间年份直接输入年份。

因此从 `8.15` 查看 `6.15` 不需要连续点击几十次，同时日 / 周 / 月 / 年仍各自保存独立游标。当前日期显示为 `今天 · M.D`，周视图显示完整起止日期。

记录默认名称为 **「未命名」**。在日视图长按记录可以重新命名；统计层会按日期边界拆分跨午夜的有效计时区间。右上角 **「导出」** 会先弹出 **黑白 / 彩色** 样式选择，再生成当前日 / 周 / 月 / 年的 PNG 数据海报并直接打开 Android 系统 **Sharesheet** 分享面板。

日视图导出采用**动态画布高度**：一天有十几段甚至更多记录时，图片会按记录数量向下延长，不再固定只画前几条。黑白模式保持像素印刷风；彩色模式使用低饱和复古配色，避免变成普通办公图表。海报不写导出时间，底部只保留一句短文案。后续仍可评估把导出入口统一迁移到设置页。

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

`0.2.6-beta` 起，小组件标题、状态和操作按钮不再依赖桌面 Launcher 自己解析自定义字体，而是由 App 使用 Fusion Pixel Font 预渲染成小尺寸位图后交给 RemoteViews，提升小米 / HyperOS 等桌面上的像素字体一致性。运行中的秒级计时继续由系统 `Chronometer` 驱动，以避免 App 每秒唤醒刷新 Widget；从 `0.2.12-beta` 起，空闲和暂停时间也改为与 Chronometer 更接近的普通数字字体，减少开始计时前后的字形突变。

`0.2.4-beta` 额外做了一轮 OEM 兼容修正：RemoteViews 只使用 Android 官方允许的布局 / View 类型，并移除了此前 XML 中的 `Space`。官方 RemoteViews 支持列表并不包含 `Space`，某些桌面宿主会因此直接显示“载入小组件失败”。小组件默认目标尺寸为 2×2，并在尺寸变化时主动重建 RemoteViews。

## 仓库忽略规则

根目录 `.gitignore` 会忽略 Android Studio / Gradle 缓存、构建产物、APK/AAB、签名文件、Python 缓存和常见系统临时文件。Fusion Pixel 字体下载缓存位于 `.gradle/idleskull-fonts/`，因此也不会被提交到仓库。

## Debug 测试数据

Debug 与 Release 使用不同 source set，但 **0.2.5-beta 起不再把测试数据写进 SharedPreferences**：

```text
app/src/debug/.../DebugDataSeeder.kt    # 仅 Debug：为统计页叠加内存样本
app/src/release/.../DebugDataSeeder.kt  # Release：原样返回真实记录
```

Debug 统计页会把确定性的半年样本与设备真实记录在内存中合并，因此即使本机已有历史数据，也能始终看到日 / 周 / 月 / 年图表效果；首页计时和真实数据本身不会被这些样本污染。Release APK 完全没有测试样本。

## 视觉路线

主页深浅色骷髅现在都经过透明像素包围盒校准：可见主体在 1024×1024 素材画布中严格水平居中，Compose 继续按 `BottomCenter` 放置。这样后续给眼窝叠加红眼、闪烁或裂纹时，不需要再为左右偏移做补偿。

主页已经启用按单次有效摆烂时长递进的**眼窝呼吸光**：满 10 分钟后出现，此后每 10 分钟增强一档。 也就是说 10 / 20 / 30 / 40 / 50 / 60 分钟会分别进入下一档，30 分钟处会从第 2 档直接切到第 3 档。深色模式使用红色眼光，浅色模式使用更高亮、更阴森的荧光绿眼；光效始终叠在固定眼窝锚点上，不修改骷髅底图。

“开裂”不会采用实时把整张 PNG 算法性撕裂的方案；如果后续确实要做，会准备数张透明裂纹 Overlay，按时间阶段叠加到同一套骷髅上。这样实现成本和性能都可控，也不会因为底图是一体图片就失去定位。

## 关于页面

`设置 → 关于` 提供：

- 当前版本
- 手动检查更新
- 更新说明
- 使用说明（`app/src/main/res/raw/usage_guide.txt`，可直接维护）
- 隐私说明
- 开源组件
- Fusion Pixel Font 字体许可
- GitHub 仓库入口

长文本内容使用独立、可滚动页面展示，而不是弹窗。

## 更新机制

IdleSkull 的在线更新只认 **GitHub Release 里的 `latest.json` 资产**，不再读取 `main/latest.json`，也不再做双源兜底。

检查流程：

```text
GitHub Releases Atom feed
        ↓
读取最前面的已发布 Release entry（包含 prerelease）
        ↓
直接请求 /releases/download/<tag>/latest.json
        ↓
比较 versionCode
        ↓
有新版本 → 系统通知 / 手动检查结果 → 用户点击后下载 APK
```

这里刻意不使用 GitHub 的 `/releases/latest` 接口，因为该接口只返回最新的非 prerelease 正式版本；IdleSkull 的 beta 版本会作为 prerelease 发布。也不再调用匿名 GitHub REST Releases API：客户端只借助公开 `releases.atom` 确定最新发布 tag，真正的版本信息仍以该 Release 自带的 `latest.json` 为唯一真相。

`latest.json` 是发布资产中的唯一更新清单。最新 Release 如果缺少该文件，更新检查会直接失败，而不会偷偷退回旧 Release 或仓库主分支中的旧清单，避免再次出现“明明发了新版但客户端读到旧版本”的情况。

更新比较始终只使用递增的整数 **`versionCode`**；`versionName` 仅用于展示。启动自动检查只在发现新版本时通知，不会后台偷跑下载；手动“检查更新”每次都会重新请求，不复用启动检查状态，也不会把一次失败缓存成后续检查结果。Release 发现不再调用 GitHub REST API，而是读取公开的 `releases.atom` feed，再直接请求最新 Release 的 `latest.json`，避免匿名 REST API 限流造成连续失败。发现新版后，“下载并安装”会读取 `latest.json` 中的 `apk.url` 下载 APK，并使用清单中的 `size` / `sha256` 校验文件，校验通过后交给 Android 系统安装程序。请求禁用本地缓存。

发布时执行 `scripts/release.py`，生成：

```text
dist/IdleSkull-vX.Y.Z-beta.apk
dist/latest.json
```

这两个文件一起上传到同一个 GitHub Release 即可。无需再把 `latest.json` 提交到 `main`，也无需 Release 后同步工作流。

## 版本规则

版本名称支持：

```text
X.Y.Z-beta
X.Y.Z
```

当前版本：

```properties
versionCode=23
versionName=0.2.20-beta
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
│       │   ├── ui/            # Compose UI / 统计 / PNG 数据图导出
│       │   ├── update/        # 更新检查与系统通知
│       │   └── widget/        # 桌面计时器小组件
│       └── res/               # Android 资源
├── app/src/debug/             # Debug 专用测试数据
├── app/src/release/           # Release 专用 no-op 调试实现
├── scripts/release.py         # Release 打包、清单生成与 Git 状态检查
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

## 第三方组件

Fusion Pixel Font Copyright © TakWolf and contributors，按 **SIL Open Font License 1.1** 使用。字体许可文本会随构建资源打包进 App，并可在「关于 → 字体许可」中查看。

---

<div align="center">

**IdleSkull · 今天你又摆烂了？**  
*Beta — 先把计时做准，再谈少摆一点。*

</div>

