package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.repository.ManagerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class ManagerServiceTest : AbstractIntegrationTest() {

    @Autowired lateinit var managerService: ManagerService
    @Autowired lateinit var managerRepository: ManagerRepository
    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `changePassword - 현재 비밀번호와 확인값이 올바르면 비밀번호를 변경한다`() {
        val manager = managerService.register("password-change-success", "current-password")

        managerService.changePassword(
            manager.id,
            "current-password",
            "new-password",
            "new-password",
        )

        val updated = managerRepository.findById(manager.id).orElseThrow()
        assertThat(passwordEncoder.matches("new-password", updated.password)).isTrue()
        assertThat(passwordEncoder.matches("current-password", updated.password)).isFalse()
    }

    @Test
    fun `changePassword - 현재 비밀번호가 다르면 변경하지 않는다`() {
        val manager = managerService.register("password-change-current", "current-password")

        val exception = assertThrows<SentinelException> {
            managerService.changePassword(manager.id, "wrong-password", "new-password", "new-password")
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD)
        assertThat(passwordEncoder.matches("current-password", manager.password)).isTrue()
    }

    @Test
    fun `changePassword - 새 비밀번호 확인이 다르면 변경하지 않는다`() {
        val manager = managerService.register("password-change-confirm", "current-password")

        val exception = assertThrows<SentinelException> {
            managerService.changePassword(manager.id, "current-password", "new-password", "different-password")
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH)
        assertThat(passwordEncoder.matches("current-password", manager.password)).isTrue()
    }

    @Test
    fun `changePassword - 새 비밀번호가 비어 있으면 변경하지 않는다`() {
        val manager = managerService.register("password-change-empty", "current-password")

        val exception = assertThrows<SentinelException> {
            managerService.changePassword(manager.id, "current-password", " ", " ")
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_NEW_PASSWORD)
        assertThat(passwordEncoder.matches("current-password", manager.password)).isTrue()
    }
}
