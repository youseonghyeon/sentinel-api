package com.seonghyeon.sentinelapi.domain

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "tokens",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_id", "token_str"])],
)
class Token(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    var application: App,

    @Column(name = "token_str", nullable = false)
    var tokenStr: String,

    @Column(name = "expire_date", nullable = false)
    var expireDate: LocalDate,

    @Column(name = "max_device_count", nullable = false)
    var maxDeviceCount: Int = 1,

) {
}
