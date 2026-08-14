package com.idleskull.app.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object StatsShareManager {
    fun share(context: Context, spec: StatsExportSpec) {
        val directory = File(context.cacheDir, "shared_exports").apply { mkdirs() }
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000L) {
                file.delete()
            }
        }

        val file = File(directory, spec.fileName())
        file.outputStream().use { output ->
            StatsExportRenderer.writePng(context, spec, output)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "IdleSkull 摆烂记录")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "分享摆烂数据图"))
    }
}
