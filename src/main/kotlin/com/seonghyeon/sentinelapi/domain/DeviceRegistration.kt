package com.seonghyeon.sentinelapi.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "device_registrations",
    uniqueConstraints = [UniqueConstraint(columnNames = ["token_id", "device_id"])],
)
class DeviceRegistration(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id", nullable = false)
    val token: Token,

    @Column(name = "device_id", nullable = false)
    val deviceId: String,

    @Column(name = "registered_at", nullable = false)
    val registeredAt: LocalDateTime,

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: LocalDateTime,
)
