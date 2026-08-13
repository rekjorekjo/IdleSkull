# IdleSkull

中文产品名：**今天你又摆烂了？**

IdleSkull 是一个记录“摆烂时间”的 Android App。项目当前处于 **Beta 测试阶段**：先让计时、统计、桌面小组件和更新提醒在真实日常使用中稳定下来，再根据连续几天的使用结果继续调整边界行为与视觉。

当前版本：**0.2.0-beta**

## 当前功能

- 正计时：开始 / 暂停 / 继续 / 结束
- 倒计时：首页通过单一模式选择器进入，使用“时 / 分 / 秒”自定义时长
- 自动记住上一次倒计时时长
- 计时状态持久化，App 被系统结束后重新打开仍可恢复
- 使用 active segments 记录真实运行区间：暂停时间不会计入统计，并支持跨日正确切分
- 日 / 周 / 月 / 年统计
- 月统计像素热力图
- 深色 / 浅色两种 App 主题
- 首页底部大尺寸海盗旗骷髅背景，深浅色使用不同设计
- 海盗旗骷髅 Adaptive Icon：系统浅色环境使用深色骷髅，深色环境使用骨白骷髅，均带红色警示元素
- 应用内像素文字渲染：标题、计时数字、按钮和关键数据采用无抗锯齿像素化文字；长说明文字保留普通排版以保证可读性
- 阶梯切角的像素按钮、卡片、弹窗和下拉菜单
- 桌面「计时器」小组件：开始 / 暂停 / 继续 / 结束
- 小组件使用系统 Chronometer，不依赖每秒唤醒 App
- 首页、设置页和统计页均处理小屏滚动

## 首页交互

首页只保留一个模式选择器：

- `正计时`
- `倒计时`

选择器与展开后的下拉面板保持同宽。

选择倒计时后弹出：

`__ 时  __ 分  __ 秒`

确认后切换为倒计时模式；计时开始后模式选择器锁定，结束当前计时后才允许重新选择。

## 更新机制

更新机制参考 The Day 的后期方案，并针对 IdleSkull 简化为“只提醒、不自动下载”：

- App 启动后在后台检查更新
- 发现更高 `versionCode` 时仅发送高重要性系统通知
- 不自动下载 APK，不自动安装
- Android 13+ 全新安装后第一次启动只请求一次通知权限
- 不申请广泛存储权限
- 点击更新通知直接打开对应 GitHub Release

更新清单默认读取：

`https://raw.githubusercontent.com/rekjorekjo/IdleSkull/main/latest.json`

如仓库路径变化，只需修改：

`app/src/main/java/com/idleskull/app/update/UpdateConfig.kt`

### 发布说明

根目录 `release-notes.md` 是发布说明唯一人工维护来源。

每次 Android 构建前，Gradle 自动生成：

`app/src/main/res/raw/update_notes.txt`

APK 内“更新说明”读取该纯文本文件，无需人工维护第二份说明。

## 版本规则

测试版与正式版统一使用：

```text
X.Y.Z-beta
X.Y.Z
```

例如：

```properties
versionCode=3
versionName=0.2.0-beta
```

下一次 Beta 可以使用：

```properties
versionCode=4
versionName=0.2.1-beta
```

更新判断**只比较 `versionCode`**，因此 `versionName` 是否带 `-beta` 不影响升级；只要每次发布递增 `versionCode` 即可。

准备好签名 APK 后执行：

```bash
python scripts/release.py --apk /path/to/IdleSkull-release.apk
```

脚本会验证版本名只能是 `X.Y.Z` 或 `X.Y.Z-beta`，并生成：

- `dist/IdleSkull-v<version>.apk`
- `dist/latest.json`
- 根目录 `latest.json`

发布时把 APK 上传到对应 GitHub Release，并把根目录生成的 `latest.json` 提交到 `main`。App 从 `main/latest.json` 读取版本清单，因此即使 GitHub Release 被标记为 **Pre-release**，`X.Y.Z-beta` 也可以被正常检查到。

## 当前暂不做

- 小米 / vivo / OPPO / 荣耀等厂商岛能力
- 自动下载与应用内安装更新
- Android Live Update / 厂商特有实时胶囊
- 倒计时到点后的后台闹钟提醒（Beta 真机测试后再决定实现方式）
- Room 数据库迁移

## 工程参数

- Project: `IdleSkull`
- Package: `com.idleskull.app`
- Kotlin + Jetpack Compose
- minSdk 26
- compileSdk / targetSdk 36
- JDK 17
- AGP 9.0.0
- Gradle 9.1.0
- Compose BOM 2026.06.00

## Beta 测试重点

- 长时间正计时后时间是否准确
- 暂停 / 继续多次后统计是否正确
- 跨午夜计时是否正确拆分到两天
- App 被系统结束、重新打开后计时是否恢复
- 倒计时过程中切后台、锁屏后的行为
- 桌面小组件与 App 内状态是否一致
- 小屏设备首页 / 设置 / 统计是否能正常滚动且不会被底部导航遮挡
- 深色 / 浅色切换后的骷髅背景、文字与系统栏可读性
- Android 13+ 通知权限与更新通知行为

发现问题时尽量记录：系统版本、设备型号、操作步骤和预期 / 实际结果。
