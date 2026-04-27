package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.DeviceRegistration
import com.seonghyeon.sentinelapi.domain.LoginHistory
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.DeviceRegistrationRepository
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import com.seonghyeon.sentinelapi.service.ApiKeyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class ManagerApiControllerTest : AbstractIntegrationTest() {

    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var tokenRepository: TokenRepository
    @Autowired lateinit var deviceRegistrationRepository: DeviceRegistrationRepository
    @Autowired lateinit var loginHistoryRepository: LoginHistoryRepository
    @Autowired lateinit var apiKeyService: ApiKeyService

    private fun givenApp(appId: String = "app_mgr0001", name: String = "TestApp"): App =
        appRepository.save(App(id = 0, name = name, description = "", appId = appId))

    private fun givenToken(app: App, tokenStr: String, maxDeviceCount: Int = 1): Token =
        tokenRepository.save(Token(id = 0, application = app, tokenStr = tokenStr, expireDate = LocalDate.now().plusDays(30), maxDeviceCount = maxDeviceCount))

    private fun givenDevice(token: Token, deviceId: String): DeviceRegistration =
        deviceRegistrationRepository.save(DeviceRegistration(id = 0, token = token, deviceId = deviceId, registeredAt = LocalDateTime.now(ZoneOffset.UTC), lastSeenAt = LocalDateTime.now(ZoneOffset.UTC)))

    private fun apiKey() = apiKeyService.generate("test key").keyStr

    private val expireDate: LocalDate = LocalDate.now().plusDays(30)

    // ─── POST /users (create token) ─────────────────────────────────────────

    @Test
    fun `create user - 유효한 ApiKey와 앱이면 200과 토큰을 반환한다`() {
        val app = givenApp()
        val key = apiKey()

        mockMvc.perform(
            post("/api/v1/manager/users")
                .header("X-Api-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"${app.appId}","expireDate":"$expireDate"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.expireDate").value(expireDate.toString()))
            .andExpect(jsonPath("$.maxDeviceCount").value(1))
            .andExpect(jsonPath("$.appId").value(app.appId))
            .andExpect(jsonPath("$.appName").value(app.name))
    }

    @Test
    fun `create user - maxDeviceCount 지정 시 그 값으로 저장된다`() {
        val app = givenApp("app_mgr_max")
        val key = apiKey()

        mockMvc.perform(
            post("/api/v1/manager/users")
                .header("X-Api-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"${app.appId}","expireDate":"$expireDate","maxDeviceCount":5}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.maxDeviceCount").value(5))
    }

    @Test
    fun `create user - maxDeviceCount 0(무제한)이면 0으로 저장된다`() {
        val app = givenApp("app_mgr_unl")
        val key = apiKey()

        mockMvc.perform(
            post("/api/v1/manager/users")
                .header("X-Api-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"${app.appId}","expireDate":"$expireDate","maxDeviceCount":0}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.maxDeviceCount").value(0))
    }

    @Test
    fun `create user - 음수 maxDeviceCount는 401 INVALID_APPLICATION`() {
        val app = givenApp("app_mgr_neg")
        val key = apiKey()

        mockMvc.perform(
            post("/api/v1/manager/users")
                .header("X-Api-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"${app.appId}","expireDate":"$expireDate","maxDeviceCount":-1}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_APPLICATION"))
    }

    @Test
    fun `create user - X-Api-Key 헤더 없으면 401 INVALID_API_KEY`() {
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
    fun `create user - 잘못된 ApiKey면 401 INVALID_API_KEY`() {
        mockMvc.perform(
            post("/api/v1/manager/users")
                .header("X-Api-Key", "invalid-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"app_mgr0001","expireDate":"$expireDate"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
    }

    @Test
    fun `create user - 존재하지 않는 앱이면 401 INVALID_APPLICATION`() {
        mockMvc.perform(
            post("/api/v1/manager/users")
                .header("X-Api-Key", apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"appId":"app_nonexistent","expireDate":"$expireDate"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_APPLICATION"))
    }

    // ─── GET /users (list) ──────────────────────────────────────────────────

    @Test
    fun `list users - X-Api-Key 없으면 401`() {
        mockMvc.perform(get("/api/v1/manager/users"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
    }

    @Test
    fun `list users - 앱별 필터링이 가능하다`() {
        val a = givenApp("app_list_a", "A")
        val b = givenApp("app_list_b", "B")
        givenToken(a, "tok-a-1")
        givenToken(a, "tok-a-2")
        givenToken(b, "tok-b-1")

        mockMvc.perform(
            get("/api/v1/manager/users")
                .header("X-Api-Key", apiKey())
                .param("appId", a.appId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].appId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo(a.appId))))
    }

    @Test
    fun `list users - q로 토큰 문자열 부분 검색`() {
        val a = givenApp("app_list_q")
        givenToken(a, "uniqueToken123")
        givenToken(a, "otherToken")

        mockMvc.perform(
            get("/api/v1/manager/users")
                .header("X-Api-Key", apiKey())
                .param("q", "unique")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].token").value("uniqueToken123"))
    }

    @Test
    fun `list users - 존재하지 않는 appId 필터는 401 INVALID_APPLICATION`() {
        mockMvc.perform(
            get("/api/v1/manager/users")
                .header("X-Api-Key", apiKey())
                .param("appId", "app_doesnotexist")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_APPLICATION"))
    }

    // ─── GET /users/{id} ────────────────────────────────────────────────────

    @Test
    fun `get user - 단건 조회`() {
        val app = givenApp("app_get_one")
        val token = givenToken(app, "tok-one")

        mockMvc.perform(
            get("/api/v1/manager/users/${token.id}")
                .header("X-Api-Key", apiKey())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(token.id))
            .andExpect(jsonPath("$.token").value("tok-one"))
    }

    @Test
    fun `get user - 없는 ID면 401 INVALID_TOKEN`() {
        mockMvc.perform(
            get("/api/v1/manager/users/999999")
                .header("X-Api-Key", apiKey())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
    }

    // ─── PATCH /users/{id} ──────────────────────────────────────────────────

    @Test
    fun `update user - expireDate와 maxDeviceCount를 갱신한다`() {
        val app = givenApp("app_patch")
        val token = givenToken(app, "tok-patch", maxDeviceCount = 1)
        val newDate = LocalDate.now().plusDays(60)

        mockMvc.perform(
            patch("/api/v1/manager/users/${token.id}")
                .header("X-Api-Key", apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"expireDate":"$newDate","maxDeviceCount":7}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expireDate").value(newDate.toString()))
            .andExpect(jsonPath("$.maxDeviceCount").value(7))

        val reloaded = tokenRepository.findById(token.id).orElseThrow()
        assertThat(reloaded.expireDate).isEqualTo(newDate)
        assertThat(reloaded.maxDeviceCount).isEqualTo(7)
    }

    @Test
    fun `update user - 일부 필드만 보내면 나머지는 유지된다`() {
        val app = givenApp("app_patch_partial")
        val token = givenToken(app, "tok-patch-2", maxDeviceCount = 3)

        mockMvc.perform(
            patch("/api/v1/manager/users/${token.id}")
                .header("X-Api-Key", apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"maxDeviceCount":5}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.expireDate").value(token.expireDate.toString()))
            .andExpect(jsonPath("$.maxDeviceCount").value(5))
    }

    @Test
    fun `update user - 음수 maxDeviceCount는 401 INVALID_APPLICATION`() {
        val app = givenApp("app_patch_neg")
        val token = givenToken(app, "tok-patch-3")

        mockMvc.perform(
            patch("/api/v1/manager/users/${token.id}")
                .header("X-Api-Key", apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"maxDeviceCount":-1}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_APPLICATION"))
    }

    // ─── DELETE /users/{id} ─────────────────────────────────────────────────

    @Test
    fun `delete user - 토큰을 삭제한다`() {
        val app = givenApp("app_del_tok")
        val token = givenToken(app, "tok-del")

        mockMvc.perform(
            delete("/api/v1/manager/users/${token.id}")
                .header("X-Api-Key", apiKey())
        ).andExpect(status().isNoContent)

        assertThat(tokenRepository.existsById(token.id)).isFalse()
    }

    @Test
    fun `delete user - 없는 ID면 401 INVALID_TOKEN`() {
        mockMvc.perform(
            delete("/api/v1/manager/users/999999")
                .header("X-Api-Key", apiKey())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
    }

    // ─── GET /users/{id}/devices ────────────────────────────────────────────

    @Test
    fun `list devices - 등록된 기기들을 반환`() {
        val app = givenApp("app_dev_list")
        val token = givenToken(app, "tok-dev", maxDeviceCount = 0)
        givenDevice(token, "dev-A")
        givenDevice(token, "dev-B")

        mockMvc.perform(
            get("/api/v1/manager/users/${token.id}/devices")
                .header("X-Api-Key", apiKey())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].deviceId").value(org.hamcrest.Matchers.containsInAnyOrder("dev-A", "dev-B")))
    }

    @Test
    fun `list devices - 없는 토큰이면 401 INVALID_TOKEN`() {
        mockMvc.perform(
            get("/api/v1/manager/users/999999/devices")
                .header("X-Api-Key", apiKey())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
    }

    // ─── DELETE /users/{id}/devices/{deviceId} ──────────────────────────────

    @Test
    fun `remove device - 특정 기기를 강제 로그아웃`() {
        val app = givenApp("app_dev_kick")
        val token = givenToken(app, "tok-dev-kick", maxDeviceCount = 0)
        givenDevice(token, "dev-keep")
        givenDevice(token, "dev-kick")

        mockMvc.perform(
            delete("/api/v1/manager/users/${token.id}/devices/dev-kick")
                .header("X-Api-Key", apiKey())
        ).andExpect(status().isNoContent)

        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "dev-kick")).isNull()
        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "dev-keep")).isNotNull
    }

    // ─── GET /apps ──────────────────────────────────────────────────────────

    @Test
    fun `list apps - 모든 앱을 반환`() {
        givenApp("app_l_1", "Alpha")
        givenApp("app_l_2", "Beta")

        mockMvc.perform(
            get("/api/v1/manager/apps")
                .header("X-Api-Key", apiKey())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.appId=='app_l_1')].name").value("Alpha"))
            .andExpect(jsonPath("$[?(@.appId=='app_l_2')].name").value("Beta"))
    }

    // ─── POST /apps ─────────────────────────────────────────────────────────

    @Test
    fun `create app - 앱을 등록하고 자동 생성된 appId를 반환한다`() {
        mockMvc.perform(
            post("/api/v1/manager/apps")
                .header("X-Api-Key", apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Newly","description":"hello"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Newly"))
            .andExpect(jsonPath("$.description").value("hello"))
            .andExpect(jsonPath("$.appId").value(org.hamcrest.Matchers.startsWith("app_")))
    }

    @Test
    fun `create app - 이름이 비면 401 INVALID_APPLICATION`() {
        mockMvc.perform(
            post("/api/v1/manager/apps")
                .header("X-Api-Key", apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"","description":""}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_APPLICATION"))
    }

    // ─── DELETE /apps/{id} ──────────────────────────────────────────────────

    @Test
    fun `delete app - 앱을 삭제한다`() {
        val app = givenApp("app_del_one", "Doomed")

        mockMvc.perform(
            delete("/api/v1/manager/apps/${app.id}")
                .header("X-Api-Key", apiKey())
        ).andExpect(status().isNoContent)

        assertThat(appRepository.existsById(app.id)).isFalse()
    }

    @Test
    fun `delete app - 없는 앱이면 401 INVALID_APPLICATION`() {
        mockMvc.perform(
            delete("/api/v1/manager/apps/999999")
                .header("X-Api-Key", apiKey())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_APPLICATION"))
    }

    // ─── GET /login-histories ───────────────────────────────────────────────

    @Test
    fun `list login-histories - appId 필터로 검색`() {
        loginHistoryRepository.save(LoginHistory(0, "tk1", "app_hist_a", "1.1.1.1", "dev-1", LocalDateTime.now(ZoneOffset.UTC)))
        loginHistoryRepository.save(LoginHistory(0, "tk2", "app_hist_b", "1.1.1.2", null, LocalDateTime.now(ZoneOffset.UTC)))

        mockMvc.perform(
            get("/api/v1/manager/login-histories")
                .header("X-Api-Key", apiKey())
                .param("appId", "app_hist_a")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].appId").value("app_hist_a"))
    }

    @Test
    fun `list login-histories - token과 deviceId 동시 필터`() {
        loginHistoryRepository.save(LoginHistory(0, "needle", "app_x", "1.1.1.1", "dev-target", LocalDateTime.now(ZoneOffset.UTC)))
        loginHistoryRepository.save(LoginHistory(0, "needle", "app_x", "1.1.1.2", "other", LocalDateTime.now(ZoneOffset.UTC)))
        loginHistoryRepository.save(LoginHistory(0, "haystack", "app_x", "1.1.1.3", "dev-target", LocalDateTime.now(ZoneOffset.UTC)))

        mockMvc.perform(
            get("/api/v1/manager/login-histories")
                .header("X-Api-Key", apiKey())
                .param("token", "needle")
                .param("deviceId", "dev-target")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].token").value("needle"))
            .andExpect(jsonPath("$.content[0].deviceId").value("dev-target"))
    }

    @Test
    fun `list login-histories - X-Api-Key 없으면 401`() {
        mockMvc.perform(get("/api/v1/manager/login-histories"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
    }

    // ─── 보안: 모든 새 엔드포인트가 X-Api-Key를 요구해야 한다 ────────────────

    @Test
    fun `보안 - 헤더 없이 모든 매니저 엔드포인트는 401 INVALID_API_KEY`() {
        val targets = listOf(
            "GET" to "/api/v1/manager/users",
            "GET" to "/api/v1/manager/users/1",
            "DELETE" to "/api/v1/manager/users/1",
            "GET" to "/api/v1/manager/users/1/devices",
            "DELETE" to "/api/v1/manager/users/1/devices/abc",
            "GET" to "/api/v1/manager/apps",
            "DELETE" to "/api/v1/manager/apps/1",
            "GET" to "/api/v1/manager/login-histories",
        )
        targets.forEach { (method, path) ->
            val req = when (method) {
                "GET" -> get(path)
                "DELETE" -> delete(path)
                else -> error("unhandled $method")
            }
            mockMvc.perform(req)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
        }
    }
}
