package com.seonghyeon.sentinelapi.domain

import jakarta.persistence.*

@Entity
@Table(name = "apps")
class App(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "description")
    val description: String,

    @Column(name = "app_id", nullable = false)
    val appId: String,

    ) {
}
