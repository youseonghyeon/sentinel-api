package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.LoginHistory
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime

class LoginHistoryDashboardTest : AbstractIntegrationTest() {

    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var loginHistoryRepository: LoginHistoryRepository

    @Test
    @WithMockUser(username = "admin")
    fun `히스토리 페이지 - KST 날짜 범위와 IP와 기기 ID를 함께 검색한다`() {
        val app = givenApp("DashboardFilterUnique", "app_dashboard_filter")
        givenHistory(app.appId, "before-range", "203.0.113.1", "device-target", LocalDateTime.of(2026, 9, 3, 14, 59))
        givenHistory(app.appId, "range-start", "203.0.113.1", "device-target", LocalDateTime.of(2026, 9, 3, 15, 0))
        givenHistory(app.appId, "range-end", "203.0.113.1", "device-target", LocalDateTime.of(2026, 9, 4, 14, 59))
        givenHistory(app.appId, "after-range", "203.0.113.1", "device-target", LocalDateTime.of(2026, 9, 4, 15, 0))
        givenHistory(app.appId, "wrong-ip", "198.51.100.1", "device-target", LocalDateTime.of(2026, 9, 4, 3, 0))
        givenHistory(app.appId, "wrong-device", "203.0.113.1", "device-other", LocalDateTime.of(2026, 9, 4, 3, 0))

        mockMvc.perform(
            get("/dashboard/history")
                .param("appName", "DashboardFilterUnique")
                .param("ip", "203.0.113.1")
                .param("deviceId", "device-target")
                .param("fromDate", "2026-09-04")
                .param("toDate", "2026-09-04")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("range-start")))
            .andExpect(content().string(containsString("range-end")))
            .andExpect(content().string(not(containsString("before-range"))))
            .andExpect(content().string(not(containsString("after-range"))))
            .andExpect(content().string(not(containsString("wrong-ip"))))
            .andExpect(content().string(not(containsString("wrong-device"))))
    }

    @Test
    @WithMockUser(username = "admin")
    fun `히스토리 페이지 - 많은 페이지가 있어도 현재 주변 번호만 표시한다`() {
        val app = givenApp("PaginationWindowUnique", "app_pagination_window")
        loginHistoryRepository.saveAll(
            (1..160).map { index ->
                LoginHistory(
                    id = 0,
                    token = "pagination-token-$index",
                    appId = app.appId,
                    ip = "127.0.0.1",
                    deviceId = null,
                    createdAt = LocalDateTime.of(2026, 9, 1, 0, 0).plusMinutes(index.toLong()),
                )
            }
        )

        mockMvc.perform(
            get("/dashboard/history")
                .param("appName", "PaginationWindowUnique")
                .param("page", "3")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("page=1")))
            .andExpect(content().string(containsString("page=5")))
            .andExpect(content().string(not(containsString("page=6"))))
            .andExpect(content().string(containsString("page=7")))
    }

    @Test
    @WithMockUser(username = "admin")
    fun `히스토리 페이지 - 음수 페이지는 첫 페이지로 보정한다`() {
        mockMvc.perform(get("/dashboard/history").param("page", "-1"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "admin")
    fun `히스토리 페이지 - 시작일이 종료일보다 늦으면 안내한다`() {
        mockMvc.perform(
            get("/dashboard/history")
                .param("fromDate", "2026-09-05")
                .param("toDate", "2026-09-04")
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("시작일은 종료일보다 늦을 수 없습니다.")))
    }

    @Test
    @WithMockUser(username = "admin")
    fun `히스토리 페이지 - 날짜별 접근 차트는 조회 종료일 기준 최근 1개월만 표시한다`() {
        val app = givenApp("DailyChartMonthUnique", "app_daily_chart_month")
        givenHistory(app.appId, "outside-chart-range", "203.0.113.10", null, LocalDateTime.of(2026, 8, 4, 14, 59))
        givenHistory(app.appId, "chart-range-start", "203.0.113.11", null, LocalDateTime.of(2026, 8, 4, 15, 0))
        givenHistory(app.appId, "chart-range-end", "203.0.113.12", null, LocalDateTime.of(2026, 9, 4, 14, 59))

        mockMvc.perform(
            get("/dashboard/history")
                .param("appName", "DailyChartMonthUnique")
                .param("toDate", "2026-09-04")
        )
            .andExpect(status().isOk)
            .andExpect(model().attribute("dailyChartFromDate", LocalDate.of(2026, 8, 5)))
            .andExpect(model().attribute("dailyChartToDate", LocalDate.of(2026, 9, 4)))
            .andExpect(
                model().attribute(
                    "dailyCounts",
                    mapOf("2026-08-05" to 1, "2026-09-04" to 1),
                )
            )
            .andExpect(content().string(containsString("날짜별 접근 수 (최근 1개월 · 검색 조건 적용)")))
    }

    private fun givenApp(name: String, appId: String): App =
        appRepository.save(App(id = 0, name = name, description = "", appId = appId))

    private fun givenHistory(
        appId: String,
        token: String,
        ip: String,
        deviceId: String?,
        createdAt: LocalDateTime,
    ): LoginHistory = loginHistoryRepository.save(
        LoginHistory(
            id = 0,
            token = token,
            appId = appId,
            ip = ip,
            deviceId = deviceId,
            createdAt = createdAt,
        )
    )
}
