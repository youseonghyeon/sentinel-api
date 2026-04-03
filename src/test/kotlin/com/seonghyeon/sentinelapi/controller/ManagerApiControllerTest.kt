package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.service.ApiKeyService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

class ManagerApiControllerTest : AbstractIntegrationTest() {

    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var apiKeyService: ApiKeyService

    private fun givenApp(appId: String = "app_mgr0001"): App =
        appRepository.save(App(id = 0, name = "TestApp", description = "", appId = appId))

    private val expireDate: LocalDate = LocalDate.now().plusDays(30)

    @Test
    fun `create user - 유효한 ApiKey와 앱이면 200과 토큰을 반환한다`() {
        val app = givenApp()
        val apiKey = apiKeyService.generate("test key")

        mockMvc.perform(
            post("/api/v1/manager/users")
                .header("X-Api-Key", apiKey.keyStr)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"${app.appId}","expireDate":"$expireDate"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.expireDate").value(expireDate.toString()))
    }

    @Test
    fun `create user - X-Api-Key 헤더가 없으면 401 INVALID_API_KEY`() {
        givenApp()

        mockMvc.perform(
            post("/api/v1/manager/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"app_mgr0001","expireDate":"$expireDate"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
    }

    @Test
    fun `create user - 유효하지 않은 ApiKey면 401 INVALID_API_KEY`() {
        mockMvc.perform(
            post("/api/v1/manager/users")
                .header("X-Api-Key", "invalid-key-value")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"app_mgr0001","expireDate":"$expireDate"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
    }

    @Test
    fun `create user - 존재하지 않는 앱이면 401 INVALID_APPLICATION`() {
        val apiKey = apiKeyService.generate("test key")

        mockMvc.perform(
            post("/api/v1/manager/users")
                .header("X-Api-Key", apiKey.keyStr)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"app_nonexistent","expireDate":"$expireDate"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_APPLICATION"))
    }
}
