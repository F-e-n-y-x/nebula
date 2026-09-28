package com.limelight.nvstream.http

import io.github.fenyx.nebula.engine.FormFactor
import io.github.fenyx.nebula.engine.PairingIdentity
import java.security.PrivateKey
import java.security.cert.X509Certificate
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The /pair request carries Nebula's identity; without one it stays the stock Moonlight request. */
class NvHttpPairingIdentityTest {
    private val paired = MockResponse.Builder().body("<root status_code=\"200\"><paired>1</paired></root>").build()

    @Test
    fun pairRequestCarriesIdentity() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(paired)
            val http = client(server)
            http.pairingIdentity = PairingIdentity("Zoë's 📱 & Tab", version = "0.3.0-dev7", form = FormFactor.TABLET)
            http.executePairingCommand("phrase=getservercert&salt=00", false)

            val url = server.takeRequest().url
            assertEquals("Zoë's 📱 & Tab", url.queryParameter("devicename"))
            assertEquals("Nebula", url.queryParameter("clientapp"))
            assertEquals("0.3.0-dev7", url.queryParameter("clientver"))
            assertEquals("tablet", url.queryParameter("clientform"))
            assertEquals("getservercert", url.queryParameter("phrase"))
            assertEquals("1", url.queryParameter("updateState"))
            assertEquals(1, url.queryParameterValues("devicename").size)
        }
    }

    @Test
    fun stockRequestWithoutIdentity() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(paired)
            client(server).executePairingCommand("phrase=getservercert&salt=00", false)

            val url = server.takeRequest().url
            assertEquals("roth", url.queryParameter("devicename"))
            assertNull(url.queryParameter("clientapp"))
            assertEquals("test-client", url.queryParameter("clientname"))
        }
    }

    private fun client(server: MockWebServer): NvHTTP {
        val identity = HeldCertificate.Builder().commonName("client").build()
        return NvHTTP(
            ComputerDetails.AddressTuple(server.hostName, server.port),
            0,
            "test-client-id",
            "test-client",
            null,
            object : LimelightCryptoProvider {
                override fun getClientCertificate(): X509Certificate = identity.certificate
                override fun getClientPrivateKey(): PrivateKey = identity.keyPair.private
                override fun getPemEncodedClientCertificate(): ByteArray = identity.certificatePem().toByteArray()
                override fun encodeBase64String(data: ByteArray): String = java.util.Base64.getEncoder().encodeToString(data)
            },
        )
    }
}
