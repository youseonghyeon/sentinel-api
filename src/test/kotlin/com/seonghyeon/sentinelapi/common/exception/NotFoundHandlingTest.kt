package com.seonghyeon.sentinelapi.common.exception

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class NotFoundHandlingTest : AbstractIntegrationTest() {

    @Test
    fun `매칭되지 않는 api 경로는 404 JSON을 반환한다`() {
        mockMvc.perform(get("/api/v1/auth/totally-bogus-endpoint"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `매칭되지 않는 actuator 경로는 404 JSON을 반환한다`() {
        mockMvc.perform(get("/actuator/bogus"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `인증된 사용자가 매칭되지 않는 경로에 접근하면 dashboard apps로 리다이렉트한다`() {
        mockMvc.perform(get("/totally-random-path").with(user("admin")))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/dashboard/apps"))
    }

    @Test
    fun `인증된 사용자가 루트 경로에 접근하면 dashboard apps로 리다이렉트한다`() {
        mockMvc.perform(get("/").with(user("admin")))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", "/dashboard/apps"))
    }
}
