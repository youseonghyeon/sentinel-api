package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.domain.Feedback
import com.seonghyeon.sentinelapi.domain.FeedbackKind
import com.seonghyeon.sentinelapi.domain.FeedbackStatus
import com.seonghyeon.sentinelapi.repository.FeedbackRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val slackNotifier: SlackNotifier,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun submit(appId: String, kind: FeedbackKind, message: String, contact: String?, ip: String?): Feedback {
        val saved = feedbackRepository.save(
            Feedback(
                id = 0,
                appId = appId,
                kind = kind,
                message = message,
                contact = contact,
                ip = ip,
                status = FeedbackStatus.OPEN,
                createdAt = LocalDateTime.now(ZoneOffset.UTC),
                resolvedAt = null,
            )
        )
        log.info("Feedback submitted: id={}, appId={}, kind={}", saved.id, appId, kind)
        slackNotifier.notifyFeedback(saved)
        return saved
    }

    fun countOpen(): Long = feedbackRepository.countByStatus(FeedbackStatus.OPEN)

    fun findPage(status: FeedbackStatus?, pageable: Pageable): Page<Feedback> =
        if (status == null) feedbackRepository.findAllBy(pageable)
        else feedbackRepository.findByStatus(status, pageable)

    @Transactional
    fun markResolved(id: Long) {
        val f = feedbackRepository.findById(id).orElseThrow()
        f.status = FeedbackStatus.RESOLVED
        f.resolvedAt = LocalDateTime.now(ZoneOffset.UTC)
        log.info("Feedback resolved: id={}", id)
    }

    @Transactional
    fun reopen(id: Long) {
        val f = feedbackRepository.findById(id).orElseThrow()
        f.status = FeedbackStatus.OPEN
        f.resolvedAt = null
    }

    fun delete(id: Long) {
        feedbackRepository.deleteById(id)
    }
}
