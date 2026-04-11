package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.App
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppRepository : JpaRepository<App, Long> {
    fun findByAppId(appId: String): App?
    fun findByNameContainingIgnoreCase(name: String): List<App>
}
