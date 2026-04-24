package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.AbstractIntegrationTest
import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.App
import com.seonghyeon.sentinelapi.domain.DeviceRegistration
import com.seonghyeon.sentinelapi.domain.Token
import com.seonghyeon.sentinelapi.repository.AppRepository
import com.seonghyeon.sentinelapi.repository.DeviceRegistrationRepository
import com.seonghyeon.sentinelapi.repository.TokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class DeviceServiceTest : AbstractIntegrationTest() {

    @Autowired lateinit var deviceService: DeviceService
    @Autowired lateinit var deviceRegistrationRepository: DeviceRegistrationRepository
    @Autowired lateinit var appRepository: AppRepository
    @Autowired lateinit var tokenRepository: TokenRepository

    private fun givenApp(appId: String = "app_dev0001"): App =
        appRepository.save(App(id = 0, name = "TestApp", description = "", appId = appId))

    private fun givenToken(app: App, maxDeviceCount: Int = 1): Token =
        tokenRepository.save(Token(id = 0, application = app, tokenStr = "tok-${System.nanoTime()}", expireDate = LocalDate.now().plusDays(30), maxDeviceCount = maxDeviceCount))

    private fun givenDevice(token: Token, deviceId: String, lastSeenAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): DeviceRegistration =
        deviceRegistrationRepository.save(DeviceRegistration(id = 0, token = token, deviceId = deviceId, registeredAt = LocalDateTime.now(ZoneOffset.UTC), lastSeenAt = lastSeenAt))

    // ─── login ───────────────────────────────────────────────────────────────

    @Test
    fun `login - 신규 기기를 DeviceRegistration에 등록한다`() {
        val token = givenToken(givenApp())

        deviceService.login(token, "guid-new-001")

        val reg = deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-new-001")
        assertThat(reg).isNotNull
        assertThat(reg!!.deviceId).isEqualTo("guid-new-001")
    }

    @Test
    fun `login - 이미 등록된 기기면 lastSeenAt을 갱신한다`() {
        val token = givenToken(givenApp())
        val oldTime = LocalDateTime.now(ZoneOffset.UTC).minusHours(2)
        givenDevice(token, "guid-existing", lastSeenAt = oldTime)

        deviceService.login(token, "guid-existing")

        val updated = deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-existing")!!
        assertThat(updated.lastSeenAt).isAfter(oldTime)
    }

    @Test
    fun `login - 이미 등록된 기기면 DeviceRegistration 수가 늘지 않는다`() {
        val token = givenToken(givenApp())
        givenDevice(token, "guid-same")

        deviceService.login(token, "guid-same")

        assertThat(deviceRegistrationRepository.countByTokenId(token.id)).isEqualTo(1)
    }

    @Test
    fun `login - 한도에 도달한 상태에서 신규 기기면 DEVICE_LIMIT_EXCEEDED`() {
        val token = givenToken(givenApp(), maxDeviceCount = 1)
        givenDevice(token, "guid-slot-taken")

        val ex = assertThrows<SentinelException> { deviceService.login(token, "guid-new-over") }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.DEVICE_LIMIT_EXCEEDED)
    }

    @Test
    fun `login - 한도 초과 시 DeviceRegistration에 추가되지 않는다`() {
        val token = givenToken(givenApp(), maxDeviceCount = 1)
        givenDevice(token, "guid-existing")

        assertThrows<SentinelException> { deviceService.login(token, "guid-blocked") }

        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-blocked")).isNull()
    }

    @Test
    fun `login - maxDeviceCount가 2이면 두 번째 기기까지 등록된다`() {
        val token = givenToken(givenApp(), maxDeviceCount = 2)
        givenDevice(token, "guid-first")

        deviceService.login(token, "guid-second")

        assertThat(deviceRegistrationRepository.countByTokenId(token.id)).isEqualTo(2)
    }

    @Test
    fun `login - maxDeviceCount가 2인데 세 번째 기기면 DEVICE_LIMIT_EXCEEDED`() {
        val token = givenToken(givenApp(), maxDeviceCount = 2)
        givenDevice(token, "guid-a")
        givenDevice(token, "guid-b")

        val ex = assertThrows<SentinelException> { deviceService.login(token, "guid-c") }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.DEVICE_LIMIT_EXCEEDED)
    }

    @Test
    fun `login - maxDeviceCount가 0이면 기기 수 제한 없이 등록된다`() {
        val token = givenToken(givenApp(), maxDeviceCount = 0)
        repeat(5) { i -> givenDevice(token, "guid-unlimited-$i") }

        deviceService.login(token, "guid-unlimited-extra")

        assertThat(deviceRegistrationRepository.countByTokenId(token.id)).isEqualTo(6)
    }

    @Test
    fun `login - 다른 토큰의 기기 수는 한도 계산에 영향을 주지 않는다`() {
        val app = givenApp()
        val tokenA = givenToken(app, maxDeviceCount = 1)
        val tokenB = givenToken(app, maxDeviceCount = 1)
        givenDevice(tokenA, "guid-other-token")  // tokenA 슬롯 점유

        // tokenB는 독립적으로 신규 등록 가능
        deviceService.login(tokenB, "guid-for-tokenB")

        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(tokenB.id, "guid-for-tokenB")).isNotNull
    }

    // ─── check ───────────────────────────────────────────────────────────────

    @Test
    fun `check - 등록된 기기면 lastSeenAt을 갱신한다`() {
        val token = givenToken(givenApp())
        val oldTime = LocalDateTime.now(ZoneOffset.UTC).minusHours(1)
        givenDevice(token, "guid-check", lastSeenAt = oldTime)

        deviceService.check(token.id, "guid-check")

        val updated = deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-check")!!
        assertThat(updated.lastSeenAt).isAfter(oldTime)
    }

    @Test
    fun `check - 등록되지 않은 기기면 DEVICE_LOGGED_OUT`() {
        val token = givenToken(givenApp())

        val ex = assertThrows<SentinelException> { deviceService.check(token.id, "guid-not-registered") }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.DEVICE_LOGGED_OUT)
    }

    @Test
    fun `check - 제거된 기기면 DEVICE_LOGGED_OUT`() {
        val token = givenToken(givenApp())
        givenDevice(token, "guid-removed")
        deviceRegistrationRepository.deleteByTokenIdAndDeviceId(token.id, "guid-removed")

        val ex = assertThrows<SentinelException> { deviceService.check(token.id, "guid-removed") }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.DEVICE_LOGGED_OUT)
    }

    @Test
    fun `check - 다른 토큰에 등록된 기기면 DEVICE_LOGGED_OUT`() {
        val app = givenApp()
        val tokenA = givenToken(app, maxDeviceCount = 0)
        val tokenB = givenToken(app, maxDeviceCount = 0)
        givenDevice(tokenA, "guid-belongs-to-A")

        // tokenB로 tokenA의 기기를 check하면 안 된다
        val ex = assertThrows<SentinelException> { deviceService.check(tokenB.id, "guid-belongs-to-A") }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.DEVICE_LOGGED_OUT)
    }

    // ─── findAllByToken ───────────────────────────────────────────────────────

    @Test
    fun `findAllByToken - 해당 토큰의 기기 목록을 반환한다`() {
        val token = givenToken(givenApp(), maxDeviceCount = 0)
        givenDevice(token, "guid-list-1")
        givenDevice(token, "guid-list-2")

        val result = deviceService.findAllByToken(token.id)

        assertThat(result.map { it.deviceId }).containsExactlyInAnyOrder("guid-list-1", "guid-list-2")
    }

    @Test
    fun `findAllByToken - 다른 토큰의 기기는 포함되지 않는다`() {
        val app = givenApp()
        val tokenA = givenToken(app, maxDeviceCount = 0)
        val tokenB = givenToken(app, maxDeviceCount = 0)
        givenDevice(tokenA, "guid-only-A")
        givenDevice(tokenB, "guid-only-B")

        val result = deviceService.findAllByToken(tokenA.id)

        assertThat(result.map { it.deviceId }).containsOnly("guid-only-A")
    }

    @Test
    fun `findAllByToken - 등록된 기기가 없으면 빈 목록을 반환한다`() {
        val token = givenToken(givenApp())

        val result = deviceService.findAllByToken(token.id)

        assertThat(result).isEmpty()
    }

    // ─── remove ───────────────────────────────────────────────────────────────

    @Test
    fun `remove - 기기를 DeviceRegistration에서 제거한다`() {
        val token = givenToken(givenApp())
        givenDevice(token, "guid-to-remove")

        deviceService.remove(token.id, "guid-to-remove")

        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-to-remove")).isNull()
    }

    @Test
    fun `remove - 제거 후 check하면 DEVICE_LOGGED_OUT`() {
        val token = givenToken(givenApp())
        givenDevice(token, "guid-remove-check")
        deviceService.remove(token.id, "guid-remove-check")

        val ex = assertThrows<SentinelException> { deviceService.check(token.id, "guid-remove-check") }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.DEVICE_LOGGED_OUT)
    }

    @Test
    fun `remove - 제거 후 동일 기기로 재로그인하면 신규 등록된다`() {
        val token = givenToken(givenApp())
        givenDevice(token, "guid-rejoin")
        deviceService.remove(token.id, "guid-rejoin")

        deviceService.login(token, "guid-rejoin")

        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-rejoin")).isNotNull
    }

    @Test
    fun `remove - 다른 기기는 영향받지 않는다`() {
        val token = givenToken(givenApp(), maxDeviceCount = 0)
        givenDevice(token, "guid-stay")
        givenDevice(token, "guid-gone")

        deviceService.remove(token.id, "guid-gone")

        assertThat(deviceRegistrationRepository.findByTokenIdAndDeviceId(token.id, "guid-stay")).isNotNull
        assertThat(deviceRegistrationRepository.countByTokenId(token.id)).isEqualTo(1)
    }

    // ─── countByTokenIds ─────────────────────────────────────────────────────

    @Test
    fun `countByTokenIds - 각 토큰의 활성 기기 수를 Map으로 반환한다`() {
        val app = givenApp()
        val tokenA = givenToken(app, maxDeviceCount = 0)
        val tokenB = givenToken(app, maxDeviceCount = 0)
        givenDevice(tokenA, "guid-a1")
        givenDevice(tokenA, "guid-a2")
        givenDevice(tokenA, "guid-a3")
        givenDevice(tokenB, "guid-b1")

        val counts = deviceService.countByTokenIds(listOf(tokenA.id, tokenB.id))

        assertThat(counts).containsEntry(tokenA.id, 3L)
        assertThat(counts).containsEntry(tokenB.id, 1L)
    }

    @Test
    fun `countByTokenIds - 기기가 없는 토큰은 맵에 포함되지 않는다`() {
        val app = givenApp()
        val tokenWithDevice = givenToken(app)
        val tokenEmpty = givenToken(app)
        givenDevice(tokenWithDevice, "guid-only")

        val counts = deviceService.countByTokenIds(listOf(tokenWithDevice.id, tokenEmpty.id))

        assertThat(counts).containsEntry(tokenWithDevice.id, 1L)
        assertThat(counts).doesNotContainKey(tokenEmpty.id)
    }

    @Test
    fun `countByTokenIds - 빈 컬렉션이면 빈 맵을 반환한다`() {
        val counts = deviceService.countByTokenIds(emptyList())

        assertThat(counts).isEmpty()
    }

    @Test
    fun `countByTokenIds - 요청하지 않은 토큰의 기기 수는 집계되지 않는다`() {
        val app = givenApp()
        val tokenQueried = givenToken(app, maxDeviceCount = 0)
        val tokenIgnored = givenToken(app, maxDeviceCount = 0)
        givenDevice(tokenQueried, "guid-q")
        givenDevice(tokenIgnored, "guid-i")

        val counts = deviceService.countByTokenIds(listOf(tokenQueried.id))

        assertThat(counts).containsOnlyKeys(tokenQueried.id)
        assertThat(counts[tokenQueried.id]).isEqualTo(1L)
    }
}
