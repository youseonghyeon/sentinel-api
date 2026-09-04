package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.LoginHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface LoginHistoryRepository : JpaRepository<LoginHistory, Long> {
    fun findAllBy(pageable: Pageable): Page<LoginHistory>
    fun findByAppIdIn(appIds: Collection<String>, pageable: Pageable): Page<LoginHistory>
    fun findByTokenContainingIgnoreCase(token: String, pageable: Pageable): Page<LoginHistory>
    fun findByAppIdInAndTokenContainingIgnoreCase(appIds: Collection<String>, token: String, pageable: Pageable): Page<LoginHistory>

    @Query(
        """
        SELECT h FROM LoginHistory h
         WHERE (:appName = '' OR h.appId IN (
                   SELECT a.appId FROM App a
                    WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :appName, '%'))
               ))
           AND (:token = '' OR LOWER(h.token) LIKE LOWER(CONCAT('%', :token, '%')))
           AND (:ip = '' OR h.ip = :ip)
           AND (:deviceId = '' OR h.deviceId = :deviceId)
           AND (:filterFrom = false OR h.createdAt >= :fromInclusive)
           AND (:filterTo = false OR h.createdAt < :toExclusive)
        """
    )
    fun searchDashboard(
        @Param("appName") appName: String,
        @Param("token") token: String,
        @Param("ip") ip: String,
        @Param("deviceId") deviceId: String,
        @Param("filterFrom") filterFrom: Boolean,
        @Param("fromInclusive") fromInclusive: LocalDateTime,
        @Param("filterTo") filterTo: Boolean,
        @Param("toExclusive") toExclusive: LocalDateTime,
        pageable: Pageable,
    ): Page<LoginHistory>

    @Query(
        """
        SELECT h FROM LoginHistory h
         WHERE (:appId = '' OR h.appId = :appId)
           AND (:token = '' OR LOWER(h.token) LIKE LOWER(CONCAT('%', :token, '%')))
           AND (:deviceId = '' OR h.deviceId = :deviceId)
        """
    )
    fun search(
        @Param("appId") appId: String,
        @Param("token") token: String,
        @Param("deviceId") deviceId: String,
        pageable: Pageable,
    ): Page<LoginHistory>
}
