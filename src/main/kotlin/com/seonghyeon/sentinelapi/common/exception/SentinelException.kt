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

    // Device
    DEVICE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "PC 식별 정보가 필요합니다."),
    DEVICE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "허용된 PC 수를 초과하였습니다."),
    DEVICE_LOGGED_OUT(HttpStatus.UNAUTHORIZED, "기기가 로그아웃되었습니다."),

    // App file
    NO_FILE_AVAILABLE(HttpStatus.NOT_FOUND, "등록된 파일이 없습니다."),
    INVALID_VERSION(HttpStatus.NOT_FOUND, "요청한 버전을 찾을 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    DUPLICATE_VERSION(HttpStatus.CONFLICT, "이미 등록된 버전입니다."),
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
}
