package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.service.ApplicationService
import com.seonghyeon.sentinelapi.service.TokenAuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/manager")
class ManagerApiController(
    private val applicationService: ApplicationService,
    private val tokenAuthService: TokenAuthService,
) {

    @PostMapping("/users")
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<CreateUserResponse> {
        val clientId = applicationService.resolveClientId(request.appId)
        val token = tokenAuthService.generate(clientId, request.expireDate)
        return ResponseEntity.ok(CreateUserResponse(token = token.tokenStr, expireDate = token.expireDate))
    }
}

data class CreateUserRequest(
    val appId: String,
    val expireDate: LocalDate,
)

data class CreateUserResponse(
    val token: String,
    val expireDate: LocalDate,
)
