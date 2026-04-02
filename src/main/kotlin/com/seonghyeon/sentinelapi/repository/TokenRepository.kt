package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.Token
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TokenRepository : JpaRepository<Token, Long> {
    fun findByTokenStrAndApplicationId(tokenStr: String, applicationId: Long): Token?
}
