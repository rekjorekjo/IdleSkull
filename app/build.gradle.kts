import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val appVersionCode = versionProperties.getProperty("versionCode").toInt()
val appVersionName = versionProperties.getProperty("versionName")

// Fusion Pixel Font is fetched at build time instead of being committed into this repository.
// The upstream release is pinned so builds remain reproducible while the source tree stays small.
val fusionPixelVersion = "2026.08.11"
val fusionPixelArchiveName = "fusion-pixel-font-12px-proportional-ttf-v${fusionPixelVersion}.zip"
val fusionPixelUrl = "https://github.com/TakWolf/fusion-pixel-font/releases/download/$fusionPixelVersion/$fusionPixelArchiveName"
val fusionPixelLicenseUrl = "https://raw.githubusercontent.com/TakWolf/fusion-pixel-font/$fusionPixelVersion/LICENSE-OFL"

abstract class PrepareFusionPixelFontTask : DefaultTask() {
    @get:Input
    abstract val fontVersion: Property<String>

    @get:Input
    abstract val archiveName: Property<String>

    @get:Input
    abstract val archiveUrl: Property<String>

    @get:Input
    abstract val licenseUrl: Property<String>

    @get:Input
    abstract val userAgentVersion: Property<String>

    // Cache is deliberately outside build/ so `clean` does not force another download.
    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val resourceOutputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val cacheDir = cacheDirectory.get().asFile
        val outputDir = resourceOutputDirectory.get().asFile
        val archive = cacheDir.resolve(archiveName.get())
        val licenseCache = cacheDir.resolve("LICENSE-OFL-${fontVersion.get()}.txt")
        val generatedFont = outputDir.resolve("font/fusion_pixel_12px_proportional.ttf")
        val generatedLicense = outputDir.resolve("raw/fusion_pixel_font_license.txt")

        fun download(url: String, destination: java.io.File, label: String) {
            destination.parentFile.mkdirs()
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "IdleSkull-Gradle/${userAgentVersion.get()}")
            try {
                val status = connection.responseCode
                if (status !in 200..299) error("$label download failed: HTTP $status")
                connection.inputStream.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
        }

        if (!archive.isFile || archive.length() == 0L) {
            logger.lifecycle("Downloading Fusion Pixel Font ${fontVersion.get()}...")
            download(archiveUrl.get(), archive, "Fusion Pixel Font")
        }
        if (!licenseCache.isFile || licenseCache.length() == 0L) {
            logger.lifecycle("Downloading Fusion Pixel Font OFL license...")
            download(licenseUrl.get(), licenseCache, "Fusion Pixel Font license")
        }

        if (!generatedFont.isFile || generatedFont.length() == 0L) {
            var selected: ByteArray? = null
            var fallback: ByteArray? = null
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".ttf")) {
                        val bytes = zip.readBytes()
                        if (fallback == null) fallback = bytes
                        val entryName = entry.name.lowercase()
                        if (
                            entryName.contains("zh_hans") ||
                            entryName.contains("zh-hans") ||
                            entryName.contains("zh_cn") ||
                            entryName.contains("zh-cn")
                        ) {
                            selected = bytes
                            break
                        }
                    }
                    zip.closeEntry()
                }
            }

            val fontBytes = selected ?: fallback
                ?: error("No TTF found in ${archiveName.get()}")
            generatedFont.parentFile.mkdirs()
            generatedFont.writeBytes(fontBytes)
            logger.lifecycle("Prepared Fusion Pixel Font: ${generatedFont.absolutePath}")
        }

        generatedLicense.parentFile.mkdirs()
        licenseCache.copyTo(generatedLicense, overwrite = true)
    }
}

android {
    namespace = "com.idleskull.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.idleskull.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9 generated resources must be registered through the Variant API.
// This also wires the generating task into resource merging automatically.
androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        val prepareFusionPixelFont = tasks.register<PrepareFusionPixelFontTask>(
            "prepare${variantName}FusionPixelFont"
        ) {
            fontVersion.set(fusionPixelVersion)
            archiveName.set(fusionPixelArchiveName)
            archiveUrl.set(fusionPixelUrl)
            licenseUrl.set(fusionPixelLicenseUrl)
            userAgentVersion.set(appVersionName)
            cacheDirectory.set(rootProject.layout.projectDirectory.dir(".gradle/idleskull-fonts"))
            resourceOutputDirectory.set(
                layout.buildDirectory.dir("generated/pixelFont/${variant.name}/res")
            )
        }

        variant.sources.res?.addGeneratedSourceDirectory(
            prepareFusionPixelFont,
            PrepareFusionPixelFontTask::resourceOutputDirectory,
        )
    }
}

val generateUpdateNotes by tasks.registering {
    val source = rootProject.file("release-notes.md")
    val output = layout.projectDirectory.file("src/main/res/raw/update_notes.txt").asFile
    inputs.file(source)
    outputs.file(output)

    doLast {
        output.parentFile.mkdirs()
        val plainText = source.readLines(Charsets.UTF_8).joinToString("\n") { line ->
            when {
                line.startsWith("#") -> line.trimStart('#').trimStart()
                line.startsWith("- ") -> "• " + line.removePrefix("- ")
                else -> line
            }
        }.trimEnd() + "\n"
        output.writeText(plainText, Charsets.UTF_8)
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateUpdateNotes)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
