package com.seonghyeon.sentinelapi.controller.auth

import com.seonghyeon.sentinelapi.controller.auth.dto.UserLoginResponse
import com.seonghyeon.sentinelapi.domain.LoginHistory
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import com.seonghyeon.sentinelapi.service.ApplicationService
import com.seonghyeon.sentinelapi.service.TokenAuthService
import com.seonghyeon.sentinelapi.utils.clientIp
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/auth")
class UserController(
    private val applicationService: ApplicationService,
    private val tokenAuthService: TokenAuthService,
    private val loginHistoryRepository: LoginHistoryRepository,
) {

    @PostMapping("/login/token")
    fun loginToken(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestParam("token") token: String,
        request: HttpServletRequest,
    ): ResponseEntity<UserLoginResponse> {
        val clientId = applicationService.resolveClientId(appId)
        val t = tokenAuthService.check(token, clientId)

        // TODO 활용 IP 개수 확인 및 제한
        // 1시간 내에 로그인 개수가 제한 수 이상이면 429 응답
        // 앞단에서는 429를 받았을 때 "이전에 사용한 PC를 로그아웃하고 사용하시겠습니까?" 등으로

        loginHistoryRepository.save(
            LoginHistory(id = 0, token = token, appId = appId, ip = request.clientIp(), createdAt = LocalDateTime.now())
        )

        return ResponseEntity.ok(UserLoginResponse.from(t))
    }

    @PostMapping("/check/token")
    fun checkToken(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestParam("token") token: String,
    ): ResponseEntity<UserLoginResponse> {
        val clientId = applicationService.resolveClientId(appId)
        val userToken = tokenAuthService.check(token, clientId)
        return ResponseEntity.ok(UserLoginResponse.from(userToken))
    }
}
