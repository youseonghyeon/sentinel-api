package com.seonghyeon.sentinelapi.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "feedbacks")
class Feedback(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long,

    @Column(name = "app_id", nullable = false)
    val appId: String,

    @Column(name = "kind", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val kind: FeedbackKind,

    @Column(name = "message", nullable = false, length = 4000)
    val message: String,

    @Column(name = "contact", length = 200)
    val contact: String?,

    @Column(name = "ip", length = 64)
    val ip: String?,

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: FeedbackStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @Column(name = "resolved_at")
    var resolvedAt: LocalDateTime?,
)

enum class FeedbackKind { BUG, IMPROVEMENT }
enum class FeedbackStatus { OPEN, RESOLVED }
