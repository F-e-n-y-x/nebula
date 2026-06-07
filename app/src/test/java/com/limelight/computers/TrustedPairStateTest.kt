package com.limelight.computers

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TrustedPairStateTest {
    @Test
    fun untrustedNotPairedDoesNotDowngradeLocalPairing() {
        val current = pairedComputer(hasServerCert = false)
        val polled = notPairedPoll(trustedByCert = false)

        val sanitized = TrustedPairState.sanitizePollResult(current, polled)

        assertEquals(PairingManager.PairState.PAIRED, sanitized.pairState)
    }

    @Test
    fun localServerCertificateIsEnoughToProtectColdStartState() {
        val current = ComputerDetails().apply {
            serverCert = TestCertificates.serverCert
            pairState = null
        }
        val polled = notPairedPoll(trustedByCert = false)

        val sanitized = TrustedPairState.sanitizePollResult(current, polled)

        assertEquals(PairingManager.PairState.PAIRED, sanitized.pairState)
        assertSame(TestCertificates.serverCert, sanitized.serverCert)
    }

    @Test
    fun trustedNotPairedCanDowngradeLocalPairing() {
        val current = pairedComputer(hasServerCert = true)
        val polled = notPairedPoll(trustedByCert = true)

        val sanitized = TrustedPairState.sanitizePollResult(current, polled)

        assertSame(polled, sanitized)
        assertEquals(PairingManager.PairState.NOT_PAIRED, sanitized.pairState)
    }

    @Test
    fun untrustedNotPairedIsAcceptedForUnpairedHost() {
        val current = ComputerDetails()
        val polled = notPairedPoll(trustedByCert = false)

        val sanitized = TrustedPairState.sanitizePollResult(current, polled)

        assertSame(polled, sanitized)
        assertEquals(PairingManager.PairState.NOT_PAIRED, sanitized.pairState)
    }

    private fun pairedComputer(hasServerCert: Boolean): ComputerDetails {
        return ComputerDetails().apply {
            pairState = PairingManager.PairState.PAIRED
            if (hasServerCert) {
                serverCert = TestCertificates.serverCert
            }
        }
    }

    private fun notPairedPoll(trustedByCert: Boolean): ComputerDetails {
        return ComputerDetails().apply {
            state = ComputerDetails.State.ONLINE
            pairState = PairingManager.PairState.NOT_PAIRED
            serverInfoTrustedByCert = trustedByCert
        }
    }
}
