package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.FeedbackKind
import com.seonghyeon.sentinelapi.service.AppFileService
import com.seonghyeon.sentinelapi.service.ApplicationService
import com.seonghyeon.sentinelapi.service.FeedbackRateLimiter
import com.seonghyeon.sentinelapi.service.FeedbackService
import com.seonghyeon.sentinelapi.service.TokenAuthService
import com.seonghyeon.sentinelapi.utils.clientIp
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.FileSystemResource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.nio.charset.StandardCharsets

@Controller
@RequestMapping("/download")
class DownloadController(
    private val applicationService: ApplicationService,
    private val tokenAuthService: TokenAuthService,
    private val appFileService: AppFileService,
    private val feedbackService: FeedbackService,
    private val feedbackRateLimiter: FeedbackRateLimiter,
) {

    @GetMapping("/{appId}")
    fun page(@PathVariable appId: String, model: Model): String {
        val app = applicationService.findByAppId(appId)
        if (app == null) {
            model.addAttribute("notFound", true)
            model.addAttribute("appIdInput", appId)
            return "download"
        }
        model.addAttribute("app", app)
        model.addAttribute("files", appFileService.findLatestVersions(app.id, 2))
        return "download"
    }

    @PostMapping("/{appId}/{version}")
    fun download(
        @PathVariable appId: String,
        @PathVariable version: String,
        @RequestParam("token") token: String,
        redirectAttributes: RedirectAttributes,
    ): Any {
        val clientId = try {
            applicationService.resolveClientId(appId)
        } catch (e: SentinelException) {
            redirectAttributes.addFlashAttribute("error", "유효하지 않은 애플리케이션입니다.")
            return "redirect:/download/$appId"
        }

        try {
            tokenAuthService.check(token, clientId)
        } catch (e: SentinelException) {
            redirectAttributes.addFlashAttribute("error", e.errorCode.message)
            return "redirect:/download/$appId"
        }

        val file = try {
            appFileService.findByAppAndVersion(clientId, version)
        } catch (e: SentinelException) {
            redirectAttributes.addFlashAttribute("error", e.errorCode.message)
            return "redirect:/download/$appId"
        }

        val path = try {
            appFileService.resolvePath(file)
        } catch (e: SentinelException) {
            redirectAttributes.addFlashAttribute("error", e.errorCode.message)
            return "redirect:/download/$appId"
        }

        val disposition = ContentDisposition.attachment()
            .filename(file.filename, StandardCharsets.UTF_8)
            .build()
            .toString()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .header("X-File-SHA256", file.sha256)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(file.sizeBytes)
            .body(FileSystemResource(path))
    }

    @PostMapping("/{appId}/feedback")
    fun submitFeedback(
        @PathVariable appId: String,
        @RequestParam("kind") kind: String,
        @RequestParam("message") message: String,
        @RequestParam("contact", required = false) contact: String?,
        request: HttpServletRequest,
        redirectAttributes: RedirectAttributes,
    ): String {
        val app = applicationService.findByAppId(appId)
            ?: return "redirect:/download/$appId"

        val ip = request.clientIp()
        if (!feedbackRateLimiter.tryAcquire(ip)) {
            redirectAttributes.addFlashAttribute("feedbackError", "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.")
            return "redirect:/download/$appId"
        }

        val parsedKind = runCatching { FeedbackKind.valueOf(kind.uppercase()) }.getOrNull()
        if (parsedKind == null) {
            redirectAttributes.addFlashAttribute("feedbackError", "올바른 종류를 선택해 주세요.")
            return "redirect:/download/$appId"
        }

        val trimmed = message.trim()
        if (trimmed.length < 5) {
            redirectAttributes.addFlashAttribute("feedbackError", "내용은 5자 이상 입력해주세요.")
            return "redirect:/download/$appId"
        }
        if (trimmed.length > 4000) {
            redirectAttributes.addFlashAttribute("feedbackError", "내용은 4000자 이내로 입력해주세요.")
            return "redirect:/download/$appId"
        }

        feedbackService.submit(
            appId = app.appId,
            kind = parsedKind,
            message = trimmed,
            contact = contact?.trim()?.takeIf { it.isNotBlank() },
            ip = ip,
        )
        redirectAttributes.addFlashAttribute("feedbackOk", true)
        return "redirect:/download/$appId"
    }
}
