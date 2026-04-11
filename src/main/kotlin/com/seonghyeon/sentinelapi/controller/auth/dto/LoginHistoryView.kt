package com.seonghyeon.sentinelapi.controller.auth.dto

import java.time.LocalDateTime

data class LoginHistoryView(
    val id: Long,
    val token: String,
    val appId: String,
    val ip: String,
    val createdAtKst: LocalDateTime,
)
