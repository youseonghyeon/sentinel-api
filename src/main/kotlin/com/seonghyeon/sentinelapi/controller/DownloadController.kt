package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.service.AppFileService
import com.seonghyeon.sentinelapi.service.ApplicationService
import com.seonghyeon.sentinelapi.service.TokenAuthService
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
) {

    @GetMapping("/{appId}")
    fun page(@PathVariable appId: String, model: Model): String {
        val clientId = try {
            applicationService.resolveClientId(appId)
        } catch (e: SentinelException) {
            model.addAttribute("notFound", true)
            model.addAttribute("appIdInput", appId)
            return "download"
        }
        val app = applicationService.findAll().first { it.id == clientId }
        model.addAttribute("app", app)
        model.addAttribute("files", appFileService.findLatestVersions(clientId, 2))
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

        val path = appFileService.resolvePath(file)
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
}
