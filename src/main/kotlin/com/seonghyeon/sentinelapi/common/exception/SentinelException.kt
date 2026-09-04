package com.seonghyeon.sentinelapi.common.exception

import org.springframework.http.HttpStatus

class SentinelException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)

enum class ErrorCode(val status: HttpStatus, val message: String) {
    // Common
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),

    // Client
    INVALID_APPLICATION(HttpStatus.UNAUTHORIZED, "유효하지 않은 애플리케이션 키입니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),

    // Manager
    MANAGER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    INVALID_NEW_PASSWORD(HttpStatus.BAD_REQUEST, "새 비밀번호를 입력해 주세요."),
    PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "새 비밀번호와 새 비밀번호 확인이 일치하지 않습니다."),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "유효하지 않은 API 키입니다."),
    TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 토큰을 찾을 수 없습니다."),
    APP_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 애플리케이션을 찾을 수 없습니다."),
    APP_IN_USE(HttpStatus.CONFLICT, "사용중인 사용자가 있습니다."),

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
