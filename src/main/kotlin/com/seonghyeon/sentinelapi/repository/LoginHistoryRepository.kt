package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.LoginHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface LoginHistoryRepository : JpaRepository<LoginHistory, Long> {
    fun findAllBy(pageable: Pageable): Page<LoginHistory>
    fun findByAppIdIn(appIds: Collection<String>, pageable: Pageable): Page<LoginHistory>
    fun findByTokenContainingIgnoreCase(token: String, pageable: Pageable): Page<LoginHistory>
    fun findByAppIdInAndTokenContainingIgnoreCase(appIds: Collection<String>, token: String, pageable: Pageable): Page<LoginHistory>
}
