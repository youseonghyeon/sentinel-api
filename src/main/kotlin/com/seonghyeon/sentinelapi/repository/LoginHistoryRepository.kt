package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.LoginHistory
import org.springframework.data.jpa.repository.JpaRepository

interface LoginHistoryRepository : JpaRepository<LoginHistory, Long> {

}
