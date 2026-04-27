package com.seonghyeon.sentinelapi.config

import com.seonghyeon.sentinelapi.utils.clientIp
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcContextFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val incoming = request.getHeader("X-Request-Id")
        val requestId = if (!incoming.isNullOrBlank()) incoming else UUID.randomUUID().toString().take(12)

        MDC.put("requestId", requestId)
        MDC.put("clientIp", request.clientIp())
        MDC.put("method", request.method)
        MDC.put("path", request.requestURI)

        response.setHeader("X-Request-Id", requestId)
        try {
            filterChain.doFilter(request, response)
            MDC.put("status", response.status.toString())
        } finally {
            MDC.clear()
        }
    }
}
