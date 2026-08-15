package com.idleskull.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idleskull.app.BuildConfig
import com.idleskull.app.R
import com.idleskull.app.ui.components.PixelButton
import com.idleskull.app.ui.components.PixelPanel
import com.idleskull.app.ui.components.PixelParagraph
import com.idleskull.app.ui.components.PixelText
import com.idleskull.app.ui.components.SkullBackdrop
import com.idleskull.app.update.AppUpdateManager
import com.idleskull.app.update.GitHubRelease
import com.idleskull.app.update.InstallLaunchResult
import com.idleskull.app.update.UpdateCheckResult
import com.idleskull.app.update.UpdateDownloadState
import com.idleskull.app.update.UpdateConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class AboutDocument(val title: String) {
    UPDATE_NOTES("更新说明"),
    USER_GUIDE("使用说明"),
    PRIVACY("隐私说明"),
    OPEN_SOURCE("开源组件"),
    FONT_LICENSE("字体许可"),
}

@Composable
fun AboutScreen(
    darkMode: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember(context) { AppUpdateManager(context) }
    var document by remember { mutableStateOf<AboutDocument?>(null) }
    var updateStatus by remember { mutableStateOf("手动检查") }
    var updateError by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<GitHubRelease?>(null) }

    document?.let { selected ->
        BackHandler { document = null }
        AboutDocumentScreen(
            title = selected.title,
            text = when (selected) {
                AboutDocument.UPDATE_NOTES -> readUpdateNotes(context)
                AboutDocument.USER_GUIDE -> readRawText(context, R.raw.usage_guide)
                AboutDocument.PRIVACY -> PRIVACY_TEXT
                AboutDocument.OPEN_SOURCE -> OPEN_SOURCE_TEXT
                AboutDocument.FONT_LICENSE -> readGeneratedRaw(context, "fusion_pixel_font_license")
            },
            onBack = { document = null },
        )
        return
    }

    availableUpdate?.let { update ->
        BackHandler { availableUpdate = null }
        UpdateDetailsScreen(
            update = update,
            updateManager = updateManager,
            onBack = { availableUpdate = null },
            onOpenRelease = { uriHandler.openUri(update.releaseUrl) },
        )
        return
    }

    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PixelButton(
                text = "返回",
                onClick = onBack,
                modifier = Modifier.width(86.dp),
                inverted = true,
            )
            PixelText("关于", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SkullBackdrop(
                darkMode = darkMode,
                modifier = Modifier.size(118.dp),
                alpha = 1f,
            )
            Spacer(Modifier.height(8.dp))
            PixelText(
                "今天你又摆烂了？",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            PixelText(
                "IdleSkull  ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }

        PixelPanel {
            Column {
                AboutRow(
                    title = "检查更新",
                    value = if (checking) "正在检查…" else updateStatus,
                    enabled = !checking,
                    onClick = {
                        checking = true
                        updateStatus = "正在检查…"
                        updateError = null
                        availableUpdate = null
                        updateManager.resetFailedDownloadForManualCheck()
                        coroutineScope.launch {
                            when (val result = updateManager.checkForUpdate()) {
                                UpdateCheckResult.UpToDate -> {
                                    updateStatus = "已是最新版本"
                                    updateError = null
                                }
                                is UpdateCheckResult.UpdateAvailable -> {
                                    updateStatus = "发现 ${result.release.versionName}"
                                    updateError = null
                                    availableUpdate = result.release
                                }
                                UpdateCheckResult.CheckFailed -> {
                                    updateStatus = "检查失败"
                                    updateError = "无法连接更新源，请稍后重试"
                                }
                            }
                            checking = false
                        }
                    },
                )
                updateError?.let { message ->
                    PixelText(
                        text = message,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 9.sp,
                    )
                }
                AboutDivider()
                AboutRow("更新说明") { document = AboutDocument.UPDATE_NOTES }
                AboutDivider()
                AboutRow("使用说明") { document = AboutDocument.USER_GUIDE }
                AboutDivider()
                AboutRow("隐私说明") { document = AboutDocument.PRIVACY }
                AboutDivider()
                AboutRow("开源组件") { document = AboutDocument.OPEN_SOURCE }
                AboutDivider()
                AboutRow("字体许可") { document = AboutDocument.FONT_LICENSE }
                AboutDivider()
                AboutRow("GitHub 仓库", value = "↗") { uriHandler.openUri(UpdateConfig.REPOSITORY_URL) }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AboutRow(
    title: String,
    value: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelText(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
        )
        PixelText(
            text = value.ifBlank { "›" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AboutDivider() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        thickness = 1.dp,
    )
}

@Composable
private fun AboutDocumentScreen(
    title: String,
    text: String,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PixelButton("返回", onBack, Modifier.width(86.dp), inverted = true)
            PixelText(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        PixelPanel {
            PixelParagraph(
                text = text.ifBlank { "暂无内容。" },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun UpdateDetailsScreen(
    update: GitHubRelease,
    updateManager: AppUpdateManager,
    onBack: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    val context = LocalContext.current
    var downloadStatus by remember(update.tagName) { mutableStateOf(updateManager.currentStatus()) }
    var installMessage by remember(update.tagName) { mutableStateOf<String?>(null) }
    var verifying by remember(update.tagName) { mutableStateOf(false) }

    LaunchedEffect(update.tagName) {
        while (isActive) {
            downloadStatus = updateManager.currentStatus()
            delay(
                when (downloadStatus.state) {
                    UpdateDownloadState.WAITING,
                    UpdateDownloadState.DOWNLOADING,
                    UpdateDownloadState.VERIFYING -> 700L
                    else -> 1_500L
                },
            )
        }
    }

    LaunchedEffect(downloadStatus.state) {
        if (downloadStatus.state == UpdateDownloadState.VERIFYING && !verifying) {
            verifying = true
            updateManager.verifyPendingDownloadIfNeeded()
            downloadStatus = updateManager.currentStatus()
            verifying = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PixelButton("返回", onBack, Modifier.width(86.dp), inverted = true)
            PixelText(stringResource(R.string.copy_update_found), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        PixelPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelText(
                    "${BuildConfig.VERSION_NAME}  →  ${update.versionName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                PixelParagraph(
                    text = update.releaseNotes.ifBlank { "该版本没有填写更新说明。" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        val downloadLabel = when (downloadStatus.state) {
            UpdateDownloadState.NONE -> stringResource(R.string.copy_update_download_install)
            UpdateDownloadState.WAITING -> stringResource(R.string.copy_update_waiting)
            UpdateDownloadState.DOWNLOADING -> stringResource(
                R.string.copy_update_downloading,
                downloadStatus.progressPercent ?: 0,
            )
            UpdateDownloadState.VERIFYING -> stringResource(R.string.copy_update_verifying)
            UpdateDownloadState.READY -> stringResource(R.string.copy_update_install)
            UpdateDownloadState.FAILED -> stringResource(R.string.copy_update_download_failed_retry)
        }
        val busy = downloadStatus.state == UpdateDownloadState.WAITING ||
            downloadStatus.state == UpdateDownloadState.DOWNLOADING ||
            downloadStatus.state == UpdateDownloadState.VERIFYING

        PixelButton(
            text = downloadLabel,
            onClick = {
                installMessage = null
                when (downloadStatus.state) {
                    UpdateDownloadState.NONE,
                    UpdateDownloadState.FAILED -> {
                        updateManager.resetFailedDownloadForManualCheck()
                        if (!updateManager.startDownload(update)) {
                            installMessage = "无法启动系统下载，请稍后重试。"
                        }
                        downloadStatus = updateManager.currentStatus()
                    }
                    UpdateDownloadState.READY -> {
                        val activity = context.findActivity()
                        if (activity == null) {
                            installMessage = "无法打开系统安装程序。"
                        } else {
                            when (updateManager.requestInstall(activity)) {
                                InstallLaunchResult.LAUNCHED -> Unit
                                InstallLaunchResult.PERMISSION_REQUIRED -> {
                                    installMessage = "请允许 IdleSkull 安装未知应用，返回后再次点击“安装更新”。"
                                }
                                InstallLaunchResult.NO_VERIFIED_UPDATE -> {
                                    installMessage = "安装包尚未校验完成。"
                                }
                                InstallLaunchResult.INSTALLER_UNAVAILABLE -> {
                                    installMessage = "系统安装程序不可用。"
                                }
                                InstallLaunchResult.FAILED -> {
                                    installMessage = "无法打开系统安装程序。"
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )

        installMessage?.let { message ->
            PixelParagraph(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }

        PixelButton(
            stringResource(R.string.copy_update_open_release),
            onOpenRelease,
            Modifier.fillMaxWidth(),
            inverted = true,
        )
        Spacer(Modifier.height(16.dp))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun readUpdateNotes(context: android.content.Context): String =
    readRawText(context, R.raw.update_notes).ifBlank { "暂无更新说明。" }

private fun readRawText(context: android.content.Context, resourceId: Int): String = runCatching {
    context.resources.openRawResource(resourceId)
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}.getOrElse { "无法读取文档。" }

private fun readGeneratedRaw(context: android.content.Context, resourceName: String): String = runCatching {
    val id = context.resources.getIdentifier(resourceName, "raw", context.packageName)
    if (id == 0) return@runCatching "构建时未生成该许可文件。"
    context.resources.openRawResource(id)
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}.getOrElse { "无法读取许可文件。" }


private val PRIVACY_TEXT = """
• IdleSkull 不要求注册账号，不包含广告，也不接入行为统计或云同步。
• 计时状态、历史记录、记录名称和主题设置保存在设备本地。
• 应用启动时会读取 GitHub 最新正式 Release 的更新清单检查新版本；更新清单不可用时会回退到 GitHub Releases API。发现新版本后仅发送系统通知，不会后台自动下载。只有用户主动点击“下载并安装”时，才交由 Android 系统下载管理器下载 APK；完成大小与 SHA-256 校验后，再由系统安装程序确认安装。
• Android 13 及以上系统可能在首次使用时请求通知权限，用于新版本提醒。
• 卸载应用或清除应用数据会删除本机保存的历史记录。
""".trimIndent()

private val OPEN_SOURCE_TEXT = """
IdleSkull 当前主要使用：

Kotlin
Android 开发语言。

AndroidX / Jetpack
Activity、Lifecycle 等 Android 官方组件。

Jetpack Compose / Material 3
应用主要界面使用的 Android 声明式 UI 组件。

Fusion Pixel Font / 缝合像素字体
作者：TakWolf
许可证：SIL Open Font License 1.1
用途：IdleSkull 的中文、数字和主要 UI 像素文字。
构建时固定获取 12px Proportional（zh_hans）版本，字体文件不提交到项目源码。

字体的完整 OFL 1.1 许可文本随 APK 一并打包，可在上一页的“字体许可”中查看。
""".trimIndent()
