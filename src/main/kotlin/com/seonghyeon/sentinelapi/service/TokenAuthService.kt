package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@Service
class TokenAuthService(
    private val tokenRepository: TokenRepository,
    private val appRepository: AppRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun check(tokenStr: String, clientId: Long): Token {
        log.info("Token check: token={}, clientId={}", tokenStr, clientId)
        val token = tokenRepository.findByTokenStrAndApplicationId(tokenStr, clientId)
            ?: run {
                log.warn("Token not found: token={}, clientId={}", tokenStr, clientId)
                throw SentinelException(ErrorCode.INVALID_TOKEN)
            }

        if (token.expireDate.isBefore(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
            log.warn("Token expired: token={}, expireDate={}", tokenStr, token.expireDate)
            throw SentinelException(ErrorCode.EXPIRED_TOKEN)
        }

        log.info("Token valid: token={}, clientId={}", tokenStr, clientId)
        return token
    }

    fun findPage(appName: String?, tokenStr: String?, pageable: Pageable): Page<Token> = when {
        !appName.isNullOrBlank() -> tokenRepository.findByApplication_NameContainingIgnoreCase(appName!!, pageable)
        !tokenStr.isNullOrBlank() -> tokenRepository.findByTokenStrContainingIgnoreCase(tokenStr!!, pageable)
        else -> tokenRepository.findAllBy(pageable)
    }

    fun updateExpireDate(id: Long, expireDate: LocalDate) {
        val token = tokenRepository.findById(id).orElseThrow { SentinelException(ErrorCode.INVALID_TOKEN) }
        token.expireDate = expireDate
        tokenRepository.save(token)
    }

    fun updateMaxDeviceCount(id: Long, maxDeviceCount: Int) {
        val token = tokenRepository.findById(id).orElseThrow { SentinelException(ErrorCode.INVALID_TOKEN) }
        token.maxDeviceCount = maxDeviceCount
        tokenRepository.save(token)
    }

    fun delete(id: Long) = tokenRepository.deleteById(id)

    fun generate(appId: Long, expireDate: LocalDate, maxDeviceCount: Int = 1): Token {
        val app = appRepository.findById(appId)
            .orElseThrow { SentinelException(ErrorCode.INVALID_APPLICATION) }
        val tokenStr = UUID.randomUUID().toString().replace("-", "")
        val token = tokenRepository.save(Token(id = 0, application = app, tokenStr = tokenStr, expireDate = expireDate, maxDeviceCount = maxDeviceCount))
        log.info("Token generated: tokenId={}, appId={}, expireDate={}, maxDeviceCount={}", token.id, appId, expireDate, maxDeviceCount)
        return token
    }
}
