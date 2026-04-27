package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.FeedbackStatus
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.FeedbackRepository
import com.seonghyeon.sentinelapi.service.FeedbackRateLimiter
import com.seonghyeon.sentinelapi.service.FeedbackService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FeedbackTest : AbstractIntegrationTest() {

    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var feedbackRepository: FeedbackRepository
    @Autowired lateinit var feedbackService: FeedbackService
    @Autowired lateinit var feedbackRateLimiter: FeedbackRateLimiter

    @BeforeEach
    fun resetRateLimiter() {
        feedbackRateLimiter.reset()
    }

    private fun givenApp(appId: String = "app_fb_test"): App =
        appRepository.save(App(id = 0, name = "FB-App", description = "", appId = appId))

    // ─── 다운로드 페이지 폼 제출 ────────────────────────────────────────────

    @Test
    fun `feedback 제출 - 성공 시 피드백이 저장되고 다운로드 페이지로 리다이렉트`() {
        val app = givenApp()
        val before = feedbackRepository.count()

        mockMvc.perform(
            post("/download/${app.appId}/feedback")
                .param("kind", "BUG")
                .param("message", "다운로드 후 실행하면 즉시 종료됩니다.")
                .param("contact", "user@example.com")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/download/${app.appId}"))

        assertThat(feedbackRepository.count()).isEqualTo(before + 1)
        val saved = feedbackRepository.findAll().last()
        assertThat(saved.appId).isEqualTo(app.appId)
        assertThat(saved.kind.name).isEqualTo("BUG")
        assertThat(saved.contact).isEqualTo("user@example.com")
        assertThat(saved.status).isEqualTo(FeedbackStatus.OPEN)
    }

    @Test
    fun `feedback 제출 - 메시지가 5자 미만이면 저장되지 않는다`() {
        val app = givenApp("app_fb_short")
        val before = feedbackRepository.count()

        mockMvc.perform(
            post("/download/${app.appId}/feedback")
                .param("kind", "IMPROVEMENT")
                .param("message", "짧음")
        )
            .andExpect(status().is3xxRedirection)

        assertThat(feedbackRepository.count()).isEqualTo(before)
    }

    @Test
    fun `feedback 제출 - 존재하지 않는 앱이면 저장되지 않는다`() {
        val before = feedbackRepository.count()

        mockMvc.perform(
            post("/download/app_doesnotexist/feedback")
                .param("kind", "BUG")
                .param("message", "any message here")
        )
            .andExpect(status().is3xxRedirection)

        assertThat(feedbackRepository.count()).isEqualTo(before)
    }

    @Test
    fun `feedback 제출 - 동일 IP에서 분당 한도 초과 시 거부된다`() {
        val app = givenApp("app_fb_rate")
        feedbackRepository.deleteAll()

        repeat(5) {
            mockMvc.perform(
                post("/download/${app.appId}/feedback")
                    .param("kind", "BUG")
                    .param("message", "rate limit body $it")
            ).andExpect(status().is3xxRedirection)
        }
        // 한도 초과: 저장되지 않아야 한다
        val countAt5 = feedbackRepository.count()
        mockMvc.perform(
            post("/download/${app.appId}/feedback")
                .param("kind", "BUG")
                .param("message", "rate limit overflow")
        ).andExpect(status().is3xxRedirection)

        assertThat(feedbackRepository.count()).isEqualTo(countAt5)
        assertThat(countAt5).isEqualTo(5)
    }

    @Test
    fun `feedback 제출 - 잘못된 kind 값은 거부되고 저장되지 않는다`() {
        val app = givenApp("app_fb_badkind")
        val before = feedbackRepository.count()

        mockMvc.perform(
            post("/download/${app.appId}/feedback")
                .param("kind", "GARBAGE")
                .param("message", "messages of various kinds")
        ).andExpect(status().is3xxRedirection)

        assertThat(feedbackRepository.count()).isEqualTo(before)
    }

    // ─── 매니저 대시보드 ─────────────────────────────────────────────────────

    @Test
    fun `매니저 - 피드백 페이지는 인증 필요`() {
        mockMvc.perform(get("/dashboard/feedbacks"))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @WithMockUser(username = "admin")
    fun `매니저 - 피드백 페이지에서 목록을 본다`() {
        val app = givenApp("app_fb_dash")
        feedbackService.submit(app.appId, com.seonghyeon.sentinelapi.domain.FeedbackKind.BUG, "first bug report", null, null)
        feedbackService.submit(app.appId, com.seonghyeon.sentinelapi.domain.FeedbackKind.IMPROVEMENT, "please improve UX", null, null)

        mockMvc.perform(get("/dashboard/feedbacks"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("first bug report")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("please improve UX")))
    }

    @Test
    @WithMockUser(username = "admin")
    fun `매니저 - 처리 완료로 상태가 RESOLVED로 변경된다`() {
        val app = givenApp("app_fb_resolve")
        val saved = feedbackService.submit(app.appId, com.seonghyeon.sentinelapi.domain.FeedbackKind.BUG, "to be resolved soon", null, null)

        mockMvc.perform(post("/dashboard/feedbacks/${saved.id}/resolve"))
            .andExpect(status().is3xxRedirection)

        val reloaded = feedbackRepository.findById(saved.id).orElseThrow()
        assertThat(reloaded.status).isEqualTo(FeedbackStatus.RESOLVED)
        assertThat(reloaded.resolvedAt).isNotNull
    }

    @Test
    @WithMockUser(username = "admin")
    fun `매니저 - 다시 열기로 상태가 OPEN으로 돌아간다`() {
        val app = givenApp("app_fb_reopen")
        val saved = feedbackService.submit(app.appId, com.seonghyeon.sentinelapi.domain.FeedbackKind.BUG, "reopen this please", null, null)
        feedbackService.markResolved(saved.id)

        mockMvc.perform(post("/dashboard/feedbacks/${saved.id}/reopen"))
            .andExpect(status().is3xxRedirection)

        val reloaded = feedbackRepository.findById(saved.id).orElseThrow()
        assertThat(reloaded.status).isEqualTo(FeedbackStatus.OPEN)
        assertThat(reloaded.resolvedAt).isNull()
    }

    @Test
    @WithMockUser(username = "admin")
    fun `매니저 - 삭제로 피드백이 제거된다`() {
        val app = givenApp("app_fb_delete")
        val saved = feedbackService.submit(app.appId, com.seonghyeon.sentinelapi.domain.FeedbackKind.BUG, "delete this row", null, null)

        mockMvc.perform(post("/dashboard/feedbacks/${saved.id}/delete"))
            .andExpect(status().is3xxRedirection)

        assertThat(feedbackRepository.existsById(saved.id)).isFalse()
    }

    // ─── 사이드바 뱃지 카운트 ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin")
    fun `사이드바 - 미처리 피드백 수가 빨간 뱃지로 표시된다`() {
        val app = givenApp("app_fb_badge")
        feedbackService.submit(app.appId, com.seonghyeon.sentinelapi.domain.FeedbackKind.BUG, "open badge test 1", null, null)
        feedbackService.submit(app.appId, com.seonghyeon.sentinelapi.domain.FeedbackKind.BUG, "open badge test 2", null, null)
        val resolved = feedbackService.submit(app.appId, com.seonghyeon.sentinelapi.domain.FeedbackKind.BUG, "resolved", null, null)
        feedbackService.markResolved(resolved.id)

        mockMvc.perform(get("/dashboard/feedbacks"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"nav-badge\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(">2<")))
    }

    @Test
    @WithMockUser(username = "admin")
    fun `사이드바 - 미처리 피드백이 0이면 뱃지가 표시되지 않는다`() {
        feedbackRepository.deleteAll()

        mockMvc.perform(get("/dashboard/feedbacks"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("class=\"nav-badge\""))))
    }
}
