package com.seonghyeon.sentinelapi.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "managers")
class Manager(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(name = "username", nullable = false)
    val username: String,

    @Column(name = "password", nullable = false)
    val password: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
)
