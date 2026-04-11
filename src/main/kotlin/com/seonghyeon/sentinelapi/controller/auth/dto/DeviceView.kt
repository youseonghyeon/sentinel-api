package com.seonghyeon.sentinelapi.controller.auth.dto

import java.time.LocalDateTime

data class DeviceView(
    val deviceId: String,
    val registeredAt: LocalDateTime,
    val lastSeenAt: LocalDateTime,
)
