import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val appVersionCode = versionProperties.getProperty("versionCode").toInt()
val appVersionName = versionProperties.getProperty("versionName")

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
