package com.seonghyeon.sentinelapi.utils

import jakarta.servlet.http.HttpServletRequest

fun HttpServletRequest.clientIp(): String {
    // 1. 보안/프록시 관련 헤더들을 순서대로 확인
    val headerNames = listOf(
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_CLIENT_IP",
        "HTTP_X_FORWARDED_FOR"
    )

    for (header in headerNames) {
        val ip = getHeader(header)
        if (!ip.isNullOrBlank() && !"unknown".equals(ip, ignoreCase = true)) {
            // X-Forwarded-For의 경우 "사용자IP, 프록시1, 프록시2" 형태이므로 첫 번째 IP만 추출
            return if (ip.contains(",")) ip.split(",")[0].trim() else ip
        }
    }

    // 2. 헤더에 정보가 없으면 기본 remoteAddr 반환 (이때 10.42.0.1 등이 찍힐 수 있음)
    return remoteAddr
}
