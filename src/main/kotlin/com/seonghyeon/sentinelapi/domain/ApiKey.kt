package com.seonghyeon.sentinelapi.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "api_keys")
class ApiKey(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(name = "key_str", nullable = false, unique = true)
    val keyStr: String,

    @Column(name = "description", nullable = false)
    val description: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
)
