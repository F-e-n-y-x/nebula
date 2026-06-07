package com.limelight.computers

import com.limelight.LimeLog
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager

internal object TrustedPairState {
    fun sanitizePollResult(current: ComputerDetails, polled: ComputerDetails): ComputerDetails {
        if (!isUntrustedNotPairedDowngrade(current, polled)) {
            return polled
        }

        LimeLog.warning("Ignoring untrusted NOT_PAIRED poll for locally paired host ${current.name ?: current.uuid}")
        return ComputerDetails(polled).apply {
            pairState = PairingManager.PairState.PAIRED
            serverCert = current.serverCert
            serverInfoTrustedByCert = false
        }
    }

    private fun isUntrustedNotPairedDowngrade(
        current: ComputerDetails,
        polled: ComputerDetails
    ): Boolean {
        return hasLocalPairing(current) &&
                polled.state == ComputerDetails.State.ONLINE &&
                polled.pairState == PairingManager.PairState.NOT_PAIRED &&
                !polled.serverInfoTrustedByCert
    }

    private fun hasLocalPairing(details: ComputerDetails): Boolean {
        return details.serverCert != null ||
                details.pairState == PairingManager.PairState.PAIRED
    }
}
