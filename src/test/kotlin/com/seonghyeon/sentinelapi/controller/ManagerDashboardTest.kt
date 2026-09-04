package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.repository.ManagerRepository
import com.seonghyeon.sentinelapi.service.ManagerService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ManagerDashboardTest : AbstractIntegrationTest() {

    @Autowired lateinit var managerService: ManagerService
    @Autowired lateinit var managerRepository: ManagerRepository
    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Test
    @WithMockUser(username = "admin")
    fun `매니저 관리 페이지 - 비밀번호 변경 필드를 표시한다`() {
        mockMvc.perform(get("/dashboard/apikeys"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("현재 비밀번호")))
            .andExpect(content().string(containsString("새 비밀번호")))
            .andExpect(content().string(containsString("새 비밀번호 확인")))
    }

    @Test
    @WithMockUser(username = "admin")
    fun `비밀번호 변경 - 입력값이 올바르면 변경하고 성공 안내로 이동한다`() {
        val manager = managerService.register("dashboard-password-success", "current-password")

        mockMvc.perform(
            post("/dashboard/managers/${manager.id}/password")
                .param("currentPassword", "current-password")
                .param("newPassword", "new-password")
                .param("newPasswordConfirmation", "new-password")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/dashboard/apikeys?success=password"))

        val updated = managerRepository.findById(manager.id).orElseThrow()
        assertThat(passwordEncoder.matches("new-password", updated.password)).isTrue()
    }

    @Test
    @WithMockUser(username = "admin")
    fun `비밀번호 변경 - 현재 비밀번호가 다르면 오류 안내와 대상 매니저를 유지한다`() {
        val manager = managerService.register("dashboard-password-error", "current-password")

        mockMvc.perform(
            post("/dashboard/managers/${manager.id}/password")
                .param("currentPassword", "wrong-password")
                .param("newPassword", "new-password")
                .param("newPasswordConfirmation", "new-password")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/dashboard/apikeys"))
            .andExpect(flash().attribute("passwordChangeError", "현재 비밀번호가 올바르지 않습니다."))
            .andExpect(flash().attribute("passwordChangeManagerId", manager.id))
    }
}
