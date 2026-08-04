package com.example.caraka.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrIdentityManagerTest {

    @Test
    fun parseQrPayloadAcceptsValidIdentityJson() {
        val raw = QrIdentityManager.buildPayload(
            peerId = "peer-123",
            name = "Relawan Satu",
            role = "PMI",
            encPub = "enc-public-key",
            signPub = "sign-public-key"
        )

        val parsed = QrIdentityManager.parseQrPayload(raw)

        assertEquals(1, parsed?.v)
        assertEquals("peer-123", parsed?.peerId)
        assertEquals("Relawan Satu", parsed?.name)
        assertEquals("PMI", parsed?.role)
        assertEquals("enc-public-key", parsed?.encPub)
        assertEquals("sign-public-key", parsed?.signPub)
    }

    @Test
    fun parseQrPayloadRejectsEmptyString() {
        assertTrue(QrIdentityManager.parseQrPayload("") == null)
    }

    @Test
    fun parseQrPayloadRejectsMalformedJson() {
        assertTrue(QrIdentityManager.parseQrPayload("{not valid json") == null)
    }

    @Test
    fun parseQrPayloadRejectsEmptyPeerId() {
        val raw = QrIdentityManager.buildPayload(
            peerId = "",
            name = "Relawan Satu",
            role = "PMI",
            encPub = "enc-public-key",
            signPub = "sign-public-key"
        )

        assertTrue(QrIdentityManager.parseQrPayload(raw) == null)
    }

    @Test
    fun parseQrPayloadRejectsEmptySignPublicKey() {
        val raw = QrIdentityManager.buildPayload(
            peerId = "peer-123",
            name = "Relawan Satu",
            role = "PMI",
            encPub = "enc-public-key",
            signPub = ""
        )

        assertTrue(QrIdentityManager.parseQrPayload(raw) == null)
    }
}
