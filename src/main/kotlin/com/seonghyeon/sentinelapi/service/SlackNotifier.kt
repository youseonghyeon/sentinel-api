package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.domain.Feedback
import com.seonghyeon.sentinelapi.domain.FeedbackKind
import com.seonghyeon.sentinelapi.repository.AppRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SlackNotifier(
    @Value("\${sentinel.slack.webhook-url:}") private val webhookUrl: String,
    private val appRepository: AppRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = RestClient.create()

    @Async
    fun notifyFeedback(feedback: Feedback) {
        if (webhookUrl.isBlank()) return
        try {
            val emoji = if (feedback.kind == FeedbackKind.BUG) ":bug:" else ":sparkles:"
            val kindLabel = if (feedback.kind == FeedbackKind.BUG) "버그 제보" else "개선 요청"
            val preview = feedback.message.take(800)
            val contactLine = feedback.contact?.takeIf { it.isNotBlank() }?.let { "\n• 연락처: $it" } ?: ""
            val ipLine = feedback.ip?.takeIf { it.isNotBlank() }?.let { "\n• IP: $it" } ?: ""
            val appName = runCatching { appRepository.findByAppId(feedback.appId)?.name }.getOrNull()
            val appLine = if (appName != null) "$appName (`${feedback.appId}`)" else "`${feedback.appId}`"
            val text = buildString {
                append("$emoji *새 피드백 - $kindLabel*")
                append("\n• App: $appLine")
                append("\n• ID: ${feedback.id}")
                append(contactLine)
                append(ipLine)
                append("\n• 내용:\n```\n$preview\n```")
            }
            restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("text" to text))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn("Slack 알림 전송 실패: feedbackId={}", feedback.id, e)
        }
    }
}
