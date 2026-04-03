package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.Token
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TokenRepository : JpaRepository<Token, Long> {
    fun findByTokenStrAndApplicationId(tokenStr: String, applicationId: Long): Token?
    fun findAllByOrderByIdDesc(): List<Token>
    fun findByApplication_NameContainingIgnoreCaseOrderByIdDesc(appName: String): List<Token>
    fun findByTokenStrContainingIgnoreCaseOrderByIdDesc(tokenStr: String): List<Token>
}
