package com.example.caraka.network

data class PeerDiscoverySession(
    val active: Boolean = false,
    val startedAtMillis: Long? = null,
    val deadlineMillis: Long? = null,
    val attemptCount: Int = 0
)

fun PeerDiscoverySession.begin(nowMillis: Long, durationMillis: Long): PeerDiscoverySession {
    return PeerDiscoverySession(
        active = true,
        startedAtMillis = nowMillis,
        deadlineMillis = nowMillis + durationMillis,
        attemptCount = attemptCount + 1
    )
}

fun PeerDiscoverySession.finish(): PeerDiscoverySession = copy(active = false)
