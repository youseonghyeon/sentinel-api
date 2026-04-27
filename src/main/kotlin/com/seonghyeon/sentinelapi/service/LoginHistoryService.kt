package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.domain.LoginHistory
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import com.seonghyeon.sentinelapi.utils.masked
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class LoginHistoryService(
    private val loginHistoryRepository: LoginHistoryRepository,
    private val appRepository: AppRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun save(loginHistory: LoginHistory): LoginHistory {
        log.info("Saving login history: appId={}, ip={}, token={}", loginHistory.appId, loginHistory.ip, loginHistory.token.masked())
        return loginHistoryRepository.save(loginHistory)
    }

    fun findAll(): List<LoginHistory> = loginHistoryRepository.findAll()

    fun search(appName: String?, tokenStr: String?, pageable: Pageable): Page<LoginHistory> {
        val hasApp = !appName.isNullOrBlank()
        val hasToken = !tokenStr.isNullOrBlank()

        if (hasApp) {
            val appIds = appRepository.findByNameContainingIgnoreCase(appName!!).map { it.appId }
            if (appIds.isEmpty()) return Page.empty(pageable)
            return if (hasToken)
                loginHistoryRepository.findByAppIdInAndTokenContainingIgnoreCase(appIds, tokenStr!!, pageable)
            else
                loginHistoryRepository.findByAppIdIn(appIds, pageable)
        }

        return if (hasToken)
            loginHistoryRepository.findByTokenContainingIgnoreCase(tokenStr!!, pageable)
        else
            loginHistoryRepository.findAllBy(pageable)
    }
}
