package io.github.f_e_n_y_x.nebula.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel as P
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.view.Display
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.BuildConfig
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.ui.theme.isTelevision
import java.net.Inet4Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Reads everything the capability report shows from the device and the paired hosts. */
class CapabilityCollector(private val context: Context, private val container: AppContainer) {

    /** [probeHosts] asks each paired host for /serverinfo and /nova/v1/capabilities again first. */
    suspend fun collect(probeHosts: Boolean): ReportInput = withContext(Dispatchers.Default) {
        val hosts = async { hosts(probeHosts) }
        ReportInput(
            generatedAtMs = System.currentTimeMillis(),
            device = device(),
            decoders = runCatching { decoders() }.getOrDefault(emptyList()),
            displays = runCatching { displays() }.getOrDefault(emptyList()),
            vulkan = vulkan(),
            network = runCatching { network() }.getOrDefault(NetworkFacts(null)),
            hosts = hosts.await(),
            tombstones = tombstones(),
        )
    }

    private fun device(): DeviceFacts {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim().takeIf { it.isNotBlank() && it != "unknown unknown" } else Build.HARDWARE
        return DeviceFacts(
            manufacturer = Build.MANUFACTURER, model = Build.MODEL, device = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE, sdk = Build.VERSION.SDK_INT, soc = soc,
            abis = Build.SUPPORTED_ABIS.toList(),
            // Cached by the engine's decoder setup (same file V+ uses).
            glRenderer = context.getSharedPreferences("GlPreferences", 0).getString("Renderer", null),
            ramMb = mem.totalMem / (1024 * 1024),
            isTv = isTelevision(context),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    private fun decoders(): List<DecoderFacts> {
        val (dw, dh) = io.github.f_e_n_y_x.nebula.ui.screens.deviceResolution(context)
        val maxHz = maxRefresh().toInt().coerceAtLeast(60)
        val checks = listOf(1280 to 720 to 60, 1920 to 1080 to 60, 1920 to 1080 to 120, 2560 to 1440 to 120, 3840 to 2160 to 60, dw to dh to maxHz)
            .distinct()
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }.flatMap { info ->
            info.supportedTypes.filter { it.lowercase() in MIMES }.mapNotNull { type ->
                val caps = runCatching { info.getCapabilitiesForType(type) }.getOrNull() ?: return@mapNotNull null
                val mime = type.lowercase()
                val v = caps.videoCapabilities
                DecoderFacts(
                    name = info.name,
                    mime = mime,
                    hardware = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else null,
                    softwareOnly = if (Build.VERSION.SDK_INT >= 29) info.isSoftwareOnly else null,
                    alias = if (Build.VERSION.SDK_INT >= 29) info.isAlias else null,
                    lowLatency = if (Build.VERSION.SDK_INT >= 30) caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency) else null,
                    adaptivePlayback = caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback),
                    profiles = profiles(mime, caps.profileLevels),
                    maxWidth = v?.supportedWidths?.upper ?: 0,
                    maxHeight = v?.supportedHeights?.upper ?: 0,
                    sizeChecks = if (v == null) emptyList() else checks.map { (wh, fps) ->
                        val (w, h) = wh
                        SizeCheck(w, h, fps, runCatching { v.areSizeAndRateSupported(w, h, fps.toDouble()) }.getOrDefault(false))
                    },
                )
            }
        }
    }

    /** Highest level per named profile, in the codec's usual order. */
    private fun profiles(mime: String, levels: Array<MediaCodecInfo.CodecProfileLevel>): List<ProfileFact> {
        val byProfile = LinkedHashMap<String, Int>()
        levels.sortedBy { it.profile }.forEach { pl ->
            val name = profileName(mime, pl.profile) ?: return@forEach
            byProfile[name] = maxOf(byProfile[name] ?: 0, pl.level)
        }
        return byProfile.map { (p, l) -> ProfileFact(p, levelName(mime, l)) }
    }

    private fun profileName(mime: String, p: Int): String? = when (mime) {
        ReportBuilder.AVC -> when (p) {
            P.AVCProfileBaseline -> "Baseline"
            P.AVCProfileConstrainedBaseline -> "Constrained Baseline"
            P.AVCProfileMain -> "Main"
            P.AVCProfileHigh -> "High"
            P.AVCProfileConstrainedHigh -> "Constrained High"
            else -> null
        }
        ReportBuilder.HEVC -> when (p) {
            P.HEVCProfileMain -> "Main"
            P.HEVCProfileMain10 -> "Main10"
            P.HEVCProfileMain10HDR10 -> "Main10 HDR10"
            P.HEVCProfileMain10HDR10Plus -> "Main10 HDR10+"
            P.HEVCProfileMainStill -> null
            else -> null
        }
        ReportBuilder.AV1 -> when (p) {
            P.AV1ProfileMain8 -> "Main 8-bit"
            P.AV1ProfileMain10 -> "Main10"
            P.AV1ProfileMain10HDR10 -> "Main10 HDR10"
            P.AV1ProfileMain10HDR10Plus -> "Main10 HDR10+"
            else -> null
        }
        else -> null
    }

    private fun levelName(mime: String, l: Int): String = when (mime) {
        ReportBuilder.AVC -> AVC_LEVELS[l]
        ReportBuilder.HEVC -> HEVC_LEVELS[l]
        ReportBuilder.AV1 -> AV1_LEVELS[l]
        else -> null
    } ?: "0x${l.toString(16)}"

    private fun displays(): List<DisplayFacts> {
        val dm = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
        return dm.displays.map { d ->
            val m = d.mode
            DisplayFacts(
                id = d.displayId,
                name = d.name,
                current = DisplayModeFact(m.physicalWidth, m.physicalHeight, m.refreshRate),
                modes = d.supportedModes.map { DisplayModeFact(it.physicalWidth, it.physicalHeight, it.refreshRate) },
                hdrTypes = hdrTypes(d),
                isDefault = d.displayId == Display.DEFAULT_DISPLAY,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun hdrTypes(d: Display): List<String> = (d.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)).map {
        when (it) {
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
            Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
            Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
            else -> "type $it"
        }
    }

    private fun maxRefresh(): Float = runCatching {
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).supportedModes.maxOf { it.refreshRate }
    }.getOrDefault(60f)

    private fun vulkan(): VulkanFacts {
        val pm = context.packageManager
        fun feature(name: String): Int? = pm.systemAvailableFeatures.firstOrNull { it.name == name }?.version
        return VulkanFacts(
            hardwareVersion = feature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION),
            hardwareLevel = feature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL),
            computeLevel = feature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE),
        )
    }

    private fun network(): NetworkFacts {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkFacts(null)
        val net = cm.activeNetwork ?: return NetworkFacts(null)
        val caps = cm.getNetworkCapabilities(net) ?: return NetworkFacts(null)
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> "USB"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        val wifi = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi(caps) else null
        val addrs = cm.getLinkProperties(net)?.linkAddresses.orEmpty().map { it.address }.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
        return NetworkFacts(
            transport = transport,
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true },
            metered = cm.isActiveNetworkMetered,
            downKbps = caps.linkDownstreamBandwidthKbps,
            upKbps = caps.linkUpstreamBandwidthKbps,
            wifi = wifi,
            addresses = addrs,
        )
    }

    @Suppress("DEPRECATION")
    private fun wifi(caps: NetworkCapabilities): WifiFacts? {
        val info: WifiInfo = (if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else null)
            ?: runCatching { context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo }.getOrNull()
            ?: return null
        return WifiFacts(
            linkMbps = info.linkSpeed.takeIf { it > 0 },
            rxMbps = if (Build.VERSION.SDK_INT >= 29) info.rxLinkSpeedMbps.takeIf { it > 0 } else null,
            txMbps = if (Build.VERSION.SDK_INT >= 29) info.txLinkSpeedMbps.takeIf { it > 0 } else null,
            frequencyMhz = info.frequency.takeIf { it > 0 },
            rssiDbm = info.rssi.takeIf { it in -126..0 },
            standard = if (Build.VERSION.SDK_INT >= 30) info.wifiStandard.takeIf { it > 0 } else null,
        )
    }

    private suspend fun hosts(probe: Boolean): List<HostFacts> {
        val engine = container.engine
        if (engine == null) {
            // Debug demo build: the mock host, with the capabilities a current Nova advertises.
            return container.hosts.observeHosts().first().map { h ->
                HostFacts(
                    name = h.name, address = h.address, state = h.status.label(), paired = h.paired, isNova = h.isNova,
                    novaVersion = h.version, features = if (h.isNova) DEMO_FEATURES else emptyList(), gpu = h.gpu, demo = true,
                )
            }
        }
        if (probe) {
            coroutineScope {
                engine.hosts.value.filter { it.paired }.map { h -> async { withTimeoutOrNull(4_000) { runCatching { engine.refresh(h.id) } } } }.awaitAll()
            }
        }
        val domain = container.hosts.observeHosts().first().associateBy { it.id }
        return engine.hosts.value.map { h ->
            val d = domain[h.id]
            HostFacts(
                name = h.name,
                address = h.activeAddress?.let { "${it.address}:${it.port} (${it.kind.name.lowercase()})" } ?: h.addresses.firstOrNull()?.address,
                state = h.state.name.lowercase().replaceFirstChar { it.uppercase() },
                paired = h.paired,
                isNova = h.isNova,
                novaVersion = h.novaCapabilities?.version,
                features = h.novaCapabilities?.features?.toList().orEmpty(),
                gpu = d?.gpu,
                serverVersion = d?.version,
            )
        }
    }

    private fun tombstones(): Map<String, String> = runCatching {
        context.getSharedPreferences("DecoderTombstone", 0).all.mapValues { it.value.toString() }
    }.getOrDefault(emptyMap())

    private fun HostStatus.label() = name.lowercase().replaceFirstChar { it.uppercase() }

    private companion object {
        val MIMES = setOf(ReportBuilder.AVC, ReportBuilder.HEVC, ReportBuilder.AV1)
        val DEMO_FEATURES = listOf("apps", "art", "details", "virtual-display", "mirror", "playtime", "last-session")

        val AVC_LEVELS = mapOf(
            P.AVCLevel1 to "1", P.AVCLevel1b to "1b", P.AVCLevel11 to "1.1", P.AVCLevel12 to "1.2", P.AVCLevel13 to "1.3",
            P.AVCLevel2 to "2", P.AVCLevel21 to "2.1", P.AVCLevel22 to "2.2", P.AVCLevel3 to "3", P.AVCLevel31 to "3.1",
            P.AVCLevel32 to "3.2", P.AVCLevel4 to "4", P.AVCLevel41 to "4.1", P.AVCLevel42 to "4.2", P.AVCLevel5 to "5",
            P.AVCLevel51 to "5.1", P.AVCLevel52 to "5.2", P.AVCLevel6 to "6", P.AVCLevel61 to "6.1", P.AVCLevel62 to "6.2",
        )
        val HEVC_LEVELS = mapOf(
            P.HEVCMainTierLevel1 to "1", P.HEVCHighTierLevel1 to "1 High", P.HEVCMainTierLevel2 to "2", P.HEVCHighTierLevel2 to "2 High",
            P.HEVCMainTierLevel21 to "2.1", P.HEVCHighTierLevel21 to "2.1 High", P.HEVCMainTierLevel3 to "3", P.HEVCHighTierLevel3 to "3 High",
            P.HEVCMainTierLevel31 to "3.1", P.HEVCHighTierLevel31 to "3.1 High", P.HEVCMainTierLevel4 to "4", P.HEVCHighTierLevel4 to "4 High",
            P.HEVCMainTierLevel41 to "4.1", P.HEVCHighTierLevel41 to "4.1 High", P.HEVCMainTierLevel5 to "5", P.HEVCHighTierLevel5 to "5 High",
            P.HEVCMainTierLevel51 to "5.1", P.HEVCHighTierLevel51 to "5.1 High", P.HEVCMainTierLevel52 to "5.2", P.HEVCHighTierLevel52 to "5.2 High",
            P.HEVCMainTierLevel6 to "6", P.HEVCHighTierLevel6 to "6 High", P.HEVCMainTierLevel61 to "6.1", P.HEVCHighTierLevel61 to "6.1 High",
            P.HEVCMainTierLevel62 to "6.2", P.HEVCHighTierLevel62 to "6.2 High",
        )
        val AV1_LEVELS = mapOf(
            P.AV1Level2 to "2.0", P.AV1Level21 to "2.1", P.AV1Level22 to "2.2", P.AV1Level23 to "2.3",
            P.AV1Level3 to "3.0", P.AV1Level31 to "3.1", P.AV1Level32 to "3.2", P.AV1Level33 to "3.3",
            P.AV1Level4 to "4.0", P.AV1Level41 to "4.1", P.AV1Level42 to "4.2", P.AV1Level43 to "4.3",
            P.AV1Level5 to "5.0", P.AV1Level51 to "5.1", P.AV1Level52 to "5.2", P.AV1Level53 to "5.3",
            P.AV1Level6 to "6.0", P.AV1Level61 to "6.1", P.AV1Level62 to "6.2", P.AV1Level63 to "6.3",
        )
    }
}
