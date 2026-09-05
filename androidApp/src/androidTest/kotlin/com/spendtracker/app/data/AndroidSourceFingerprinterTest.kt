package com.spendtracker.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendtracker.core.model.SourceMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSourceFingerprinterTest {
    private val fingerprinter = AndroidSourceFingerprinter()

    @After
    fun tearDown() = fingerprinter.deleteKey()

    @Test
    fun fingerprintIsStableAndSeparatesEveryInputPart() {
        val base = SourceMessage("1", "BANK", "INR 10 debited", 100)
        assertEquals(fingerprinter.fingerprint(base), fingerprinter.fingerprint(base))
        assertNotEquals(fingerprinter.fingerprint(base), fingerprinter.fingerprint(base.copy(sender = "CARD")))
        assertNotEquals(fingerprinter.fingerprint(base), fingerprinter.fingerprint(base.copy(body = "INR 20 debited")))
        assertNotEquals(fingerprinter.fingerprint(base), fingerprinter.fingerprint(base.copy(receivedAtEpochMillis = 101)))
    }
}
