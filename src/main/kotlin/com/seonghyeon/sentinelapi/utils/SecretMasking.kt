package com.seonghyeon.sentinelapi.utils

fun String?.masked(visiblePrefix: Int = 4, visibleSuffix: Int = 4): String {
    if (this.isNullOrEmpty()) return "<empty>"
    if (this.length <= visiblePrefix + visibleSuffix) return "*".repeat(this.length)
    return take(visiblePrefix) + "***(" + (length - visiblePrefix - visibleSuffix) + ")***" + takeLast(visibleSuffix)
}
