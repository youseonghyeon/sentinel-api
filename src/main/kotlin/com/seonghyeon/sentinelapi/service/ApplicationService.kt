package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.repository.AppRepository
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class ApplicationService(private val appRepository: AppRepository) {

    fun resolveClientId(appId: String): Long =
        appRepository.findByAppId(appId)?.id
            ?: throw SentinelException(ErrorCode.INVALID_APPLICATION)

    fun register(name: String, description: String): App {
        val appId = "app_" + Random.nextInt(10_000_000, 99_999_999)
        return appRepository.save(App(id = 0, name = name, description = description, appId = appId))
    }
}
