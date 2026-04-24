package com.seonghyeon.sentinelapi.repository

import com.seonghyeon.sentinelapi.domain.AppFile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppFileRepository : JpaRepository<AppFile, Long> {
    fun findAllByApplicationIdOrderByUploadedAtDesc(applicationId: Long): List<AppFile>
    fun findByApplicationIdAndVersion(applicationId: Long, version: String): AppFile?
    fun findByApplicationIdAndIsLatestTrue(applicationId: Long): AppFile?
    fun existsByApplicationIdAndVersion(applicationId: Long, version: String): Boolean
}
