package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.domain.LoginHistory
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import com.seonghyeon.sentinelapi.utils.masked
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class LoginHistorySearchCondition(
    val appName: String? = null,
    val token: String? = null,
    val ip: String? = null,
    val deviceId: String? = null,
    val fromInclusive: LocalDateTime? = null,
    val toExclusive: LocalDateTime? = null,
)

@Service
class LoginHistoryService(
    private val loginHistoryRepository: LoginHistoryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun save(loginHistory: LoginHistory): LoginHistory {
        log.info("Saving login history: appId={}, ip={}, token={}", loginHistory.appId, loginHistory.ip, loginHistory.token.masked())
        return loginHistoryRepository.save(loginHistory)
    }

    fun findAll(): List<LoginHistory> = loginHistoryRepository.findAll()

    fun search(appName: String?, tokenStr: String?, pageable: Pageable): Page<LoginHistory> =
        search(LoginHistorySearchCondition(appName = appName, token = tokenStr), pageable)

    fun search(condition: LoginHistorySearchCondition, pageable: Pageable): Page<LoginHistory> =
        loginHistoryRepository.searchDashboard(
            appName = condition.appName.normalized(),
            token = condition.token.normalized(),
            ip = condition.ip.normalized(),
            deviceId = condition.deviceId.normalized(),
            filterFrom = condition.fromInclusive != null,
            fromInclusive = condition.fromInclusive ?: UNUSED_DATE_FILTER_VALUE,
            filterTo = condition.toExclusive != null,
            toExclusive = condition.toExclusive ?: UNUSED_DATE_FILTER_VALUE,
            pageable = pageable,
        )

    fun findAll(condition: LoginHistorySearchCondition): List<LoginHistory> =
        search(condition, Pageable.unpaged()).content

    private fun String?.normalized(): String = this?.trim().orEmpty()

    companion object {
        private val UNUSED_DATE_FILTER_VALUE = LocalDateTime.of(1970, 1, 1, 0, 0)
    }
}
