package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class TokenAuthServiceTest : AbstractIntegrationTest() {

    @Autowired lateinit var tokenAuthService: TokenAuthService
    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var tokenRepository: TokenRepository

    private fun givenApp(appId: String = "app_svc0001"): App =
        appRepository.save(App(id = 0, name = "TestApp", description = "", appId = appId))

    @Test
    fun `generate - 토큰을 생성하고 반환한다`() {
        val app = givenApp()
        val expireDate = LocalDate.now().plusDays(30)

        val token = tokenAuthService.generate(app.id, expireDate)

        assertThat(token.tokenStr).isNotBlank()
        assertThat(token.expireDate).isEqualTo(expireDate)
        assertThat(token.application.id).isEqualTo(app.id)
    }

    @Test
    fun `generate - 존재하지 않는 앱이면 INVALID_APPLICATION`() {
        val ex = assertThrows<SentinelException> { tokenAuthService.generate(99999L, LocalDate.now().plusDays(30)) }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.INVALID_APPLICATION)
    }

    @Test
    fun `check - 유효한 토큰이면 토큰을 반환한다`() {
        val app = givenApp()
        val saved = tokenRepository.save(Token(id = 0, application = app, tokenStr = "svctoken", expireDate = LocalDate.now().plusDays(10)))

        val result = tokenAuthService.check("svctoken", app.id)

        assertThat(result.id).isEqualTo(saved.id)
    }

    @Test
    fun `check - 존재하지 않는 토큰이면 INVALID_TOKEN`() {
        val app = givenApp()

        val ex = assertThrows<SentinelException> { tokenAuthService.check("nonexistent", app.id) }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.INVALID_TOKEN)
    }

    @Test
    fun `check - 만료된 토큰이면 EXPIRED_TOKEN`() {
        val app = givenApp()
        tokenRepository.save(Token(id = 0, application = app, tokenStr = "expired", expireDate = LocalDate.now().minusDays(1)))

        val ex = assertThrows<SentinelException> { tokenAuthService.check("expired", app.id) }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.EXPIRED_TOKEN)
    }
}
