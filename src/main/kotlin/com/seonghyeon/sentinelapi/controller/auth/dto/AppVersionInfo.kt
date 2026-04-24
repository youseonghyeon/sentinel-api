package com.seonghyeon.sentinelapi.controller.auth.dto

import com.seonghyeon.sentinelapi.domain.AppFile
import java.time.LocalDateTime

data class AppVersionInfo(
    val version: String,
    val filename: String,
    val size: Long,
    val sha256: String,
    val uploadedAt: LocalDateTime,
    val changelog: String?,
    val isLatest: Boolean,
) {
    companion object {
        fun from(f: AppFile) = AppVersionInfo(
            version = f.version,
            filename = f.filename,
            size = f.sizeBytes,
            sha256 = f.sha256,
            uploadedAt = f.uploadedAt,
            changelog = f.changelog,
            isLatest = f.isLatest,
        )
    }
}
