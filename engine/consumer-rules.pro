# Rules applied to every app that uses :engine.
# JNI entry points (MoonBridge natives + callbacks, Opus encoder) are looked up by name.
-keep class com.limelight.nvstream.jni.* {*;}
-keep class com.limelight.binding.audio.OpusEncoder {*;}
# Okio
-keep class sun.misc.Unsafe {*;}
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okio.**
# BouncyCastle (providers are loaded reflectively)
-keep class org.bouncycastle.jcajce.provider.asymmetric.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.util.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.* {*;}
-keep class org.bouncycastle.jcajce.provider.digest.** {*;}
-keep class org.bouncycastle.jcajce.provider.symmetric.** {*;}
-keep class org.bouncycastle.jcajce.spec.* {*;}
-keep class org.bouncycastle.jce.** {*;}
-dontwarn javax.naming.**
# jMDNS
-dontwarn javax.jmdns.impl.DNSCache
-dontwarn org.slf4j.**
# BouncyCastle registers algorithms by class-name strings (e.g. X.509 → ...asymmetric.x509.CertificateFactory).
# Obfuscating those names makes CertificateFactory.getInstance("X.509", bc) fail, which breaks host polling.
-keep class org.bouncycastle.jcajce.provider.asymmetric.x509.** {*;}
-keepnames class org.bouncycastle.jcajce.provider.** {*;}
-keepnames class org.bouncycastle.jce.provider.** {*;}
