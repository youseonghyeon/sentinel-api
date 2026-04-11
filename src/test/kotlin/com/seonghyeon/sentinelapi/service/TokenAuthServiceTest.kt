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
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
    fun `generate - maxDeviceCount를 지정하면 저장된다`() {
        val app = givenApp()

        val token = tokenAuthService.generate(app.id, LocalDate.now().plusDays(30), maxDeviceCount = 3)

        assertThat(token.maxDeviceCount).isEqualTo(3)
    }

    @Test
    fun `generate - maxDeviceCount 미지정 시 기본값 1로 저장된다`() {
        val app = givenApp()

        val token = tokenAuthService.generate(app.id, LocalDate.now().plusDays(30))

        assertThat(token.maxDeviceCount).isEqualTo(1)
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

    // --- findPage ---

    private val pageableDesc = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"))

    @Test
    fun `findPage - 조건 없으면 전체 토큰을 반환한다`() {
        val app = givenApp()
        tokenAuthService.generate(app.id, LocalDate.now().plusDays(30))
        tokenAuthService.generate(app.id, LocalDate.now().plusDays(30))

        val page = tokenAuthService.findPage(null, null, pageableDesc)

        assertThat(page.totalElements).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `findPage - 앱 이름으로 검색하면 해당 앱의 토큰만 반환한다`() {
        val app = givenApp()
        tokenAuthService.generate(app.id, LocalDate.now().plusDays(30))

        val page = tokenAuthService.findPage("TestApp", null, pageableDesc)

        assertThat(page.content).allMatch { it.application.name.contains("TestApp") }
    }

    @Test
    fun `findPage - 존재하지 않는 앱 이름으로 검색하면 빈 페이지를 반환한다`() {
        val page = tokenAuthService.findPage("NonExistentApp", null, pageableDesc)

        assertThat(page.content).isEmpty()
    }

    @Test
    fun `findPage - 토큰 문자열로 검색하면 일치하는 토큰을 반환한다`() {
        val app = givenApp()
        val token = tokenAuthService.generate(app.id, LocalDate.now().plusDays(30))

        val page = tokenAuthService.findPage(null, token.tokenStr.substring(0, 6), pageableDesc)

        assertThat(page.content).anyMatch { it.tokenStr == token.tokenStr }
    }

    // --- updateExpireDate ---

    @Test
    fun `updateExpireDate - 만료일을 변경한다`() {
        val app = givenApp()
        val token = tokenAuthService.generate(app.id, LocalDate.now().plusDays(30))
        val newDate = LocalDate.now().plusDays(60)

        tokenAuthService.updateExpireDate(token.id, newDate)

        val updated = tokenRepository.findById(token.id).orElseThrow()
        assertThat(updated.expireDate).isEqualTo(newDate)
    }

    @Test
    fun `updateExpireDate - 존재하지 않는 토큰이면 INVALID_TOKEN`() {
        val ex = assertThrows<SentinelException> { tokenAuthService.updateExpireDate(99999L, LocalDate.now().plusDays(10)) }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.INVALID_TOKEN)
    }

    // --- updateMaxDeviceCount ---

    @Test
    fun `updateMaxDeviceCount - 최대 PC 수를 변경한다`() {
        val app = givenApp()
        val token = tokenAuthService.generate(app.id, LocalDate.now().plusDays(30), maxDeviceCount = 1)

        tokenAuthService.updateMaxDeviceCount(token.id, 5)

        val updated = tokenRepository.findById(token.id).orElseThrow()
        assertThat(updated.maxDeviceCount).isEqualTo(5)
    }

    @Test
    fun `updateMaxDeviceCount - 0으로 변경하면 무제한이 된다`() {
        val app = givenApp()
        val token = tokenAuthService.generate(app.id, LocalDate.now().plusDays(30), maxDeviceCount = 1)

        tokenAuthService.updateMaxDeviceCount(token.id, 0)

        val updated = tokenRepository.findById(token.id).orElseThrow()
        assertThat(updated.maxDeviceCount).isEqualTo(0)
    }

    @Test
    fun `updateMaxDeviceCount - 존재하지 않는 토큰이면 INVALID_TOKEN`() {
        val ex = assertThrows<SentinelException> { tokenAuthService.updateMaxDeviceCount(99999L, 3) }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.INVALID_TOKEN)
    }

    // --- delete ---

    @Test
    fun `delete - 토큰을 삭제한다`() {
        val app = givenApp()
        val token = tokenAuthService.generate(app.id, LocalDate.now().plusDays(30))

        tokenAuthService.delete(token.id)

        assertThat(tokenRepository.findById(token.id)).isEmpty
    }
}
