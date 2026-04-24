package com.seonghyeon.sentinelapi.config

import com.seonghyeon.sentinelapi.service.ManagerService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        managerService: ManagerService,
        apiKeyAuthFilter: ApiKeyAuthFilter,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authenticationProvider(authenticationProvider(managerService, passwordEncoder()))
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/manager/**",
                    "/actuator/**",
                    "/login",
                    "/download/**",
                    "/css/**",
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .formLogin {
                it.loginPage("/login")
                it.loginProcessingUrl("/login")
                it.defaultSuccessUrl("/dashboard", true)
                it.failureUrl("/login?error")
            }
            .logout {
                it.logoutUrl("/logout")
                it.logoutSuccessUrl("/login")
            }
        return http.build()
    }

    @Bean
    fun authenticationProvider(managerService: ManagerService, passwordEncoder: PasswordEncoder): DaoAuthenticationProvider =
        DaoAuthenticationProvider(managerService).also {
            it.setPasswordEncoder(passwordEncoder)
        }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
