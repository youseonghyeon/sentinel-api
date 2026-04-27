package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.DeviceRegistration
import com.seonghyeon.sentinelapi.domain.LoginHistory
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import com.seonghyeon.sentinelapi.service.ApplicationService
import com.seonghyeon.sentinelapi.service.DeviceService
import com.seonghyeon.sentinelapi.service.TokenAuthService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/manager")
class ManagerApiController(
    private val applicationService: ApplicationService,
    private val tokenAuthService: TokenAuthService,
    private val tokenRepository: TokenRepository,
    private val appRepository: AppRepository,
    private val loginHistoryRepository: LoginHistoryRepository,
    private val deviceService: DeviceService,
) {

    // ─── Tokens ─────────────────────────────────────────────────────────────

    @PostMapping("/users")
    fun createUser(@RequestBody request: CreateUserRequest): ResponseEntity<TokenView> {
        val clientId = applicationService.resolveClientId(request.appId)
        val maxDeviceCount = (request.maxDeviceCount ?: 1).also {
            if (it < 0) throw SentinelException(ErrorCode.INVALID_APPLICATION)
        }
        val token = tokenAuthService.generate(clientId, request.expireDate, maxDeviceCount)
        return ResponseEntity.ok(TokenView.from(token))
    }

    @GetMapping("/users")
    fun listUsers(
        @RequestParam(required = false) appId: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<TokenView>> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200), Sort.by(Sort.Direction.DESC, "id"))
        val resultPage = when {
            !appId.isNullOrBlank() -> {
                val app = appRepository.findByAppId(appId) ?: throw SentinelException(ErrorCode.INVALID_APPLICATION)
                if (!q.isNullOrBlank()) tokenRepository.findByApplication_IdAndTokenStrContainingIgnoreCase(app.id, q, pageable)
                else tokenRepository.findByApplication_Id(app.id, pageable)
            }
            !q.isNullOrBlank() -> tokenRepository.findByTokenStrContainingIgnoreCase(q, pageable)
            else -> tokenRepository.findAllBy(pageable)
        }
        return ResponseEntity.ok(PageResponse.from(resultPage.map { TokenView.from(it) }))
    }

    @GetMapping("/users/{tokenId}")
    fun getUser(@PathVariable tokenId: Long): ResponseEntity<TokenView> {
        val token = tokenRepository.findById(tokenId).orElseThrow { SentinelException(ErrorCode.INVALID_TOKEN) }
        return ResponseEntity.ok(TokenView.from(token))
    }

    @PatchMapping("/users/{tokenId}")
    fun updateUser(
        @PathVariable tokenId: Long,
        @RequestBody request: UpdateUserRequest,
    ): ResponseEntity<TokenView> {
        val token = tokenRepository.findById(tokenId).orElseThrow { SentinelException(ErrorCode.INVALID_TOKEN) }
        val newExpire = request.expireDate ?: token.expireDate
        val newMax = request.maxDeviceCount ?: token.maxDeviceCount
        if (newMax < 0) throw SentinelException(ErrorCode.INVALID_APPLICATION)
        tokenAuthService.update(tokenId, newExpire, newMax)
        val updated = tokenRepository.findById(tokenId).orElseThrow { SentinelException(ErrorCode.INVALID_TOKEN) }
        return ResponseEntity.ok(TokenView.from(updated))
    }

    @DeleteMapping("/users/{tokenId}")
    fun deleteUser(@PathVariable tokenId: Long): ResponseEntity<Void> {
        if (!tokenRepository.existsById(tokenId)) throw SentinelException(ErrorCode.INVALID_TOKEN)
        tokenAuthService.delete(tokenId)
        return ResponseEntity.noContent().build()
    }

    // ─── Devices ────────────────────────────────────────────────────────────

    @GetMapping("/users/{tokenId}/devices")
    fun listDevices(@PathVariable tokenId: Long): ResponseEntity<List<DeviceItem>> {
        if (!tokenRepository.existsById(tokenId)) throw SentinelException(ErrorCode.INVALID_TOKEN)
        return ResponseEntity.ok(deviceService.findAllByToken(tokenId).map(DeviceItem::from))
    }

    @DeleteMapping("/users/{tokenId}/devices/{deviceId}")
    fun removeDevice(
        @PathVariable tokenId: Long,
        @PathVariable deviceId: String,
    ): ResponseEntity<Void> {
        if (!tokenRepository.existsById(tokenId)) throw SentinelException(ErrorCode.INVALID_TOKEN)
        deviceService.remove(tokenId, deviceId)
        return ResponseEntity.noContent().build()
    }

    // ─── Apps ───────────────────────────────────────────────────────────────

    @GetMapping("/apps")
    fun listApps(): ResponseEntity<List<AppView>> =
        ResponseEntity.ok(applicationService.findAll().map(AppView::from))

    @PostMapping("/apps")
    fun createApp(@RequestBody request: CreateAppRequest): ResponseEntity<AppView> {
        if (request.name.isBlank()) throw SentinelException(ErrorCode.INVALID_APPLICATION)
        val app = applicationService.register(request.name, request.description ?: "")
        return ResponseEntity.ok(AppView.from(app))
    }

    @DeleteMapping("/apps/{id}")
    fun deleteApp(@PathVariable id: Long): ResponseEntity<Void> {
        if (!appRepository.existsById(id)) throw SentinelException(ErrorCode.INVALID_APPLICATION)
        applicationService.delete(id)
        return ResponseEntity.noContent().build()
    }

    // ─── Login Histories ────────────────────────────────────────────────────

    @GetMapping("/login-histories")
    fun listLoginHistories(
        @RequestParam(required = false) appId: String?,
        @RequestParam(required = false) token: String?,
        @RequestParam(required = false) deviceId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<LoginHistoryView>> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200), Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = loginHistoryRepository.search(
            appId = appId?.trim().orEmpty(),
            token = token?.trim().orEmpty(),
            deviceId = deviceId?.trim().orEmpty(),
            pageable = pageable,
        )
        return ResponseEntity.ok(PageResponse.from(result.map(LoginHistoryView::from)))
    }
}

