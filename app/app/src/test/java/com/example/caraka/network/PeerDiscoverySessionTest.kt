package com.example.caraka.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerDiscoverySessionTest {

    @Test
    fun beginCreatesDeadlineAndIncrementsAttempt() {
        val session = PeerDiscoverySession(attemptCount = 2).begin(
            nowMillis = 10_000L,
            durationMillis = 15_000L
        )

        assertTrue(session.active)
        assertEquals(10_000L, session.startedAtMillis)
        assertEquals(25_000L, session.deadlineMillis)
        assertEquals(3, session.attemptCount)
    }

    @Test
    fun finishKeepsSessionMetadataForUi() {
        val session = PeerDiscoverySession(
            active = true,
            startedAtMillis = 10_000L,
            deadlineMillis = 25_000L,
            attemptCount = 3
        ).finish()

        assertFalse(session.active)
        assertEquals(10_000L, session.startedAtMillis)
        assertEquals(25_000L, session.deadlineMillis)
        assertEquals(3, session.attemptCount)
    }
}
