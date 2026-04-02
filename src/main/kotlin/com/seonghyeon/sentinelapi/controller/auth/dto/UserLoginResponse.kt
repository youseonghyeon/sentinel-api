package com.seonghyeon.sentinelapi.controller.auth.dto

import com.seonghyeon.sentinelapi.domain.Token
import java.time.LocalDate

data class UserLoginResponse(
    val expireDate: LocalDate,
) {
    companion object {
        fun from(token: Token): UserLoginResponse {
            return UserLoginResponse(token.expireDate)
        }
    }
}
