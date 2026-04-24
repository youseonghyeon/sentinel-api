package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.repository.AppRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

@Service
class ApplicationService(
    private val appRepository: AppRepository,
    private val appFileService: AppFileService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun resolveClientId(appId: String): Long {
        val id = appRepository.findByAppId(appId)?.id
            ?: run {
                log.warn("Application not found: appId={}", appId)
                throw SentinelException(ErrorCode.INVALID_APPLICATION)
            }
        log.debug("Resolved clientId: appId={}, id={}", appId, id)
        return id
    }

    fun findByAppId(appId: String): App? = appRepository.findByAppId(appId)

    fun register(name: String, description: String): App {
        val appId = "app_" + Random.nextInt(10_000_000, 99_999_999)
        log.info("Registering app: name={}, appId={}", name, appId)
        val app = appRepository.save(App(id = 0, name = name, description = description, appId = appId))
        log.info("App registered: id={}, appId={}", app.id, appId)
        return app
    }

    fun findAll(): List<App> = appRepository.findAll()

    fun findAllAsNameMap(): Map<String, String> = appRepository.findAll().associate { it.appId to it.name }

    @Transactional
    fun delete(id: Long) {
        log.info("Deleting app: id={}", id)
        appFileService.deleteAllByApp(id)
        appRepository.deleteById(id)
    }
}
