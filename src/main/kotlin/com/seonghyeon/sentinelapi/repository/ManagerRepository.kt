package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.Manager
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ManagerRepository : JpaRepository<Manager, Long> {
    fun findByUsername(username: String): Manager?
}
