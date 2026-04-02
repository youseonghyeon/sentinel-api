package com.seonghyeon.sentinelapi.controller.auth

import com.seonghyeon.sentinelapi.controller.auth.dto.UserLoginResponse
import com.seonghyeon.sentinelapi.domain.LoginHistory
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import com.seonghyeon.sentinelapi.service.ApplicationService
import com.seonghyeon.sentinelapi.service.TokenAuthService
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
        loginHistoryRepository.save(
            LoginHistory(id = 0, token = token, appId = appId, ip = request.remoteAddr, createdAt = LocalDateTime.now())
        )

        return ResponseEntity.ok(UserLoginResponse.from(t))
    }

    @PostMapping("/check/token")
    fun checkToken(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestParam("token") token: String,
    ): ResponseEntity<Void> {
        val clientId = applicationService.resolveClientId(appId)
        tokenAuthService.check(token, clientId)
        return ResponseEntity.ok().build()
    }
}
