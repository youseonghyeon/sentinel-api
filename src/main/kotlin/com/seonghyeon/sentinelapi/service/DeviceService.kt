package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.DeviceRegistration
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.DeviceRegistrationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class DeviceService(
    private val deviceRegistrationRepository: DeviceRegistrationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun login(token: Token, deviceId: String) {
        val existing = deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, deviceId)
        if (existing != null) {
            log.info("Device re-login: tokenId={}, deviceId={}", token.id, deviceId)
            existing.lastSeenAt = LocalDateTime.now(ZoneOffset.UTC)
            return
        }

        if (token.maxDeviceCount > 0) {
            val count = deviceRegistrationRepository.countByTokenId(token.id)
            if (count >= token.maxDeviceCount) {
                log.warn("Device limit exceeded: tokenId={}, count={}, max={}", token.id, count, token.maxDeviceCount)
                throw SentinelException(ErrorCode.DEVICE_LIMIT_EXCEEDED)
            }
        }

        log.info("Device registered: tokenId={}, deviceId={}", token.id, deviceId)
        deviceRegistrationRepository.save(
            DeviceRegistration(
                id = 0,
                token = token,
                deviceId = deviceId,
                registeredAt = LocalDateTime.now(ZoneOffset.UTC),
                lastSeenAt = LocalDateTime.now(ZoneOffset.UTC),
            )
        )
    }

    @Transactional
    fun check(tokenId: Long, deviceId: String) {
        val registration = deviceRegistrationRepository.findByTokenIdAndDeviceId(tokenId, deviceId)
            ?: run {
                log.warn("Device not registered: tokenId={}, deviceId={}", tokenId, deviceId)
                throw SentinelException(ErrorCode.DEVICE_LOGGED_OUT)
            }
        registration.lastSeenAt = LocalDateTime.now(ZoneOffset.UTC)
        log.debug("Device heartbeat: tokenId={}, deviceId={}", tokenId, deviceId)
    }

    fun findAllByToken(tokenId: Long): List<DeviceRegistration> =
        deviceRegistrationRepository.findByTokenId(tokenId)

    @Transactional
    fun remove(tokenId: Long, deviceId: String) {
        log.info("Device removed: tokenId={}, deviceId={}", tokenId, deviceId)
        deviceRegistrationRepository.deleteByTokenIdAndDeviceId(tokenId, deviceId)
    }
}
