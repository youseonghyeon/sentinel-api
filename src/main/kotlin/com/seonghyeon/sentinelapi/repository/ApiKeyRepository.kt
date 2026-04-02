package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    fun findByKeyStr(keyStr: String): ApiKey?
}
