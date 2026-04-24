package com.seonghyeon.sentinelapi.controller.auth

import com.seonghyeon.sentinelapi.controller.auth.dto.LoginHistoryView
import com.seonghyeon.sentinelapi.service.ApiKeyService
import com.seonghyeon.sentinelapi.service.AppFileService
import com.seonghyeon.sentinelapi.service.ApplicationService
import com.seonghyeon.sentinelapi.service.DeviceService
import com.seonghyeon.sentinelapi.service.LoginHistoryService
import com.seonghyeon.sentinelapi.service.ManagerService
import com.seonghyeon.sentinelapi.service.TokenAuthService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDate
import java.time.ZoneId

@Controller
class MangerController(
    private val tokenAuthService: TokenAuthService,
    private val managerService: ManagerService,
    private val applicationService: ApplicationService,
    private val apiKeyService: ApiKeyService,
    private val loginHistoryService: LoginHistoryService,
    private val deviceService: DeviceService,
    private val appFileService: AppFileService,
) {

    @GetMapping("/login")
    fun login(): String = "login"

    @GetMapping("/dashboard")
    fun dashboard(): String = "redirect:/dashboard/apps"

    // --- 등록 ---

    @GetMapping("/dashboard/apikeys")
    fun apiKeysPage(model: Model): String {
        model.addAttribute("apiKeys", apiKeyService.findAll())
        return "dashboard/apikeys"
    }

    @PostMapping("/dashboard/register/token")
    fun registerToken(
        @RequestParam appId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) expireDate: LocalDate,
        @RequestParam(defaultValue = "1") maxDeviceCount: Int,
        redirectAttributes: RedirectAttributes,
    ): String {
        val token = tokenAuthService.generate(appId, expireDate, maxDeviceCount)
        redirectAttributes.addFlashAttribute("newToken", token.tokenStr)
        redirectAttributes.addFlashAttribute("newAppName", token.application.name)
        redirectAttributes.addFlashAttribute("newExpireDate", token.expireDate)
        return "redirect:/dashboard/users"
    }

    @PostMapping("/dashboard/register/app")
    fun registerApp(
        @RequestParam name: String,
        @RequestParam(required = false, defaultValue = "") description: String,
    ): String {
        applicationService.register(name, description)
        return "redirect:/dashboard/apps?success=app"
    }

    @PostMapping("/dashboard/register/manager")
    fun registerManager(
        @RequestParam username: String,
        @RequestParam password: String,
    ): String {
        managerService.register(username, password)
        return "redirect:/dashboard/apikeys?success=manager"
    }

    // --- 사용자 관리 ---

    @GetMapping("/dashboard/users")
    fun usersPage(
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) tokenStr: String?,
        @RequestParam(defaultValue = "0") page: Int,
        model: Model,
    ): String {
        val pageable = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id"))
        val tokenPage = tokenAuthService.findPage(appName, tokenStr, pageable)
        model.addAttribute("tokens", tokenPage.content)
        model.addAttribute("apps", applicationService.findAll())
        model.addAttribute("appName", appName.orEmpty())
        model.addAttribute("tokenStr", tokenStr.orEmpty())
        model.addAttribute("currentPage", tokenPage.number)
        model.addAttribute("totalPages", tokenPage.totalPages)
        return "dashboard/users"
    }

    @PostMapping("/dashboard/users/{id}/update")
    fun updateToken(
        @PathVariable id: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) expireDate: LocalDate,
        @RequestParam maxDeviceCount: Int,
    ): String {
        tokenAuthService.update(id, expireDate, maxDeviceCount)
        return "redirect:/dashboard/users"
    }

    @GetMapping("/dashboard/users/{id}/devices")
    fun devicesPage(@PathVariable id: Long, model: Model): String {
        val kst = ZoneId.of("Asia/Seoul")
        val utc = ZoneId.of("UTC")
        val devices = deviceService.findAllByToken(id).map { d ->
            mapOf(
                "deviceId" to d.deviceId,
                "registeredAtKst" to d.registeredAt.atZone(utc).withZoneSameInstant(kst).toLocalDateTime(),
                "lastSeenAtKst" to d.lastSeenAt.atZone(utc).withZoneSameInstant(kst).toLocalDateTime(),
            )
        }
        model.addAttribute("tokenId", id)
        model.addAttribute("devices", devices)
        return "dashboard/devices"
    }

    @PostMapping("/dashboard/users/{id}/devices/{deviceId}/delete")
    fun deleteDevice(@PathVariable id: Long, @PathVariable deviceId: String): String {
        deviceService.remove(id, deviceId)
        return "redirect:/dashboard/users/$id/devices"
    }

    @PostMapping("/dashboard/users/{id}/delete")
    fun deleteToken(@PathVariable id: Long): String {
        tokenAuthService.delete(id)
        return "redirect:/dashboard/users"
    }

    // --- API Key 관리 ---

    @PostMapping("/dashboard/register/apikey")
    fun generateApiKey(@RequestParam description: String): String {
        val apiKey = apiKeyService.generate(description)
        return "redirect:/dashboard/apikeys?success=apikey&key=${apiKey.keyStr}"
    }

    @PostMapping("/dashboard/apikeys/{id}/delete")
    fun deleteApiKey(@PathVariable id: Long): String {
        apiKeyService.delete(id)
        return "redirect:/dashboard/apikeys"
    }

    // --- 애플리케이션 관리 ---

    @GetMapping("/dashboard/apps")
    fun appsPage(model: Model): String {
        val apps = applicationService.findAll()
        val latestVersions = apps.associate { it.id to appFileService.findLatestOrNull(it.id)?.version }
        model.addAttribute("apps", apps)
        model.addAttribute("latestVersions", latestVersions)
        return "dashboard/apps"
    }

    @PostMapping("/dashboard/apps/{id}/delete")
    fun deleteApp(@PathVariable id: Long): String {
        applicationService.delete(id)
        return "redirect:/dashboard/apps"
    }

    // --- 애플리케이션 파일 ---

    @GetMapping("/dashboard/apps/{appId}/files")
    fun appFilesPage(@PathVariable appId: Long, model: Model): String {
        val app = applicationService.findAll().firstOrNull { it.id == appId }
            ?: return "redirect:/dashboard/apps"
        model.addAttribute("app", app)
        model.addAttribute("files", appFileService.findAllByApp(appId))
        return "dashboard/files"
    }

    @PostMapping("/dashboard/apps/{appId}/files")
    fun uploadAppFile(
        @PathVariable appId: Long,
        @RequestParam version: String,
        @RequestParam(required = false) changelog: String?,
        @RequestParam("file") file: MultipartFile,
        redirectAttributes: RedirectAttributes,
    ): String {
        if (file.isEmpty) {
            redirectAttributes.addFlashAttribute("error", "파일이 비어있습니다.")
            return "redirect:/dashboard/apps/$appId/files"
        }
        appFileService.upload(appId, version.trim(), changelog, file)
        return "redirect:/dashboard/apps/$appId/files?success=upload"
    }

    @PostMapping("/dashboard/apps/{appId}/files/{fileId}/latest")
    fun markLatest(@PathVariable appId: Long, @PathVariable fileId: Long): String {
        appFileService.markLatest(appId, fileId)
        return "redirect:/dashboard/apps/$appId/files?success=latest"
    }

    @PostMapping("/dashboard/apps/{appId}/files/{fileId}/delete")
    fun deleteAppFile(@PathVariable appId: Long, @PathVariable fileId: Long): String {
        appFileService.delete(appId, fileId)
        return "redirect:/dashboard/apps/$appId/files?success=delete"
    }

    // --- 히스토리 ---

    @GetMapping("/dashboard/history")
    fun historyPage(
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) tokenStr: String?,
        @RequestParam(defaultValue = "0") page: Int,
        model: Model,
    ): String {
        val kst = ZoneId.of("Asia/Seoul")
        val utc = ZoneId.of("UTC")
        val pageable = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        val historyPage = loginHistoryService.search(appName, tokenStr, pageable)

        val appNames = applicationService.findAllAsNameMap()

        val historyViews = historyPage.map { h ->
            LoginHistoryView(
                id = h.id,
                token = h.token,
                appId = h.appId,
                ip = h.ip,
                createdAtKst = h.createdAt.atZone(utc).withZoneSameInstant(kst).toLocalDateTime(),
            )
        }

        // 차트용 전체 데이터 (검색 필터 미적용)
        val allHistories = loginHistoryService.findAll()
        val appAccessCounts = allHistories
            .groupBy { appNames[it.appId] ?: it.appId }
            .mapValues { it.value.size }
            .toSortedMap()
        val dailyCounts = allHistories
            .groupBy { it.createdAt.atZone(utc).withZoneSameInstant(kst).toLocalDate().toString() }
            .mapValues { it.value.size }
            .toSortedMap()

        model.addAttribute("histories", historyViews.content)
        model.addAttribute("appNames", appNames)
        model.addAttribute("appAccessCounts", appAccessCounts)
        model.addAttribute("dailyCounts", dailyCounts)
        model.addAttribute("appName", appName.orEmpty())
        model.addAttribute("tokenStr", tokenStr.orEmpty())
        model.addAttribute("currentPage", historyViews.number)
        model.addAttribute("totalPages", historyViews.totalPages)
        return "dashboard/history"
    }
}
