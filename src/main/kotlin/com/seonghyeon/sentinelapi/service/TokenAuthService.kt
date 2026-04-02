package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
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

        if (token.expireDate.isBefore(LocalDate.now())) {
            log.warn("Token expired: token={}, expireDate={}", tokenStr, token.expireDate)
            throw SentinelException(ErrorCode.EXPIRED_TOKEN)
        }

        log.info("Token valid: token={}, clientId={}", tokenStr, clientId)
        return token
    }

    fun generate(appId: Long, expireDate: LocalDate): Token {
        val app = appRepository.findById(appId)
            .orElseThrow { SentinelException(ErrorCode.INVALID_APPLICATION) }
        val tokenStr = UUID.randomUUID().toString().replace("-", "")
        val token = tokenRepository.save(Token(id = 0, application = app, tokenStr = tokenStr, expireDate = expireDate))
        log.info("Token generated: tokenId={}, appId={}, expireDate={}", token.id, appId, expireDate)
        return token
    }
}
