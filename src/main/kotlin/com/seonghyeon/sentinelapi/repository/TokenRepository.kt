package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.Token
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TokenRepository : JpaRepository<Token, Long> {
    fun findByTokenStrAndApplicationId(tokenStr: String, applicationId: Long): Token?
    fun findAllBy(pageable: Pageable): Page<Token>
    fun findByApplication_NameContainingIgnoreCase(appName: String, pageable: Pageable): Page<Token>
    fun findByTokenStrContainingIgnoreCase(tokenStr: String, pageable: Pageable): Page<Token>
}
