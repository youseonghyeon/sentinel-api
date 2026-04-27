package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.Feedback
import com.seonghyeon.sentinelapi.domain.FeedbackStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackRepository : JpaRepository<Feedback, Long> {
    fun countByStatus(status: FeedbackStatus): Long
    fun findAllBy(pageable: Pageable): Page<Feedback>
    fun findByStatus(status: FeedbackStatus, pageable: Pageable): Page<Feedback>
}