// ─── Request / Response DTOs ────────────────────────────────────────────────

data class CreateUserRequest(
    val appId: String,
    val expireDate: LocalDate,
    val maxDeviceCount: Int? = null,
)

data class UpdateUserRequest(
    val expireDate: LocalDate? = null,
    val maxDeviceCount: Int? = null,
)

data class CreateAppRequest(
    val name: String,
    val description: String? = null,
)

data class TokenView(
    val id: Long,
    val token: String,
    val appId: String,
    val appName: String,
    val expireDate: LocalDate,
    val maxDeviceCount: Int,
) {
    companion object {
        fun from(token: Token) = TokenView(
            id = token.id,
            token = token.tokenStr,
            appId = token.application.appId,
            appName = token.application.name,
            expireDate = token.expireDate,
            maxDeviceCount = token.maxDeviceCount,
        )
    }
}

data class AppView(
    val id: Long,
    val appId: String,
    val name: String,
    val description: String,
) {
    companion object {
        fun from(app: App) = AppView(app.id, app.appId, app.name, app.description)
    }
}

data class DeviceItem(
    val deviceId: String,
    val registeredAt: LocalDateTime,
    val lastSeenAt: LocalDateTime,
) {
    companion object {
        fun from(d: DeviceRegistration) = DeviceItem(d.deviceId, d.registeredAt, d.lastSeenAt)
    }
}

data class LoginHistoryView(
    val id: Long,
    val token: String,
    val appId: String,
    val ip: String,
    val deviceId: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(h: LoginHistory) = LoginHistoryView(
            id = h.id,
            token = h.token,
            appId = h.appId,
            ip = h.ip,
            deviceId = h.deviceId,
            createdAt = h.createdAt,
        )
    }
}

data class PageResponse<T : Any>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T : Any> from(p: org.springframework.data.domain.Page<T>) = PageResponse(
            content = p.content,
            page = p.number,
            size = p.size,
            totalElements = p.totalElements,
            totalPages = p.totalPages,
        )
    }
}
