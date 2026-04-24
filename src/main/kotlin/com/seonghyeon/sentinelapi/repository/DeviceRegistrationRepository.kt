package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.DeviceRegistration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeviceRegistrationRepository : JpaRepository<DeviceRegistration, Long> {
    fun findByTokenIdAndDeviceId(tokenId: Long, deviceId: String): DeviceRegistration?
    fun findByTokenId(tokenId: Long): List<DeviceRegistration>
    fun countByTokenId(tokenId: Long): Long
    fun deleteByTokenIdAndDeviceId(tokenId: Long, deviceId: String)

    @Query("SELECT d.token.id AS tokenId, COUNT(d) AS cnt FROM DeviceRegistration d WHERE d.token.id IN :tokenIds GROUP BY d.token.id")
    fun countByTokenIdIn(@Param("tokenIds") tokenIds: Collection<Long>): List<TokenDeviceCount>
}

interface TokenDeviceCount {
    val tokenId: Long
    val cnt: Long
}
