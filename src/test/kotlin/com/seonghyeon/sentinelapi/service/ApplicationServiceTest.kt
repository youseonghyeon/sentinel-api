package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.repository.AppRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class ApplicationServiceTest : AbstractIntegrationTest() {

    @Autowired lateinit var applicationService: ApplicationService
    @Autowired lateinit var appRepository: AppRepository

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
}
