package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.ApiKey
import com.seonghyeon.sentinelapi.repository.ApiKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(description: String): ApiKey {
        val keyStr = UUID.randomUUID().toString().replace("-", "")
        val apiKey = apiKeyRepository.save(
            ApiKey(id = 0, keyStr = keyStr, description = description, createdAt = LocalDateTime.now())
        )
        log.info("API key generated: id={}, description={}", apiKey.id, description)
        return apiKey
    }

    fun validate(keyStr: String): ApiKey =
        apiKeyRepository.findByKeyStr(keyStr)
            ?: throw SentinelException(ErrorCode.INVALID_API_KEY)

    fun delete(id: Long) = apiKeyRepository.deleteById(id)

    fun findAll(): List<ApiKey> = apiKeyRepository.findAll()
}
