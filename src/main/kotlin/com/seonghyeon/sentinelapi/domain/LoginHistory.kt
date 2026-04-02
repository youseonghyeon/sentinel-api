package com.seonghyeon.sentinelapi.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "login_history")
class LoginHistory(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long,

    @Column(name = "token_str", nullable = false)
    val token: String,

    @Column(name = "app_id", nullable = false)
    val appId: String,

    @Column(name = "ip", nullable = false)
    val ip: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
)
