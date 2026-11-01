package com.nendo.argosy.data.model

data class PushedApkPrompt(
    val gameId: Long,
    val apkPath: String,
    val title: String,
    val packageName: String,
    val signerSha256: String?,
    val serverHost: String?
)
