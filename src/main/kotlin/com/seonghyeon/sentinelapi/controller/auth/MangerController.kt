package com.seonghyeon.sentinelapi.controller.auth

import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.LoginHistoryRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import com.seonghyeon.sentinelapi.service.ApiKeyService
import com.seonghyeon.sentinelapi.service.ApplicationService
import com.seonghyeon.sentinelapi.service.ManagerService
import com.seonghyeon.sentinelapi.service.TokenAuthService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

@Controller
class MangerController(
    private val tokenAuthService: TokenAuthService,
    private val managerService: ManagerService,
    private val applicationService: ApplicationService,
    private val apiKeyService: ApiKeyService,
    private val appRepository: AppRepository,
    private val tokenRepository: TokenRepository,
    private val loginHistoryRepository: LoginHistoryRepository,
) {

    @GetMapping("/login")
    fun login(): String = "login"

    @GetMapping("/dashboard")
    fun dashboard(): String = "redirect:/dashboard/register"

    // --- 등록 ---

    @GetMapping("/dashboard/register")
    fun registerPage(model: Model): String {
        model.addAttribute("apps", appRepository.findAll())
        model.addAttribute("apiKeys", apiKeyService.findAll())
        return "dashboard/register"
    }

    @PostMapping("/dashboard/register/token")
    fun registerToken(
        @RequestParam appId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) expireDate: LocalDate,
    ): String {
        tokenAuthService.generate(appId, expireDate)
        return "redirect:/dashboard/users?success=token"
    }

    @PostMapping("/dashboard/register/app")
    fun registerApp(
        @RequestParam name: String,
        @RequestParam(required = false, defaultValue = "") description: String,
    ): String {
        applicationService.register(name, description)
        return "redirect:/dashboard/register?success=app"
    }

    @PostMapping("/dashboard/register/manager")
    fun registerManager(
        @RequestParam username: String,
        @RequestParam password: String,
    ): String {
        managerService.register(username, password)
        return "redirect:/dashboard/register?success=manager"
    }

    // --- 사용자 관리 ---

    @GetMapping("/dashboard/users")
    fun usersPage(model: Model): String {
        model.addAttribute("tokens", tokenRepository.findAll())
        return "dashboard/users"
    }

    @PostMapping("/dashboard/users/{id}/expire")
    fun updateExpireDate(
        @PathVariable id: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) expireDate: LocalDate,
    ): String {
        val token = tokenRepository.findById(id).orElseThrow()
        token.expireDate = expireDate
        tokenRepository.save(token)
        return "redirect:/dashboard/users"
    }

    @PostMapping("/dashboard/users/{id}/delete")
    fun deleteToken(@PathVariable id: Long): String {
        tokenRepository.deleteById(id)
        return "redirect:/dashboard/users"
    }

    // --- API Key 관리 ---

    @PostMapping("/dashboard/register/apikey")
    fun generateApiKey(@RequestParam description: String): String {
        val apiKey = apiKeyService.generate(description)
        return "redirect:/dashboard/register?success=apikey&key=${apiKey.keyStr}"
    }

    @PostMapping("/dashboard/apikeys/{id}/delete")
    fun deleteApiKey(@PathVariable id: Long): String {
        apiKeyService.delete(id)
        return "redirect:/dashboard/register"
    }

    // --- 애플리케이션 관리 ---

    @GetMapping("/dashboard/apps")
    fun appsPage(model: Model): String {
        model.addAttribute("apps", appRepository.findAll())
        return "dashboard/apps"
    }

    @PostMapping("/dashboard/apps/{id}/delete")
    fun deleteApp(@PathVariable id: Long): String {
        appRepository.deleteById(id)
        return "redirect:/dashboard/apps"
    }

    // --- 히스토리 ---

    @GetMapping("/dashboard/history")
    fun historyPage(model: Model): String {
        val histories = loginHistoryRepository.findAll()
        val appNames = appRepository.findAll().associate { it.appId to it.name }

        // 서비스별 접근 횟수
        val appAccessCounts = histories
            .groupBy { appNames[it.appId] ?: it.appId }
            .mapValues { it.value.size }
            .toSortedMap()

        // 날짜별 사용자 수
        val dailyCounts = histories
            .groupBy { it.createdAt.toLocalDate().toString() }
            .mapValues { it.value.size }
            .toSortedMap()

        model.addAttribute("histories", histories)
        model.addAttribute("appNames", appNames)
        model.addAttribute("appAccessCounts", appAccessCounts)
        model.addAttribute("dailyCounts", dailyCounts)
        return "dashboard/history"
    }
}
