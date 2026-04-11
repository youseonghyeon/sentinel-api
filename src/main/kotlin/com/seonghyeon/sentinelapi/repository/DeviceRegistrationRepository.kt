package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.DeviceRegistration
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceRegistrationRepository : JpaRepository<DeviceRegistration, Long> {
    fun findByTokenIdAndDeviceId(tokenId: Long, deviceId: String): DeviceRegistration?
    fun findByTokenId(tokenId: Long): List<DeviceRegistration>
    fun countByTokenId(tokenId: Long): Long
    fun deleteByTokenIdAndDeviceId(tokenId: Long, deviceId: String)
}
