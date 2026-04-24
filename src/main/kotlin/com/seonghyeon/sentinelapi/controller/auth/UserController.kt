package com.seonghyeon.sentinelapi.controller.auth

import com.seonghyeon.sentinelapi.controller.auth.dto.AppVersionInfo
import com.seonghyeon.sentinelapi.controller.auth.dto.DeviceView
import com.seonghyeon.sentinelapi.controller.auth.dto.UserLoginResponse
import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.AppFile
import com.seonghyeon.sentinelapi.domain.LoginHistory
import com.seonghyeon.sentinelapi.service.AppFileService
import com.seonghyeon.sentinelapi.service.ApplicationService
import com.seonghyeon.sentinelapi.service.DeviceService
import com.seonghyeon.sentinelapi.service.LoginHistoryService
import com.seonghyeon.sentinelapi.service.TokenAuthService
import com.seonghyeon.sentinelapi.utils.clientIp
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/v1/auth")
class UserController(
    private val applicationService: ApplicationService,
    private val tokenAuthService: TokenAuthService,
    private val loginHistoryService: LoginHistoryService,
    private val deviceService: DeviceService,
    private val appFileService: AppFileService,
) {

    @PostMapping("/login/token")
    fun loginToken(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestHeader(value = "X-Device-Id", required = false) deviceId: String?,
        @RequestParam("token") token: String,
        request: HttpServletRequest,
    ): ResponseEntity<UserLoginResponse> {
        val clientId = applicationService.resolveClientId(appId)
        val t = tokenAuthService.check(token, clientId)

        if (t.maxDeviceCount > 0 && deviceId.isNullOrBlank()) {
            throw SentinelException(ErrorCode.DEVICE_ID_REQUIRED)
        }

        if (!deviceId.isNullOrBlank()) {
            deviceService.login(t, deviceId)
        }

        loginHistoryService.save(
            LoginHistory(
                id = 0,
                token = token,
                appId = appId,
                ip = request.clientIp(),
                deviceId = deviceId,
                createdAt = LocalDateTime.now(ZoneOffset.UTC),
            )
        )

        return ResponseEntity.ok(UserLoginResponse.from(t))
    }

    @PostMapping("/check/token")
    fun checkToken(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestHeader(value = "X-Device-Id", required = false) deviceId: String?,
        @RequestParam("token") token: String,
    ): ResponseEntity<UserLoginResponse> {
        val clientId = applicationService.resolveClientId(appId)
        val userToken = tokenAuthService.check(token, clientId)

        if (!deviceId.isNullOrBlank()) {
            deviceService.check(userToken.id, deviceId)
        }

        return ResponseEntity.ok(UserLoginResponse.from(userToken))
    }

    @GetMapping("/devices")
    fun getDevices(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestParam("token") token: String,
    ): ResponseEntity<List<DeviceView>> {
        val clientId = applicationService.resolveClientId(appId)
        val userToken = tokenAuthService.check(token, clientId)
        val devices = deviceService.findAllByToken(userToken.id)
            .map { DeviceView(it.deviceId, it.registeredAt, it.lastSeenAt) }
        return ResponseEntity.ok(devices)
    }

    @DeleteMapping("/devices/{deviceId}")
    fun removeDevice(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestParam("token") token: String,
        @PathVariable deviceId: String,
    ): ResponseEntity<Void> {
        val clientId = applicationService.resolveClientId(appId)
        val userToken = tokenAuthService.check(token, clientId)
        deviceService.remove(userToken.id, deviceId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/logout")
    fun logout(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestParam("token") token: String,
    ): ResponseEntity<Void> {
        val clientId = applicationService.resolveClientId(appId)
        val userToken = tokenAuthService.check(token, clientId)
        deviceService.remove(userToken.id, deviceId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/version")
    fun latestVersion(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestParam("token") token: String,
    ): ResponseEntity<AppVersionInfo> {
        val clientId = applicationService.resolveClientId(appId)
        tokenAuthService.check(token, clientId)
        val file = appFileService.findLatest(clientId)
        return ResponseEntity.ok(AppVersionInfo.from(file))
    }

    @GetMapping("/version/{version}")
    fun specificVersion(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestParam("token") token: String,
        @PathVariable version: String,
    ): ResponseEntity<AppVersionInfo> {
        val clientId = applicationService.resolveClientId(appId)
        tokenAuthService.check(token, clientId)
        val file = appFileService.findByAppAndVersion(clientId, version)
        return ResponseEntity.ok(AppVersionInfo.from(file))
    }

    @GetMapping("/download")
    fun downloadLatest(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestParam("token") token: String,
    ): ResponseEntity<Resource> {
        val clientId = applicationService.resolveClientId(appId)
        tokenAuthService.check(token, clientId)
        return streamFile(appFileService.findLatest(clientId))
    }

    @GetMapping("/download/{version}")
    fun downloadVersion(
        @RequestHeader("X-Client-Id") appId: String,
        @RequestParam("token") token: String,
        @PathVariable version: String,
    ): ResponseEntity<Resource> {
        val clientId = applicationService.resolveClientId(appId)
        tokenAuthService.check(token, clientId)
        return streamFile(appFileService.findByAppAndVersion(clientId, version))
    }

    private fun streamFile(file: AppFile): ResponseEntity<Resource> {
        val path = appFileService.resolvePath(file)
        val disposition = ContentDisposition.attachment()
            .filename(file.filename, StandardCharsets.UTF_8)
            .build()
            .toString()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .header("X-File-Version", file.version)
            .header("X-File-SHA256", file.sha256)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(file.sizeBytes)
            .body(FileSystemResource(path))
    }
}
