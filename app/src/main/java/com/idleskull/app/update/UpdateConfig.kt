package com.idleskull.app.update

object UpdateConfig {
    const val REPOSITORY = "rekjorekjo/IdleSkull"
    const val REPOSITORY_URL = "https://github.com/rekjorekjo/IdleSkull"

    /**
     * Keep update discovery deliberately boring, matching The Day:
     * one stable GitHub "latest release asset" URL first, GitHub's latest-release API only as fallback.
     *
     * Important: beta-named tags are still published as a normal GitHub Release (prerelease=false),
     * otherwise GitHub's /releases/latest endpoints intentionally ignore them.
     */
    const val MANIFEST_URL =
        "https://github.com/rekjorekjo/IdleSkull/releases/latest/download/latest.json"
    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/rekjorekjo/IdleSkull/releases/latest"
    const val RELEASE_BASE_URL = "https://github.com/rekjorekjo/IdleSkull/releases/tag/"
    const val GITHUB_API_VERSION = "2026-03-10"

    fun expectedApkName(tagName: String): String = "IdleSkull-$tagName.apk"
}
