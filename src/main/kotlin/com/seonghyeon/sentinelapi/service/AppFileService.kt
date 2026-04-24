package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.AppFile
import com.seonghyeon.sentinelapi.repository.AppFileRepository
import com.seonghyeon.sentinelapi.repository.AppRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class AppFileService(
    private val appFileRepository: AppFileRepository,
    private val appRepository: AppRepository,
    @Value("\${sentinel.storage.root}") private val storageRoot: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun upload(appId: Long, version: String, changelog: String?, file: MultipartFile): AppFile {
        if (version.isBlank()) throw IllegalArgumentException("version must not be blank")

        val app = appRepository.findById(appId)
            .orElseThrow { SentinelException(ErrorCode.INVALID_APPLICATION) }

        if (appFileRepository.existsByApplicationIdAndVersion(appId, version)) {
            throw SentinelException(ErrorCode.DUPLICATE_VERSION)
        }

        val filename = sanitizeFilename(file.originalFilename)

        val entity = appFileRepository.saveAndFlush(
            AppFile(
                id = 0,
                application = app,
                version = version,
                filename = filename,
                storagePath = "pending",
                sizeBytes = 0,
                sha256 = "",
                changelog = changelog?.ifBlank { null },
                isLatest = false,
                uploadedAt = LocalDateTime.now(ZoneOffset.UTC),
            )
        )

        val relativePath = "$appId/${entity.id}/$filename"
        val absolutePath = Path.of(storageRoot, relativePath)

        try {
            Files.createDirectories(absolutePath.parent)
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            file.inputStream.use { input ->
                Files.newOutputStream(absolutePath).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        size += n
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }

            entity.storagePath = relativePath
            entity.sizeBytes = size
            entity.sha256 = hex

            appFileRepository.findByApplicationIdAndIsLatestTrue(appId)?.let { prev ->
                if (prev.id != entity.id) prev.isLatest = false
            }
            entity.isLatest = true

            log.info("AppFile uploaded: appId={}, version={}, size={}, sha256={}", appId, version, size, hex)
            return entity
        } catch (e: Exception) {
            log.error("AppFile upload failed: appId={}, version={}", appId, version, e)
            runCatching { Files.deleteIfExists(absolutePath) }
            throw if (e is SentinelException) e else SentinelException(ErrorCode.UPLOAD_FAILED)
        }
    }

    fun findAllByApp(appId: Long): List<AppFile> =
        appFileRepository.findAllByApplicationIdOrderByUploadedAtDesc(appId)

    fun findLatestVersions(appId: Long, limit: Int): List<AppFile> =
        appFileRepository.findAllByApplicationIdOrderByUploadedAtDesc(appId).take(limit)

    fun findLatest(appId: Long): AppFile =
        appFileRepository.findByApplicationIdAndIsLatestTrue(appId)
            ?: throw SentinelException(ErrorCode.NO_FILE_AVAILABLE)

    fun findByAppAndVersion(appId: Long, version: String): AppFile =
        appFileRepository.findByApplicationIdAndVersion(appId, version)
            ?: throw SentinelException(ErrorCode.INVALID_VERSION)

    @Transactional
    fun markLatest(appId: Long, fileId: Long) {
        val target = appFileRepository.findById(fileId)
            .orElseThrow { SentinelException(ErrorCode.INVALID_VERSION) }
        if (target.application.id != appId) throw SentinelException(ErrorCode.INVALID_VERSION)
        appFileRepository.findByApplicationIdAndIsLatestTrue(appId)?.let { prev ->
            if (prev.id != target.id) prev.isLatest = false
        }
        target.isLatest = true
    }

    @Transactional
    fun delete(appId: Long, fileId: Long) {
        val entity = appFileRepository.findById(fileId)
            .orElseThrow { SentinelException(ErrorCode.INVALID_VERSION) }
        if (entity.application.id != appId) throw SentinelException(ErrorCode.INVALID_VERSION)
        val abs = Path.of(storageRoot, entity.storagePath)
        runCatching {
            Files.deleteIfExists(abs)
            Files.deleteIfExists(abs.parent)
        }
        appFileRepository.delete(entity)
        log.info("AppFile deleted: appId={}, fileId={}, version={}", appId, fileId, entity.version)
    }

    fun resolvePath(appFile: AppFile): Path {
        val path = Path.of(storageRoot, appFile.storagePath)
        if (!Files.exists(path)) throw SentinelException(ErrorCode.FILE_NOT_FOUND)
        return path
    }

    private fun sanitizeFilename(name: String?): String {
        val base = (name ?: "").replace("\\", "/").substringAfterLast('/')
        return base.ifBlank { "upload.bin" }
    }
}
