package com.seonghyeon.sentinelapi.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(SentinelException::class)
    fun handleSentinelException(e: SentinelException): ResponseEntity<ErrorResponse> {
        if (e.errorCode.status.is5xxServerError) {
            log.error("Server error: code={}", e.errorCode, e)
        } else {
            log.warn("Client error: code={}", e.errorCode)
        }
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ErrorResponse(code = e.errorCode.name, message = e.errorCode.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation error: {}", message)
        return ResponseEntity.badRequest()
            .body(ErrorResponse(code = "VALIDATION_ERROR", message = message))
    }

    // JSON 파싱 오류 (non-nullable 필드 누락 등)
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.warn("Message not readable: {}", e.message)
        return ResponseEntity.badRequest()
            .body(ErrorResponse(code = "VALIDATION_ERROR", message = "요청 본문을 파싱할 수 없습니다."))
    }

    // X-Client-Id 등 필수 헤더 누락 시
    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(e: MissingRequestHeaderException): ResponseEntity<ErrorResponse> {
        log.warn("Missing header: {}", e.headerName)
        val message = if (e.headerName == "X-Client-Id") "X-Client-Id 헤더가 필요합니다."
                      else "필수 헤더가 누락되었습니다: ${e.headerName}"
        return ResponseEntity.badRequest()
            .body(ErrorResponse(code = "MISSING_HEADER", message = message))
    }

    // 동시 요청으로 인한 DB UNIQUE 제약 위반
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        log.warn("Data integrity violation: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(code = "CONFLICT", message = "이미 존재하는 데이터입니다."))
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(e: NoResourceFoundException, request: HttpServletRequest): ResponseEntity<Any> {
        log.warn("No resource found: {} {}", e.httpMethod, e.resourcePath)
        val path = request.requestURI
        if (path.startsWith("/api/") || path.startsWith("/actuator/")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse(code = "NOT_FOUND", message = "요청한 리소스를 찾을 수 없습니다."))
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/dashboard/apps")
            .build()
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", e)
        return ResponseEntity.internalServerError()
            .body(ErrorResponse(code = "INTERNAL_ERROR", message = "서버 오류가 발생했습니다."))
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
)
