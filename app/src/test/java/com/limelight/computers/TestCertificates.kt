package com.limelight.computers

import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

internal object TestCertificates {
    val serverCert: X509Certificate = FakeCertificate()

    private class FakeCertificate : X509Certificate() {
        override fun getEncoded(): ByteArray = byteArrayOf(1, 2, 3)
        override fun verify(key: PublicKey?) = Unit
        override fun verify(key: PublicKey?, sigProvider: String?) = Unit
        override fun toString(): String = "FakeCertificate"
        override fun getPublicKey(): PublicKey? = null
        override fun checkValidity() = Unit
        override fun checkValidity(date: Date?) = Unit
        override fun getVersion(): Int = 3
        override fun getSerialNumber(): BigInteger = BigInteger.ONE
        override fun getIssuerDN(): Principal = X500Principal("CN=fake")
        override fun getSubjectDN(): Principal = X500Principal("CN=fake")
        override fun getNotBefore(): Date = Date(0)
        override fun getNotAfter(): Date = Date(Long.MAX_VALUE)
        override fun getTBSCertificate(): ByteArray = byteArrayOf()
        override fun getSignature(): ByteArray = byteArrayOf()
        override fun getSigAlgName(): String = "none"
        override fun getSigAlgOID(): String = "0"
        override fun getSigAlgParams(): ByteArray? = null
        override fun getIssuerUniqueID(): BooleanArray? = null
        override fun getSubjectUniqueID(): BooleanArray? = null
        override fun getKeyUsage(): BooleanArray? = null
        override fun getBasicConstraints(): Int = -1
        override fun getCriticalExtensionOIDs(): MutableSet<String>? = null
        override fun getExtensionValue(oid: String?): ByteArray? = null
        override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = null
        override fun hasUnsupportedCriticalExtension(): Boolean = false
    }
}
