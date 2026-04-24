package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.repository.AppFileRepository
import com.seonghyeon.sentinelapi.repository.AppRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class AppFileServiceTest : AbstractIntegrationTest() {

    @Autowired lateinit var appFileService: AppFileService
    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var appFileRepository: AppFileRepository

    companion object {
        private val tempStorageRoot: Path = Files.createTempDirectory("sentinel-appfile-test-")

        @JvmStatic
        @DynamicPropertySource
        fun overrideStorageRoot(registry: DynamicPropertyRegistry) {
            registry.add("sentinel.storage.root") { tempStorageRoot.toString() }
        }
    }

    private fun givenApp(appId: String = "app_file_t1"): App =
        appRepository.save(App(id = 0, name = "TestApp", description = "", appId = appId))

    private fun multipart(name: String, content: ByteArray) =
        MockMultipartFile("file", name, "application/octet-stream", content)

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // --- upload ---

    @Test
    fun `upload - 파일을 저장하고 메타를 DB에 기록한다`() {
        val app = givenApp()
        val bytes = "hello binary".toByteArray()

        val saved = appFileService.upload(app.id, "1.0.0", "initial", multipart("my.exe", bytes))

        assertThat(saved.filename).isEqualTo("my.exe")
        assertThat(saved.sizeBytes).isEqualTo(bytes.size.toLong())
        assertThat(saved.sha256).isEqualTo(sha256Hex(bytes))
        assertThat(saved.changelog).isEqualTo("initial")
        assertThat(saved.isLatest).isTrue()
        assertThat(Files.exists(Path.of(tempStorageRoot.toString(), saved.storagePath))).isTrue()
    }

    @Test
    fun `upload - 두 번째 업로드 시 이전 파일의 latest가 해제된다`() {
        val app = givenApp()
        val first = appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "first".toByteArray()))
        val second = appFileService.upload(app.id, "1.1.0", null, multipart("b.exe", "second".toByteArray()))

        assertThat(second.isLatest).isTrue()
        assertThat(appFileRepository.findById(first.id).orElseThrow().isLatest).isFalse()
    }

    @Test
    fun `upload - 같은 버전을 두 번 올리면 DUPLICATE_VERSION`() {
        val app = givenApp()
        appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))

        val ex = assertThrows<SentinelException> {
            appFileService.upload(app.id, "1.0.0", null, multipart("b.exe", "y".toByteArray()))
        }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.DUPLICATE_VERSION)
    }

    @Test
    fun `upload - 존재하지 않는 앱이면 INVALID_APPLICATION`() {
        val ex = assertThrows<SentinelException> {
            appFileService.upload(999_999L, "1.0.0", null, multipart("a.exe", "x".toByteArray()))
        }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.INVALID_APPLICATION)
    }

    @Test
    fun `upload - 버전이 공백이면 IllegalArgumentException`() {
        val app = givenApp()
        assertThrows<IllegalArgumentException> {
            appFileService.upload(app.id, "  ", null, multipart("a.exe", "x".toByteArray()))
        }
    }

    @Test
    fun `upload - 파일명에 경로 구분자가 있어도 basename만 사용한다`() {
        val app = givenApp()
        val saved = appFileService.upload(app.id, "1.0.0", null, multipart("../../etc/passwd", "x".toByteArray()))
        assertThat(saved.filename).isEqualTo("passwd")
    }

    // --- findLatest / findLatestOrNull ---

    @Test
    fun `findLatest - 파일이 없으면 NO_FILE_AVAILABLE`() {
        val app = givenApp()
        val ex = assertThrows<SentinelException> { appFileService.findLatest(app.id) }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.NO_FILE_AVAILABLE)
    }

    @Test
    fun `findLatestOrNull - 파일이 없으면 null`() {
        val app = givenApp()
        assertThat(appFileService.findLatestOrNull(app.id)).isNull()
    }

    @Test
    fun `findLatestOrNull - 최신 파일을 반환한다`() {
        val app = givenApp()
        appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))
        appFileService.upload(app.id, "2.0.0", null, multipart("b.exe", "y".toByteArray()))

        val latest = appFileService.findLatestOrNull(app.id)
        assertThat(latest?.version).isEqualTo("2.0.0")
    }

    // --- findByAppAndVersion ---

    @Test
    fun `findByAppAndVersion - 버전으로 조회한다`() {
        val app = givenApp()
        appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))

        val f = appFileService.findByAppAndVersion(app.id, "1.0.0")
        assertThat(f.version).isEqualTo("1.0.0")
    }

    @Test
    fun `findByAppAndVersion - 없는 버전이면 INVALID_VERSION`() {
        val app = givenApp()
        val ex = assertThrows<SentinelException> { appFileService.findByAppAndVersion(app.id, "9.9.9") }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.INVALID_VERSION)
    }

    // --- markLatest ---

    @Test
    fun `markLatest - 지정한 파일이 latest가 되고 이전 latest는 해제된다`() {
        val app = givenApp()
        val v1 = appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))
        val v2 = appFileService.upload(app.id, "2.0.0", null, multipart("b.exe", "y".toByteArray()))

        appFileService.markLatest(app.id, v1.id)

        assertThat(appFileRepository.findById(v1.id).orElseThrow().isLatest).isTrue()
        assertThat(appFileRepository.findById(v2.id).orElseThrow().isLatest).isFalse()
    }

    @Test
    fun `markLatest - 다른 앱의 파일 ID면 INVALID_VERSION`() {
        val app1 = givenApp("app_file_t1")
        val app2 = givenApp("app_file_t2")
        val f1 = appFileService.upload(app1.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))

        val ex = assertThrows<SentinelException> { appFileService.markLatest(app2.id, f1.id) }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.INVALID_VERSION)
    }

    // --- delete ---

    @Test
    fun `delete - DB와 디스크 파일을 모두 제거한다`() {
        val app = givenApp()
        val f = appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))
        val absolute = Path.of(tempStorageRoot.toString(), f.storagePath)
        assertThat(Files.exists(absolute)).isTrue()

        appFileService.delete(app.id, f.id)

        assertThat(appFileRepository.findById(f.id)).isEmpty
        assertThat(Files.exists(absolute)).isFalse()
    }

    @Test
    fun `delete - 다른 앱의 파일 ID면 INVALID_VERSION`() {
        val app1 = givenApp("app_file_t1")
        val app2 = givenApp("app_file_t2")
        val f = appFileService.upload(app1.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))

        val ex = assertThrows<SentinelException> { appFileService.delete(app2.id, f.id) }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.INVALID_VERSION)
    }

    // --- deleteAllByApp ---

    @Test
    fun `deleteAllByApp - 앱의 모든 파일을 제거한다`() {
        val app = givenApp()
        val a = appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))
        val b = appFileService.upload(app.id, "2.0.0", null, multipart("b.exe", "y".toByteArray()))

        appFileService.deleteAllByApp(app.id)

        assertThat(appFileRepository.findById(a.id)).isEmpty
        assertThat(appFileRepository.findById(b.id)).isEmpty
        assertThat(Files.exists(Path.of(tempStorageRoot.toString(), a.storagePath))).isFalse()
        assertThat(Files.exists(Path.of(tempStorageRoot.toString(), b.storagePath))).isFalse()
    }

    @Test
    fun `deleteAllByApp - 파일이 없어도 예외 없이 동작`() {
        val app = givenApp()
        appFileService.deleteAllByApp(app.id)
        // no exception
    }

    // --- findLatestVersions ---

    @Test
    fun `findLatestVersions - 최신 순으로 limit개 반환`() {
        val app = givenApp()
        appFileService.upload(app.id, "1.0.0", null, multipart("a.exe", "x".toByteArray()))
        appFileService.upload(app.id, "2.0.0", null, multipart("b.exe", "y".toByteArray()))
        appFileService.upload(app.id, "3.0.0", null, multipart("c.exe", "z".toByteArray()))

        val top2 = appFileService.findLatestVersions(app.id, 2)

        assertThat(top2).hasSize(2)
        assertThat(top2.map { it.version }).containsExactly("3.0.0", "2.0.0")
    }
}
