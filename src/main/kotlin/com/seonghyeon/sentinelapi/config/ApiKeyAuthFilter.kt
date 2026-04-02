package com.seonghyeon.sentinelapi.config

import com.seonghyeon.sentinelapi.common.exception.ErrorCode
import com.seonghyeon.sentinelapi.service.ApiKeyService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiKeyAuthFilter(
    private val apiKeyService: ApiKeyService,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/v1/manager/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val keyStr = request.getHeader("X-Api-Key")
        if (keyStr.isNullOrBlank()) {
            writeError(response, ErrorCode.INVALID_API_KEY)
            return
        }

        runCatching { apiKeyService.validate(keyStr) }
            .onSuccess { apiKey ->
                val auth = UsernamePasswordAuthenticationToken(
                    apiKey.keyStr, null, listOf(SimpleGrantedAuthority("ROLE_API"))
                )
                SecurityContextHolder.getContext().authentication = auth
                filterChain.doFilter(request, response)
            }
            .onFailure { writeError(response, ErrorCode.INVALID_API_KEY) }
    }

    private fun writeError(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"code":"${errorCode.name}","message":"${errorCode.message}"}""")
    }
}
