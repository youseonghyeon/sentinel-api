package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.service.FeedbackService
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice(annotations = [DashboardSidebar::class])
class SidebarModelAdvice(
    private val feedbackService: FeedbackService,
) {
    @ModelAttribute("openFeedbackCount")
    fun openFeedbackCount(): Long = feedbackService.countOpen()
}
