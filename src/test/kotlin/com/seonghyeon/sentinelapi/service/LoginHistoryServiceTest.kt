package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.LoginHistory
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.LocalDateTime
import java.time.ZoneOffset

class LoginHistoryServiceTest : AbstractIntegrationTest() {

    @Autowired lateinit var loginHistoryService: LoginHistoryService
    @Autowired lateinit var loginHistoryRepository: LoginHistoryRepository
    @Autowired lateinit var appRepository: AppRepository

    private val pageableDesc = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))

    private fun givenApp(name: String, appId: String): App =
        appRepository.save(App(id = 0, name = name, description = "", appId = appId))

    private fun givenHistory(
        appId: String,
        token: String,
        ip: String = "127.0.0.1",
        deviceId: String? = null,
        createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    ): LoginHistory =
        loginHistoryRepository.save(
            LoginHistory(id = 0, token = token, appId = appId, ip = ip, deviceId = deviceId, createdAt = createdAt)
        )

    // --- save ---

    @Test
    fun `save - 히스토리를 저장하고 반환한다`() {
        val history = LoginHistory(id = 0, token = "tok1", appId = "app_save01", ip = "1.2.3.4", deviceId = null, createdAt = LocalDateTime.now(ZoneOffset.UTC))

        val saved = loginHistoryService.save(history)

        assertThat(saved.id).isGreaterThan(0)
        assertThat(saved.token).isEqualTo("tok1")
        assertThat(saved.ip).isEqualTo("1.2.3.4")
    }

    // --- search ---

    @Test
    fun `search - 조건 없으면 전체 히스토리를 반환한다`() {
        val app = givenApp("SearchApp", "app_srch01")
        givenHistory(app.appId, "token-a")
        givenHistory(app.appId, "token-b")

        val page = loginHistoryService.search(null, null, pageableDesc)

        assertThat(page.totalElements).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `search - 앱 이름으로 검색하면 해당 앱의 히스토리만 반환한다`() {
        val app1 = givenApp("TargetApp", "app_srch02")
        val app2 = givenApp("OtherApp", "app_srch03")
        givenHistory(app1.appId, "token-c")
        givenHistory(app2.appId, "token-d")

        val page = loginHistoryService.search("TargetApp", null, pageableDesc)

        assertThat(page.content).isNotEmpty
        assertThat(page.content).allMatch { it.appId == app1.appId }
    }

    @Test
    fun `search - 존재하지 않는 앱 이름으로 검색하면 빈 페이지를 반환한다`() {
        val page = loginHistoryService.search("NonExistentApp", null, pageableDesc)

        assertThat(page.content).isEmpty()
    }

    @Test
    fun `search - 토큰 문자열로 검색하면 일치하는 히스토리를 반환한다`() {
        val app = givenApp("TokenSearchApp", "app_srch04")
        givenHistory(app.appId, "unique-token-xyz")
        givenHistory(app.appId, "other-token-abc")

        val page = loginHistoryService.search(null, "unique-token", pageableDesc)

        assertThat(page.content).hasSize(1)
        assertThat(page.content[0].token).isEqualTo("unique-token-xyz")
    }

    @Test
    fun `search - 앱 이름과 토큰을 동시에 검색하면 둘 다 일치하는 히스토리만 반환한다`() {
        val app1 = givenApp("ComboApp", "app_srch05")
        val app2 = givenApp("OtherApp", "app_srch06")
        givenHistory(app1.appId, "combo-token")
        givenHistory(app2.appId, "combo-token")

        val page = loginHistoryService.search("ComboApp", "combo-token", pageableDesc)

        assertThat(page.content).hasSize(1)
        assertThat(page.content[0].appId).isEqualTo(app1.appId)
    }

    @Test
    fun `search - IP와 기기 ID와 기간을 동시에 적용한다`() {
        val app = givenApp("DetailedSearchApp", "app_srch_detail")
        givenHistory(
            appId = app.appId,
            token = "matching-history",
            ip = "203.0.113.10",
            deviceId = "device-target",
            createdAt = LocalDateTime.of(2026, 9, 4, 3, 0),
        )
        givenHistory(
            appId = app.appId,
            token = "wrong-device",
            ip = "203.0.113.10",
            deviceId = "device-other",
            createdAt = LocalDateTime.of(2026, 9, 4, 3, 0),
        )
        givenHistory(
            appId = app.appId,
            token = "outside-period",
            ip = "203.0.113.10",
            deviceId = "device-target",
            createdAt = LocalDateTime.of(2026, 9, 5, 3, 0),
        )

        val page = loginHistoryService.search(
            LoginHistorySearchCondition(
                appName = "DetailedSearch",
                ip = "203.0.113.10",
                deviceId = "device-target",
                fromInclusive = LocalDateTime.of(2026, 9, 4, 0, 0),
                toExclusive = LocalDateTime.of(2026, 9, 5, 0, 0),
            ),
            pageableDesc,
        )

        assertThat(page.content.map { it.token }).containsExactly("matching-history")
    }

    @Test
    fun `search - 결과가 createdAt 내림차순으로 정렬된다`() {
        val app = givenApp("SortApp", "app_srch07")
        loginHistoryRepository.save(
            LoginHistory(id = 0, token = "older", appId = app.appId, ip = "1.1.1.1", deviceId = null, createdAt = LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
        )
        loginHistoryRepository.save(
            LoginHistory(id = 0, token = "newer", appId = app.appId, ip = "1.1.1.1", deviceId = null, createdAt = LocalDateTime.now(ZoneOffset.UTC))
        )

        val page = loginHistoryService.search(null, null, pageableDesc)

        val tokens = page.content.map { it.token }
        assertThat(tokens.indexOf("newer")).isLessThan(tokens.indexOf("older"))
    }

    // --- findAll ---

    @Test
    fun `findAll - 전체 히스토리를 반환한다`() {
        val app = givenApp("AllApp", "app_srch08")
        givenHistory(app.appId, "token-all-1")
        givenHistory(app.appId, "token-all-2")

        val result = loginHistoryService.findAll()

        assertThat(result.map { it.token }).contains("token-all-1", "token-all-2")
    }
}
