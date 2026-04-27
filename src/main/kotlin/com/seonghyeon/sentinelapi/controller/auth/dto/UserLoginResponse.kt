package com.seonghyeon.sentinelapi.controller.auth.dto

import com.seonghyeon.sentinelapi.domain.Token
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

data class UserLoginResponse(
    val appName: String,
    val expireDate: LocalDate,
    val maxDeviceCount: Int,
    val currentTime: LocalDateTime,
) {
    companion object {
        fun from(token: Token): UserLoginResponse {
            return UserLoginResponse(
                appName = token.application.name,
                expireDate = token.expireDate,
                maxDeviceCount = token.maxDeviceCount,
                currentTime = LocalDateTime.now(ZoneOffset.UTC),
            )
        }
    }
}

data class UserCheckResponse(
    val expireDate: LocalDate,
    val currentTime: LocalDateTime,
) {
    companion object {
        fun from(token: Token): UserCheckResponse {
            return UserCheckResponse(
                expireDate = token.expireDate,
                currentTime = LocalDateTime.now(ZoneOffset.UTC),
            )
        }
    }
}
