package com.seonghyeon.sentinelapi.service

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.common.exception.SentinelException
import com.seonghyeon.sentinelapi.domain.Manager
import com.seonghyeon.sentinelapi.repository.ManagerRepository
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class ManagerService(
    private val managerRepository: ManagerRepository,
    private val passwordEncoder: PasswordEncoder,
) : UserDetailsService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadUserByUsername(username: String): UserDetails {
        val manager = managerRepository.findByUsername(username)
            ?: throw SentinelException(ErrorCode.MANAGER_NOT_FOUND)
        return User(manager.username, manager.password, listOf(SimpleGrantedAuthority("ROLE_MANAGER")))
    }

    fun register(username: String, password: String): Manager {
        log.info("Register manager: username={}", username)
        val manager = managerRepository.save(
            Manager(
                id = 0,
                username = username,
                password = passwordEncoder.encode(password)!!,
                createdAt = LocalDateTime.now(ZoneOffset.UTC),
            )
        )
        log.info("Manager registered: id={}", manager.id)
        return manager
    }

    fun findAll(): List<Manager> = managerRepository.findAll()
}
