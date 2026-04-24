package com.seonghyeon.sentinelapi.controller

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import com.seonghyeon.sentinelapi.service.AppFileService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class DownloadControllerTest : AbstractIntegrationTest() {

    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var tokenRepository: TokenRepository
    @Autowired lateinit var appFileService: AppFileService

    companion object {
        private val tempStorageRoot: Path = Files.createTempDirectory("sentinel-download-test-")

        @JvmStatic
        @DynamicPropertySource
        fun overrideStorageRoot(registry: DynamicPropertyRegistry) {
            registry.add("sentinel.storage.root") { tempStorageRoot.toString() }
        }
    }

    private fun givenApp(appId: String = "app_dl_t1"): App =
        appRepository.save(App(id = 0, name = "TestApp", description = "desc", appId = appId))

    private fun givenToken(
        app: App,
        tokenStr: String = "dl_token",
        expireDate: LocalDate = LocalDate.now().plusDays(30),
    ): Token = tokenRepository.save(
        Token(id = 0, application = app, tokenStr = tokenStr, expireDate = expireDate, maxDeviceCount = 0)
    )

    private fun multipart(name: String, content: ByteArray) =
        MockMultipartFile("file", name, "application/octet-stream", content)

    // --- GET /download/{appId} ---

    @Test
    fun `page - 존재하는 앱이면 파일 목록을 모델에 담아 반환한다`() {
        val app = givenApp()
        appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))
        appFileService.upload(app.id, "2.0.0", null, multipart("b.exe", "y".toByteArray()))
        appFileService.upload(app.id, "3.0.0", null, multipart("c.exe", "z".toByteArray()))

        mockMvc.perform(get("/download/{appId}", app.appId))
            .andExpect(status().isOk)
            .andExpect(view().name("download"))
            .andExpect(model().attributeExists("app"))
            .andExpect(model().attributeExists("files"))
            .andExpect(model().attribute("notFound", org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `page - 존재하지 않는 앱이면 notFound 모델 플래그와 함께 반환한다`() {
        mockMvc.perform(get("/download/{appId}", "app_unknown_9999"))
            .andExpect(status().isOk)
            .andExpect(view().name("download"))
            .andExpect(model().attribute("notFound", true))
            .andExpect(model().attribute("appIdInput", "app_unknown_9999"))
    }

    // --- POST /download/{appId}/{version} ---

    @Test
    fun `download - 유효 토큰이면 파일 바이트를 스트리밍한다`() {
        val app = givenApp()
        val token = givenToken(app)
        val bytes = "hello binary download".toByteArray()
        appFileService.upload(app.id, "1.0.0", null, multipart("app.exe", bytes))

        mockMvc.perform(
            post("/download/{appId}/{version}", app.appId, "1.0.0")
                .param("token", token.tokenStr)
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("app.exe")))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, bytes.size.toLong()))
            .andExpect(header().exists("X-File-SHA256"))
            .andExpect(content().bytes(bytes))
    }

    @Test
    fun `download - 존재하지 않는 앱이면 redirect with error`() {
        mockMvc.perform(
            post("/download/{appId}/{version}", "app_unknown_x", "1.0.0")
                .param("token", "any")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/download/app_unknown_x"))
            .andExpect(flash().attributeExists("error"))
    }

    @Test
    fun `download - 잘못된 토큰이면 redirect with error`() {
        val app = givenApp()
        appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))

        mockMvc.perform(
            post("/download/{appId}/{version}", app.appId, "1.0.0")
                .param("token", "wrong_token")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(flash().attributeExists("error"))
    }

    @Test
    fun `download - 만료된 토큰이면 redirect with error`() {
        val app = givenApp()
        val token = givenToken(app, expireDate = LocalDate.now().minusDays(1))
        appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))

        mockMvc.perform(
            post("/download/{appId}/{version}", app.appId, "1.0.0")
                .param("token", token.tokenStr)
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(flash().attributeExists("error"))
    }

    @Test
    fun `download - 없는 버전이면 redirect with error`() {
        val app = givenApp()
        val token = givenToken(app)

        mockMvc.perform(
            post("/download/{appId}/{version}", app.appId, "9.9.9")
                .param("token", token.tokenStr)
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(flash().attributeExists("error"))
    }

    @Test
    fun `download - 디스크 파일이 없으면 redirect with error`() {
        val app = givenApp()
        val token = givenToken(app)
        val f = appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))
        Files.deleteIfExists(Path.of(tempStorageRoot.toString(), f.storagePath))

        mockMvc.perform(
            post("/download/{appId}/{version}", app.appId, "1.0.0")
                .param("token", token.tokenStr)
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(flash().attributeExists("error"))
    }
}
