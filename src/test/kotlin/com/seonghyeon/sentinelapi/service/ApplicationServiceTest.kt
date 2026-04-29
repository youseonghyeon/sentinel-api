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

class ApplicationServiceTest : AbstractIntegrationTest() {

    @Autowired lateinit var applicationService: ApplicationService
    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var tokenRepository: TokenRepository

    @Test
    fun `register - 앱을 생성하고 반환한다`() {
        val app = applicationService.register("MyApp", "설명")

        assertThat(app.name).isEqualTo("MyApp")
        assertThat(app.description).isEqualTo("설명")
        assertThat(app.appId).startsWith("app_")
        assertThat(app.id).isGreaterThan(0)
    }

    @Test
    fun `resolveClientId - 유효한 appId면 내부 id를 반환한다`() {
        val saved = appRepository.save(App(id = 0, name = "TestApp", description = "", appId = "app_resolve01"))

        val clientId = applicationService.resolveClientId("app_resolve01")

        assertThat(clientId).isEqualTo(saved.id)
    }

    @Test
    fun `resolveClientId - 존재하지 않는 appId면 INVALID_APPLICATION`() {
        val ex = assertThrows<SentinelException> { applicationService.resolveClientId("app_unknown") }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.INVALID_APPLICATION)
    }

    @Test
    fun `findAll - 등록된 앱 목록을 반환한다`() {
        appRepository.save(App(id = 0, name = "App1", description = "", appId = "app_all01"))
        appRepository.save(App(id = 0, name = "App2", description = "", appId = "app_all02"))

        val result = applicationService.findAll()

        assertThat(result.map { it.appId }).contains("app_all01", "app_all02")
    }

    @Test
    fun `findAllAsNameMap - appId를 키로 앱 이름 맵을 반환한다`() {
        appRepository.save(App(id = 0, name = "App1", description = "", appId = "app_map01"))
        appRepository.save(App(id = 0, name = "App2", description = "", appId = "app_map02"))

        val map = applicationService.findAllAsNameMap()

        assertThat(map["app_map01"]).isEqualTo("App1")
        assertThat(map["app_map02"]).isEqualTo("App2")
    }

    @Test
    fun `delete - 앱을 삭제한다`() {
        val app = appRepository.save(App(id = 0, name = "ToDelete", description = "", appId = "app_del01"))

        applicationService.delete(app.id)

        assertThat(appRepository.findById(app.id)).isEmpty
    }

    @Test
    fun `delete - 사용중인 토큰이 있으면 APP_IN_USE 예외를 던진다`() {
        val app = appRepository.save(App(id = 0, name = "InUse", description = "", appId = "app_inuse01"))
        tokenRepository.save(
            Token(
                id = 0,
                application = app,
                tokenStr = "in-use-token",
                expireDate = LocalDate.now().plusDays(30),
            )
        )

        val ex = assertThrows<SentinelException> { applicationService.delete(app.id) }

        assertThat(ex.errorCode).isEqualTo(ErrorCode.APP_IN_USE)
    }

    @Test
    fun `delete - 사용중인 토큰이 있으면 앱을 삭제하지 않는다`() {
        val app = appRepository.save(App(id = 0, name = "InUse", description = "", appId = "app_inuse02"))
        tokenRepository.save(
            Token(
                id = 0,
                application = app,
                tokenStr = "in-use-token-2",
                expireDate = LocalDate.now().plusDays(30),
            )
        )

        runCatching { applicationService.delete(app.id) }

        assertThat(appRepository.findById(app.id)).isPresent
    }

    @Test
    fun `delete - 만료된 토큰이라도 존재하면 APP_IN_USE 예외를 던진다`() {
        val app = appRepository.save(App(id = 0, name = "ExpiredOnly", description = "", appId = "app_inuse03"))
        tokenRepository.save(
            Token(
                id = 0,
                application = app,
                tokenStr = "expired-token",
                expireDate = LocalDate.now().minusDays(1),
            )
        )

        val ex = assertThrows<SentinelException> { applicationService.delete(app.id) }

        assertThat(ex.errorCode).isEqualTo(ErrorCode.APP_IN_USE)
    }

    @Test
    fun `delete - 토큰을 모두 제거한 후에는 정상 삭제된다`() {
        val app = appRepository.save(App(id = 0, name = "Cleanup", description = "", appId = "app_inuse04"))
        val token = tokenRepository.save(
            Token(
                id = 0,
                application = app,
                tokenStr = "soon-to-go",
                expireDate = LocalDate.now().plusDays(30),
            )
        )
        tokenRepository.deleteById(token.id)
        tokenRepository.flush()

        applicationService.delete(app.id)

        assertThat(appRepository.findById(app.id)).isEmpty
    }
}
