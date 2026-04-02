package com.seonghyeon.sentinelapi.common.exception

import org.springframework.http.HttpStatus

class SentinelException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)

enum class ErrorCode(val status: HttpStatus, val message: String) {
    // Client
    INVALID_APPLICATION(HttpStatus.UNAUTHORIZED, "유효하지 않은 애플리케이션 키입니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),

    // Manager
    MANAGER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "유효하지 않은 API 키입니다."),
}
