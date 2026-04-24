package com.seonghyeon.sentinelapi.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity
@Table(
    name = "app_files",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_id", "version"])],
)
class AppFile(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    var application: App,

    @Column(name = "version", nullable = false)
    var version: String,

    @Column(name = "filename", nullable = false)
    var filename: String,

    @Column(name = "storage_path", nullable = false, length = 1024)
    var storagePath: String,

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,

    @Column(name = "sha256", nullable = false, length = 64)
    var sha256: String,

    @Column(name = "changelog", columnDefinition = "text")
    var changelog: String? = null,

    @Column(name = "is_latest", nullable = false)
    var isLatest: Boolean = false,

    @Column(name = "uploaded_at", nullable = false)
    var uploadedAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),

) {
}
