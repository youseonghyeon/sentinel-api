package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

class UserControllerTest : AbstractIntegrationTest() {

    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var tokenRepository: TokenRepository
    @Autowired lateinit var loginHistoryRepository: LoginHistoryRepository

    private fun givenApp(appId: String = "app_test0001"): App =
        appRepository.save(App(id = 0, name = "TestApp", description = "", appId = appId))

    private fun givenToken(app: App, tokenStr: String, expireDate: LocalDate = LocalDate.now().plusDays(30)): Token =
        tokenRepository.save(Token(id = 0, application = app, tokenStr = tokenStr, expireDate = expireDate))

    // --- /login/token ---

    @Test
    fun `login token - 유효한 토큰이면 200과 만료일을 반환한다`() {
        val app = givenApp()
        val expireDate = LocalDate.now().plusDays(30)
        givenToken(app, "validtoken", expireDate)

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .param("token", "validtoken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expireDate").value(expireDate.toString()))
    }

    @Test
    fun `login token - 존재하지 않는 앱이면 401 INVALID_APPLICATION`() {
        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", "app_nonexistent")
                .param("token", "anytoken")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_APPLICATION"))
    }

    @Test
    fun `login token - 존재하지 않는 토큰이면 401 INVALID_TOKEN`() {
        val app = givenApp()

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .param("token", "wrongtoken")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
    }

    @Test
    fun `login token - 만료된 토큰이면 401 EXPIRED_TOKEN`() {
        val app = givenApp()
        givenToken(app, "expiredtoken", LocalDate.now().minusDays(1))

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .param("token", "expiredtoken")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"))
    }

    @Test
    fun `login token - 성공하면 LoginHistory를 기록한다`() {
        val app = givenApp()
        givenToken(app, "histtoken")
        val countBefore = loginHistoryRepository.count()

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .param("token", "histtoken")
        )
            .andExpect(status().isOk)

        assertThat(loginHistoryRepository.count()).isEqualTo(countBefore + 1)
    }

    // --- /check/token ---

    @Test
    fun `check token - 유효한 토큰이면 200을 반환한다`() {
        val app = givenApp()
        givenToken(app, "checktoken")

        mockMvc.perform(
            post("/api/v1/auth/check/token")
                .header("X-Client-Id", app.appId)
                .param("token", "checktoken")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `check token - 만료된 토큰이면 401 EXPIRED_TOKEN`() {
        val app = givenApp()
        givenToken(app, "expiredcheck", LocalDate.now().minusDays(1))

        mockMvc.perform(
            post("/api/v1/auth/check/token")
                .header("X-Client-Id", app.appId)
                .param("token", "expiredcheck")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"))
    }

    @Test
    fun `check token - LoginHistory를 기록하지 않는다`() {
        val app = givenApp()
        givenToken(app, "nohisttoken")
        val countBefore = loginHistoryRepository.count()

        mockMvc.perform(
            post("/api/v1/auth/check/token")
                .header("X-Client-Id", app.appId)
                .param("token", "nohisttoken")
        )
            .andExpect(status().isOk)

        assertThat(loginHistoryRepository.count()).isEqualTo(countBefore)
    }
}
