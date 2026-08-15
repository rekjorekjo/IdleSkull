<div align="center">

# ☠️ IdleSkull

### 今天你又摆烂了？

**一个把“开卷”和“摆烂”做成骷髅血条对抗的像素风 Android 计时器。**  
你卷的每一秒都在造成伤害；你摆的每一秒都在给它回血。

[![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#技术栈)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#技术栈)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](#技术栈)
[![Gradle](https://img.shields.io/badge/Gradle-9.1.0-02303A?style=for-the-badge&logo=gradle)](#开发与构建)
[![GitHub](https://img.shields.io/badge/GitHub-Releases-181717?style=for-the-badge&logo=github)](#更新机制)

![Version](https://img.shields.io/badge/version-0.3.0--beta-F2B134?style=flat-square)
![versionCode](https://img.shields.io/badge/versionCode-29-555555?style=flat-square)
![Status](https://img.shields.io/badge/status-Beta-E67E22?style=flat-square)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?style=flat-square)
![targetSdk](https://img.shields.io/badge/targetSdk-36-3DDC84?style=flat-square)

</div>

> [!IMPORTANT]
> `0.3.0-beta` 是一次数据模型重构。Beta 阶段不兼容 0.2.x 的旧计时记录；骷髅游戏规则升级时也不迁移旧的 1800 HP / 未取整血量进度，直接从 Lv.1 / 10000 HP 重新开始。

## 0.3.0：卷与摆

IdleSkull 现在不再只是“摆烂计时器”。首页有两个互斥入口：

```text
[ 开摆！ ]   [ 开卷！ ]
```

- **开卷**：每 1 秒对当前骷髅造成 1 HP 伤害。
- **开摆**：每 1 秒给当前骷髅恢复 1 HP，最多恢复到当前等级满血。
- 骷髅 HP 归零后立即进入下一级，并生成更高的最大 HP。
- 一次长时间“开卷”可以连续击破多个等级。
- 摆烂不会让已击破的等级倒退，也不会复活旧等级。

当前 HP 曲线使用对数增长。Lv.1 固定为 `10000 HP`，Lv.2 起先计算：

```text
rawHP(n) = 10000 × ln(n^π)
         = 10000 × π × ln(n)
HP(n)    = floor(rawHP(n) / 1000) × 1000
```

也就是把结果向下抹到整千：Lv.2 = 21000、Lv.3 = 34000、Lv.10 = 72000、Lv.100 = 144000。1 HP 仍对应 1 秒有效时间。代码不会每秒写磁盘；HP 由有效计时区间按时间差计算，暂停、恢复和 App 被系统回收后仍可正确结算。

少于 **1 分钟** 的整段“卷 / 摆”视为误触：结束时不写入日志、不计入统计，同时撤销这一段尚未持久化的骷髅伤害 / 回血。

## 功能概览

| 模块 | 能力 |
| --- | --- |
| ⚔️ 开卷 | 正计时 / 倒计时、暂停、继续、结束；每秒削减骷髅 HP |
| 💤 开摆 | 正计时 / 倒计时、暂停、继续、结束；每秒恢复骷髅 HP |
| ☠️ 骷髅等级 | 等级、当前 HP / 最大 HP、对数成长血量与击败动画 |
| 🧾 日志 | 摆 / 卷切换，日 / 周 / 月 / 年统计，独立日期游标，长按重命名 |
| 🖼️ 导出 | 设置页独立“导出记录”，选择摆 / 卷、范围、日期、黑白 / 彩色后分享 PNG |
| 🖥️ 桌面小组件 | 继续使用系统 Chronometer 秒级刷新；0.3.0 首版从空闲状态启动时默认开始“摆” |
| 🌓 外观 | 浅色 / 深色；深色红眼、浅色阴森绿眼，光效强度由当前 HP 驱动 |
| 🔔 更新 | 沿用 The Day 风格：固定 latest.json 主路径、GitHub latest 单次兜底、DownloadManager 下载 |

## 首页

顶部显示当天两个方向的有效时间：

```text
今天  摆 1h 12m · 卷 2h 46m
```

首页不再显示“今天你卷还是摆？”标题，只保留当天汇总、计时器和操作区。`SKULL Lv.x`、血条与实际血量移动到操作按钮和底部骷髅之间：

```text
[ 暂停 ]   [ 结束 ]

SKULL Lv.7
████████░░░░
42000/61000

        ☠️
```

血量数字直接显示为 `当前 HP/最大 HP`，不额外显示百分比；“本次已击破 x 个骷髅”提示已移除。

眼窝光效不再按“单次摆烂 10 / 20 / 30 分钟”分档，而是直接反映当前骷髅生命状态：HP 越高越亮，开卷会逐渐压暗，摆烂会逐渐恢复。深色模式继续使用红眼，浅色模式使用高亮阴森绿眼。为了减少无意义的持续重绘，眼光不再做常驻呼吸脉冲。

运行中的主计时数字仍按秒刷新；骷髅 HP / 血条约每 **5 秒**刷新一次，顶部“今天 摆 / 卷”汇总约每 **10 秒**刷新一次。空闲或暂停时不会保留秒级轮询；秒级 tick 也不再每次读取 SharedPreferences。

## 日志

底部导航与页面标题统一为 **「日志」**。摆 / 卷选择器移动到标题栏右上角，并改成和首页计时模式选择器相同的紧凑下拉样式：

```text
日志                         [ 摆 ▼ ]

[ 日 | 周 | 月 | 年 ]
‹      当前日期 / 周 / 月 / 年      ›
```

“摆”和“卷”分别统计，不把两种时间混在一起。日视图单条记录显示名称、类型、起止时间和右对齐时长；长按可改名，空名称归一为“未命名”。跨午夜仍按实际日期边界拆分统计。

日 / 周使用主题化像素日历跳转；月使用等宽的“年 / 月”输入；年直接输入年份。四种范围仍各自保留独立日期游标。

## 导出与分享

导出入口从日志页移到：

```text
设置 → 数据 → 导出记录
```

独立页面可以选择：

- 记录类型：摆 / 卷
- 统计范围：日 / 周 / 月 / 年
- 日期
- 黑白 / 彩色

生成后直接进入 Android Sharesheet。日视图仍根据记录数量动态增长画布高度；周统计使用较轻的局部条形图；月 / 年继续使用现有像素统计结构。导出标题会根据类型使用“摆烂记录”或“开卷记录”。

## 桌面小组件

小组件继续与 App 共享同一个 ActiveTimer，并使用系统 `Chronometer` 负责运行中的秒级数字刷新，避免 App 每秒重建 RemoteViews。

0.3.0 首版暂不在桌面小组件内增加“摆 / 卷”二选一入口：**从空闲状态点击小组件开始，默认创建一段“摆”记录**；如果 App 内已经在“卷”，小组件仍可暂停、继续和结束这段计时。后续再单独设计不容易误触的 Widget 交互。

## Debug 测试数据

Debug 与 Release 使用不同 source set。Debug 只在统计层叠加确定性的内存样本，并同时生成“摆”和“卷”数据；不会写入真实记录，也不会污染首页骷髅进度。Release 完全不注入测试样本。

## 字体与视觉

中文 UI 继续使用 **Fusion Pixel Font 12px Proportional（简体中文）**。字体在构建时下载并缓存，不把字体二进制直接提交进仓库。

骷髅素材继续使用独立的浅 / 深色 PNG，并保持主体水平居中。眼窝光效作为独立绘制层叠加。

击败骷髅时会播放全屏 Compose 动画：骷髅从下方冲到中央并放大，浅色模式用黑色、深色模式用白色的墨团从中央扩散；随后绿 / 红主题色光团用约 3.25 秒从中央扩散到覆盖整个屏幕，再显示带固定裂纹 Overlay 的骷髅。原先的斜向书法“提”改为黑 / 白不规则泼墨与飞溅墨点，最后以逐字重击的打字机效果敲出 `DEFEATED`。裂纹仍是独立绘制层，不永久修改原始骷髅素材。动画只是遮住画面，计时和新等级骷髅的伤害仍在后台继续结算，动画结束后直接显示最新状态。

## 更新机制

更新机制继续沿用已经稳定的 The Day 思路，而不是自行扫描 Atom / Releases 列表：

```text
固定 latest.json
      ↓
失败时 GitHub /releases/latest 单次兜底
      ↓
比较版本
      ↓
用户主动“下载并安装”
      ↓
Android DownloadManager
      ↓
size + SHA-256 校验
      ↓
系统安装器
```

Beta 仍使用 `0.3.0-beta / v0.3.0-beta` 命名，但为了让 GitHub `/releases/latest` 能识别，GitHub Release 本身不要勾选 **Set as a pre-release**。客户端不会后台自动下载 APK。

## 技术栈

- Kotlin
- Jetpack Compose
- Android Gradle Plugin 9.0.0
- Gradle 9.1.0
- JDK 17
- compileSdk 36
- targetSdk 36
- minSdk 26

## 开发与构建

版本统一读取根目录：

```properties
versionCode=29
versionName=0.3.0-beta
```

发布说明唯一人工维护来源为根目录 `release-notes.md`。发布脚本负责生成 Release 所需的 APK 元数据与 `latest.json`。

## 项目结构

```text
app/src/main/
├── java/com/idleskull/app/
│   ├── data/          # 本地计时、记录与骷髅进度
│   ├── model/         # ActivityType / Timer / SkullState / TimeSession
│   ├── ui/
│   │   ├── screens/   # 首页、日志、导出、设置、关于
│   │   ├── export/    # PNG 导出与分享
│   │   └── components/
│   ├── update/        # The Day 风格应用内更新
│   └── widget/        # 桌面计时器小组件
└── res/
    ├── raw/usage_guide.txt
    └── raw/update_notes.txt
```

## License

见仓库中的 `LICENSE` 与第三方许可说明。

---

*Beta — 你摆的每一秒，都在给它回血；你卷的每一秒，都在把它打回地狱。*
