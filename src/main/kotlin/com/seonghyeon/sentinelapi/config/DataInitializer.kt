package com.seonghyeon.sentinelapi.config

import com.seonghyeon.sentinelapi.repository.ManagerRepository
import com.seonghyeon.sentinelapi.service.ManagerService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DataInitializer(
    private val managerRepository: ManagerRepository,
    private val managerService: ManagerService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (managerRepository.findByUsername("admin") != null) return

        val password = UUID.randomUUID().toString().substring(0, 12)
        managerService.register("admin", password)
        log.info("=================================================")
        log.info("  Initial admin account created")
        log.info("  ID       : admin")
        log.info("  Password : {}", password)
        log.info("=================================================")
    }
}
