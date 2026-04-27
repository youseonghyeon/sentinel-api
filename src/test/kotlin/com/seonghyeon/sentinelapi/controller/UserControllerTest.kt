package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.DeviceRegistration
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.DeviceRegistrationRepository
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class UserControllerTest : AbstractIntegrationTest() {

    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var tokenRepository: TokenRepository
    @Autowired lateinit var loginHistoryRepository: LoginHistoryRepository
    @Autowired lateinit var deviceRegistrationRepository: DeviceRegistrationRepository

    private fun givenApp(appId: String = "app_test0001"): App =
        appRepository.save(App(id = 0, name = "TestApp", description = "", appId = appId))

    private fun givenToken(
        app: App,
        tokenStr: String,
        expireDate: LocalDate = LocalDate.now().plusDays(30),
        maxDeviceCount: Int = 1,
    ): Token = tokenRepository.save(Token(id = 0, application = app, tokenStr = tokenStr, expireDate = expireDate, maxDeviceCount = maxDeviceCount))

    private fun givenDevice(token: Token, deviceId: String): DeviceRegistration =
        deviceRegistrationRepository.save(DeviceRegistration(id = 0, token = token, deviceId = deviceId, registeredAt = LocalDateTime.now(ZoneOffset.UTC), lastSeenAt = LocalDateTime.now(ZoneOffset.UTC)))

    // --- /login/token ---

    @Test
    fun `login token - 유효한 토큰이면 200과 만료일을 반환한다`() {
        val app = givenApp()
        val expireDate = LocalDate.now().plusDays(30)
        givenToken(app, "validtoken", expireDate, maxDeviceCount = 0)

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .param("token", "validtoken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.appName").value(app.name))
            .andExpect(jsonPath("$.expireDate").value(expireDate.toString()))
            .andExpect(jsonPath("$.maxDeviceCount").value(0))
            .andExpect(jsonPath("$.currentTime").exists())
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
        givenToken(app, "histtoken", maxDeviceCount = 0)
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

    // ─── login/token + X-Device-Id ──────────────────────────────────────────

    @Test
    fun `login token - X-Device-Id와 함께 로그인하면 DeviceRegistration에 등록된다`() {
        val app = givenApp("app_dvc0001")
        givenToken(app, "dvc-tok-01")

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "test-guid-001")
                .param("token", "dvc-tok-01")
        ).andExpect(status().isOk)

        val token = tokenRepository.findAll().first { it.tokenStr == "dvc-tok-01" }
        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "test-guid-001")).isNotNull
    }

    @Test
    fun `login token - maxDeviceCount 1인데 X-Device-Id 없으면 400 DEVICE_ID_REQUIRED`() {
        val app = givenApp("app_dvc0002")
        givenToken(app, "dvc-tok-02", maxDeviceCount = 1)

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .param("token", "dvc-tok-02")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"))
    }

    @Test
    fun `login token - 이미 등록된 기기로 재로그인하면 200 OK`() {
        val app = givenApp("app_dvc0003")
        val token = givenToken(app, "dvc-tok-03")
        givenDevice(token, "guid-existing-003")

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-existing-003")
                .param("token", "dvc-tok-03")
        ).andExpect(status().isOk)
    }

    @Test
    fun `login token - 기기 한도 초과면 429 DEVICE_LIMIT_EXCEEDED`() {
        val app = givenApp("app_dvc0004")
        val token = givenToken(app, "dvc-tok-04", maxDeviceCount = 1)
        givenDevice(token, "guid-already-registered")

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-new-over-limit")
                .param("token", "dvc-tok-04")
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.code").value("DEVICE_LIMIT_EXCEEDED"))
    }

    @Test
    fun `login token - maxDeviceCount가 0이면 X-Device-Id 없어도 200 OK`() {
        val app = givenApp("app_dvc0005")
        givenToken(app, "dvc-tok-05", maxDeviceCount = 0)

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .param("token", "dvc-tok-05")
        ).andExpect(status().isOk)
    }

    @Test
    fun `login token - maxDeviceCount가 0이면 X-Device-Id가 있어도 등록된다`() {
        val app = givenApp("app_dvc0006")
        givenToken(app, "dvc-tok-06", maxDeviceCount = 0)

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-unlimited")
                .param("token", "dvc-tok-06")
        ).andExpect(status().isOk)

        val token = tokenRepository.findAll().first { it.tokenStr == "dvc-tok-06" }
        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-unlimited")).isNotNull
    }

    @Test
    fun `login token - LoginHistory에 deviceId가 저장된다`() {
        val app = givenApp("app_dvc0007")
        givenToken(app, "dvc-tok-07")

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-history-check")
                .param("token", "dvc-tok-07")
        ).andExpect(status().isOk)

        val history = loginHistoryRepository.findAll().last()
        assertThat(history.deviceId).isEqualTo("guid-history-check")
    }

    // ─── check/token + X-Device-Id ──────────────────────────────────────────

    @Test
    fun `check token - 등록된 기기면 200 OK`() {
        val app = givenApp("app_dvc0008")
        val token = givenToken(app, "dvc-tok-08")
        givenDevice(token, "guid-check-ok")

        mockMvc.perform(
            post("/api/v1/auth/check/token")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-check-ok")
                .param("token", "dvc-tok-08")
        ).andExpect(status().isOk)
    }

    @Test
    fun `check token - 제거된 기기면 401 DEVICE_LOGGED_OUT`() {
        val app = givenApp("app_dvc0009")
        val token = givenToken(app, "dvc-tok-09")
        givenDevice(token, "guid-removed-009")
        deviceRegistrationRepository.deleteByTokenIdAndDeviceId(token.id, "guid-removed-009")

        mockMvc.perform(
            post("/api/v1/auth/check/token")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-removed-009")
                .param("token", "dvc-tok-09")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("DEVICE_LOGGED_OUT"))
    }

    @Test
    fun `check token - X-Device-Id 없으면 기기 체크 없이 200 OK`() {
        val app = givenApp("app_dvc0010")
        givenToken(app, "dvc-tok-10")

        mockMvc.perform(
            post("/api/v1/auth/check/token")
                .header("X-Client-Id", app.appId)
                .param("token", "dvc-tok-10")
        ).andExpect(status().isOk)
    }

    // ─── GET /devices ────────────────────────────────────────────────────────

    @Test
    fun `GET devices - 등록된 기기 목록을 반환한다`() {
        val app = givenApp("app_dvc0011")
        val token = givenToken(app, "dvc-tok-11", maxDeviceCount = 0)
        givenDevice(token, "guid-list-a")
        givenDevice(token, "guid-list-b")

        mockMvc.perform(
            get("/api/v1/auth/devices")
                .header("X-Client-Id", app.appId)
                .param("token", "dvc-tok-11")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].deviceId").value(org.hamcrest.Matchers.containsInAnyOrder("guid-list-a", "guid-list-b")))
    }

    @Test
    fun `GET devices - 등록된 기기가 없으면 빈 배열을 반환한다`() {
        val app = givenApp("app_dvc0012")
        givenToken(app, "dvc-tok-12")

        mockMvc.perform(
            get("/api/v1/auth/devices")
                .header("X-Client-Id", app.appId)
                .param("token", "dvc-tok-12")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET devices - 유효하지 않은 토큰이면 401`() {
        val app = givenApp("app_dvc0013")

        mockMvc.perform(
            get("/api/v1/auth/devices")
                .header("X-Client-Id", app.appId)
                .param("token", "invalid-token")
        ).andExpect(status().isUnauthorized)
    }

    // ─── DELETE /devices/{deviceId} ──────────────────────────────────────────

    @Test
    fun `DELETE devices - 특정 기기를 제거한다`() {
        val app = givenApp("app_dvc0014")
        val token = givenToken(app, "dvc-tok-14", maxDeviceCount = 0)
        givenDevice(token, "guid-del-target")
        givenDevice(token, "guid-del-keep")

        mockMvc.perform(
            delete("/api/v1/auth/devices/guid-del-target")
                .header("X-Client-Id", app.appId)
                .param("token", "dvc-tok-14")
        ).andExpect(status().isNoContent)

        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-del-target")).isNull()
        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-del-keep")).isNotNull
    }

    @Test
    fun `DELETE devices - 제거 후 해당 기기로 check하면 DEVICE_LOGGED_OUT`() {
        val app = givenApp("app_dvc0015")
        val token = givenToken(app, "dvc-tok-15")
        givenDevice(token, "guid-del-then-check")

        mockMvc.perform(
            delete("/api/v1/auth/devices/guid-del-then-check")
                .header("X-Client-Id", app.appId)
                .param("token", "dvc-tok-15")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/check/token")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-del-then-check")
                .param("token", "dvc-tok-15")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("DEVICE_LOGGED_OUT"))
    }

    // ─── DELETE /logout ──────────────────────────────────────────────────────

    @Test
    fun `logout - 현재 기기를 DeviceRegistration에서 제거한다`() {
        val app = givenApp("app_dvc0016")
        val token = givenToken(app, "dvc-tok-16")
        givenDevice(token, "guid-logout-001")

        mockMvc.perform(
            delete("/api/v1/auth/logout")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-logout-001")
                .param("token", "dvc-tok-16")
        ).andExpect(status().isNoContent)

        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-logout-001")).isNull()
    }

    @Test
    fun `logout - 로그아웃 후 동일 기기로 재로그인하면 슬롯이 반환되어 200 OK`() {
        val app = givenApp("app_dvc0017")
        val token = givenToken(app, "dvc-tok-17", maxDeviceCount = 1)
        givenDevice(token, "guid-logout-rejoin")

        mockMvc.perform(
            delete("/api/v1/auth/logout")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-logout-rejoin")
                .param("token", "dvc-tok-17")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/login/token")
                .header("X-Client-Id", app.appId)
                .header("X-Device-Id", "guid-logout-rejoin")
                .param("token", "dvc-tok-17")
        ).andExpect(status().isOk)
    }
}
