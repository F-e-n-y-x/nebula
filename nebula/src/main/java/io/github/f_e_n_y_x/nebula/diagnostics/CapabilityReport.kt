package io.github.f_e_n_y_x.nebula.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---- Raw facts, gathered on the device by CapabilityCollector (kept free of Android types) ----

data class DeviceFacts(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdk: Int,
    val soc: String? = null,
    val abis: List<String> = emptyList(),
    val glRenderer: String? = null,
    val ramMb: Long = 0,
    val isTv: Boolean = false,
    val appVersion: String = "",
)

/** One profile a decoder declares, with its highest level, both already named. */
data class ProfileFact(val profile: String, val maxLevel: String)

/** Whether a decoder claims it can do [width]×[height] at [fps]. */
data class SizeCheck(val width: Int, val height: Int, val fps: Int, val supported: Boolean)

data class DecoderFacts(
    val name: String,
    /** "video/avc", "video/hevc" or "video/av01". */
    val mime: String,
    /** Android 10+: null when the OS can't say. */
    val hardware: Boolean?,
    val softwareOnly: Boolean?,
    val alias: Boolean? = null,
    /** FEATURE_LowLatency, Android 11+; null when the OS can't say. */
    val lowLatency: Boolean?,
    val adaptivePlayback: Boolean = false,
    val profiles: List<ProfileFact> = emptyList(),
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val sizeChecks: List<SizeCheck> = emptyList(),
)

data class DisplayModeFact(val width: Int, val height: Int, val refreshHz: Float)

data class DisplayFacts(
    val id: Int,
    val name: String,
    val current: DisplayModeFact,
    val modes: List<DisplayModeFact>,
    val hdrTypes: List<String> = emptyList(),
    val isDefault: Boolean = true,
)

/** PackageManager's Vulkan features; null when the device declares none. */
data class VulkanFacts(val hardwareVersion: Int?, val hardwareLevel: Int?, val computeLevel: Int?)

data class WifiFacts(
    val linkMbps: Int?,
    val rxMbps: Int? = null,
    val txMbps: Int? = null,
    val frequencyMhz: Int?,
    val rssiDbm: Int? = null,
    /** ScanResult.WIFI_STANDARD_* (Android 11+). */
    val standard: Int? = null,
)

data class NetworkFacts(
    /** "Wi-Fi", "Ethernet", "Cellular", ... or null when offline. */
    val transport: String?,
    val vpn: Boolean = false,
    val metered: Boolean = false,
    val downKbps: Int? = null,
    val upKbps: Int? = null,
    val wifi: WifiFacts? = null,
    val addresses: List<String> = emptyList(),
)

data class HostFacts(
    val name: String,
    val address: String?,
    val state: String,
    val paired: Boolean,
    val isNova: Boolean,
    val novaVersion: String? = null,
    val features: List<String> = emptyList(),
    val gpu: String? = null,
    val serverVersion: String? = null,
    /** The debug build's mock host: facts are canned, not probed. */
    val demo: Boolean = false,
)

data class ReportInput(
    val generatedAtMs: Long,
    val device: DeviceFacts,
    val decoders: List<DecoderFacts>,
    val displays: List<DisplayFacts>,
    val vulkan: VulkanFacts,
    val network: NetworkFacts,
    val hosts: List<HostFacts>,
    /** DecoderTombstone entries: decoders the engine gave up on after crashes. */
    val tombstones: Map<String, String> = emptyMap(),
)

// ---- The report the UI and the text export show ----

enum class Level { OK, INFO, WARN, BAD }

data class ReportRow(val label: String, val value: String, val level: Level = Level.INFO)

data class ReportSection(val id: String, val title: String, val rows: List<ReportRow>, val notes: List<ReportRow> = emptyList())

/** A one-line headline for the top of the report (a chip in the UI). */
data class Highlight(val text: String, val level: Level)

data class CapabilityReport(val generatedAtMs: Long, val highlights: List<Highlight>, val sections: List<ReportSection>) {
    /** Plain text for copy / share. Stable and greppable: `Label: value`, one per line. */
    fun toText(): String = buildString {
        append("Nebula diagnostics report\n")
        append("Generated ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(generatedAtMs))).append('\n')
        if (highlights.isNotEmpty()) {
            append('\n')
            highlights.forEach { append(marker(it.level)).append(it.text).append('\n') }
        }
        sections.forEach { s ->
            append("\n== ").append(s.title).append(" ==\n")
            s.rows.forEach { r -> append(r.label).append(": ").append(r.value).append('\n') }
            s.notes.forEach { n -> append(marker(n.level)).append(n.value).append('\n') }
        }
    }.trimEnd() + "\n"

    private fun marker(l: Level) = when (l) {
        Level.OK -> "[ok] "
        Level.INFO -> "[i] "
        Level.WARN -> "[!] "
        Level.BAD -> "[x] "
    }
}

/** Turns [ReportInput] into verdicts and rows. Pure, so it's unit tested. */
object ReportBuilder {
    const val AVC = "video/avc"
    const val HEVC = "video/hevc"
    const val AV1 = "video/av01"

    /** Frame generation's Vulkan pipeline targets Vulkan 1.1 with compute. */
    val FRAMEGEN_MIN_VULKAN: Int = vulkanVersion(1, 1, 0)

    fun build(input: ReportInput): CapabilityReport {
        val sections = listOf(
            device(input.device),
            displays(input.displays),
        ) + decoders(input.decoders, input.tombstones) + listOf(
            vulkan(input.vulkan),
            network(input.network),
            hosts(input.hosts),
        )
        return CapabilityReport(input.generatedAtMs, highlights(input), sections)
    }

    // ---- Highlights ----

    fun highlights(input: ReportInput): List<Highlight> {
        val out = ArrayList<Highlight>()
        val hevc = best(input.decoders, HEVC)
        out += when {
            hevc == null -> Highlight("No HEVC decoder: streams fall back to H.264", Level.WARN)
            hasMain10(hevc) -> Highlight("HEVC Main10 in hardware" + if (hevc.lowLatency == true) " · low latency" else "", Level.OK)
            else -> Highlight("HEVC Main only (no 10-bit)", Level.INFO)
        }
        best(input.decoders, AV1)?.let { out += Highlight("AV1 decoder: ${it.name}", if (it.hardware == false || it.softwareOnly == true) Level.INFO else Level.OK) }
        val maxHz = input.displays.flatMap { d -> d.modes + d.current }.maxOfOrNull { it.refreshHz }
        if (maxHz != null) out += Highlight("Display up to ${hz(maxHz)} Hz", if (maxHz >= 90f) Level.OK else Level.INFO)
        out += if (framegenReady(input.vulkan)) {
            Highlight("Vulkan ${vulkanName(input.vulkan.hardwareVersion!!)} · frame generation ready", Level.OK)
        } else {
            Highlight("Frame generation needs Vulkan 1.1 with compute", Level.WARN)
        }
        networkHighlight(input.network)?.let { out += it }
        return out
    }

    private fun networkHighlight(n: NetworkFacts): Highlight? {
        val t = n.transport ?: return Highlight("Offline", Level.BAD)
        val w = n.wifi
        if (w != null) {
            val band = band(w.frequencyMhz)
            val speed = w.linkMbps?.let { " · $it Mbps" } ?: ""
            return Highlight("Wi-Fi ${band ?: ""}$speed".replace("  ", " ").trim(), if (band == BAND_24) Level.WARN else Level.OK)
        }
        return Highlight(t + if (n.vpn) " · VPN" else "", Level.OK)
    }

    // ---- Sections ----

    private fun device(d: DeviceFacts) = ReportSection(
        "device", "Device",
        listOfNotNull(
            ReportRow("Model", "${d.manufacturer} ${d.model} (${d.device})"),
            ReportRow("Android", "${d.androidRelease} (API ${d.sdk})"),
            d.soc?.let { ReportRow("SoC", it) },
            d.glRenderer?.takeIf { it.isNotBlank() }?.let { ReportRow("GPU", it) },
            ReportRow("ABIs", d.abis.joinToString(", ").ifEmpty { "—" }),
            if (d.ramMb > 0) ReportRow("Memory", "%.1f GB".format(Locale.US, d.ramMb / 1024f)) else null,
            ReportRow("Form factor", if (d.isTv) "TV" else "Phone / tablet"),
            ReportRow("Nebula", d.appVersion.ifEmpty { "—" }),
        ),
    )

    private fun displays(list: List<DisplayFacts>): ReportSection {
        val rows = ArrayList<ReportRow>()
        val notes = ArrayList<ReportRow>()
        list.forEach { d ->
            val tag = if (d.isDefault) d.name else "${d.name} (external)"
            rows += ReportRow(tag, "${d.current.width}×${d.current.height} @ ${hz(d.current.refreshHz)} Hz")
            val rates = (d.modes + d.current).filter { it.width == d.current.width && it.height == d.current.height }
                .map { hz(it.refreshHz) }.distinct().sortedBy { it.toFloatOrNull() ?: 0f }
            rows += ReportRow("  Refresh rates", rates.joinToString(", ") { "$it Hz" })
            val sizes = d.modes.map { "${it.width}×${it.height}" }.distinct()
            if (sizes.size > 1) rows += ReportRow("  Sizes", sizes.joinToString(", "))
            rows += ReportRow("  HDR", d.hdrTypes.joinToString(", ").ifEmpty { "none" })
        }
        if (list.isEmpty()) notes += ReportRow("", "No displays reported.", Level.WARN)
        val max = list.flatMap { it.modes + it.current }.maxOfOrNull { it.refreshHz } ?: 0f
        if (max in 1f..61f) notes += ReportRow("", "This screen tops out at 60 Hz; streaming above 60 fps won't look smoother here.", Level.INFO)
        return ReportSection("displays", "Displays", rows, notes)
    }

    /** One section per codec, hardware decoders first. */
    private fun decoders(all: List<DecoderFacts>, tombstones: Map<String, String>): List<ReportSection> {
        val names = mapOf(AVC to "H.264 decoders", HEVC to "HEVC decoders", AV1 to "AV1 decoders")
        val sections = listOf(AVC, HEVC, AV1).map { mime ->
            val list = sortDecoders(all.filter { it.mime == mime })
            val rows = ArrayList<ReportRow>()
            val notes = ArrayList<ReportRow>()
            list.forEach { d ->
                val kind = when {
                    d.softwareOnly == true || d.hardware == false -> "software"
                    d.hardware == true -> "hardware"
                    else -> "unknown"
                }
                val flags = listOfNotNull(
                    kind,
                    when (d.lowLatency) { true -> "low latency"; false -> "no low-latency mode"; null -> null },
                    if (d.adaptivePlayback) "adaptive" else null,
                    if (d.alias == true) "alias" else null,
                )
                rows += ReportRow(d.name, flags.joinToString(" · "), if (kind == "hardware") Level.OK else Level.INFO)
                if (d.maxWidth > 0) rows += ReportRow("  Max size", "${d.maxWidth}×${d.maxHeight}")
                d.profiles.forEach { p -> rows += ReportRow("  ${p.profile}", "up to ${p.maxLevel}") }
                if (d.sizeChecks.isNotEmpty()) {
                    rows += ReportRow("  Modes", d.sizeChecks.joinToString(", ") { c -> "${label(c)} ${if (c.supported) "yes" else "no"}" })
                }
                tombstones[d.name]?.let { notes += ReportRow("", "${d.name} was disabled after crashing ($it).", Level.BAD) }
            }
            if (list.isEmpty()) notes += ReportRow("", "No decoder for ${mime.removePrefix("video/")}.", if (mime == AVC) Level.BAD else Level.INFO)
            if (mime == HEVC) {
                val b = best(list, HEVC)
                if (b != null && !hasMain10(b)) notes += ReportRow("", "No HEVC Main10: HDR and 10-bit streams need it.", Level.INFO)
                if (b != null && b.lowLatency == false) notes += ReportRow("", "${b.name} has no low-latency mode; the engine uses vendor tweaks instead.", Level.INFO)
            }
            ReportSection("decoders-${mime.removePrefix("video/")}", names.getValue(mime), rows, notes)
        }
        val orphans = tombstones.keys - all.map { it.name }.toSet()
        return if (orphans.isEmpty()) sections else sections + ReportSection(
            "tombstones", "Disabled decoders", orphans.map { ReportRow(it, tombstones.getValue(it), Level.BAD) },
        )
    }

    private fun vulkan(v: VulkanFacts): ReportSection {
        val rows = listOf(
            ReportRow("Vulkan", v.hardwareVersion?.let { vulkanName(it) } ?: "not supported", if (v.hardwareVersion != null) Level.OK else Level.WARN),
            ReportRow("Hardware level", v.hardwareLevel?.toString() ?: "—"),
            ReportRow("Compute level", v.computeLevel?.toString() ?: "—"),
        )
        val notes = listOf(
            if (framegenReady(v)) ReportRow("", "Frame generation can run on this device.", Level.OK)
            else ReportRow("", "Frame generation needs Vulkan 1.1 or newer with compute.", Level.WARN),
        )
        return ReportSection("vulkan", "Vulkan (frame generation)", rows, notes)
    }

    private fun network(n: NetworkFacts): ReportSection {
        val rows = ArrayList<ReportRow>()
        val notes = ArrayList<ReportRow>()
        rows += ReportRow("Connection", n.transport ?: "offline", if (n.transport == null) Level.BAD else Level.INFO)
        if (n.vpn) rows += ReportRow("VPN", "yes")
        rows += ReportRow("Metered", if (n.metered) "yes" else "no")
        n.wifi?.let { w ->
            w.linkMbps?.let { rows += ReportRow("Link speed", "$it Mbps") }
            if (w.rxMbps != null || w.txMbps != null) rows += ReportRow("  Receive / send", "${w.rxMbps ?: "—"} / ${w.txMbps ?: "—"} Mbps")
            w.frequencyMhz?.let { f -> rows += ReportRow("Band", listOfNotNull(band(f), "$f MHz").joinToString(" · ")) }
            w.standard?.let { s -> wifiStandard(s)?.let { rows += ReportRow("Standard", it) } }
            w.rssiDbm?.let { rows += ReportRow("Signal", "$it dBm", if (it < -70) Level.WARN else Level.INFO) }
            if (band(w.frequencyMhz) == BAND_24) notes += ReportRow("", "2.4 GHz Wi-Fi is slow and crowded; use 5 GHz or 6 GHz for streaming.", Level.WARN)
            if (w.rssiDbm != null && w.rssiDbm < -70) notes += ReportRow("", "Weak Wi-Fi signal: expect packet loss at high bitrates.", Level.WARN)
            if (w.linkMbps != null && w.linkMbps in 1..150) notes += ReportRow("", "The Wi-Fi link is slow (${w.linkMbps} Mbps); keep the bitrate well under it.", Level.WARN)
        }
        if (n.downKbps != null && n.downKbps > 0) rows += ReportRow("Estimated bandwidth", "${n.downKbps / 1000} Mbps down · ${(n.upKbps ?: 0) / 1000} Mbps up")
        if (n.addresses.isNotEmpty()) rows += ReportRow("Addresses", n.addresses.joinToString(", "))
        return ReportSection("network", "Network", rows, notes)
    }

    private fun hosts(list: List<HostFacts>): ReportSection {
        val rows = ArrayList<ReportRow>()
        val notes = ArrayList<ReportRow>()
        list.forEach { h ->
            val state = listOf(h.state, if (h.paired) "paired" else "not paired").joinToString(" · ")
            rows += ReportRow(h.name + if (h.demo) " (demo)" else "", state, if (h.state.equals("online", true)) Level.OK else Level.INFO)
            h.address?.let { rows += ReportRow("  Address", it) }
            rows += ReportRow("  Server", if (h.isNova) "Nova ${h.novaVersion.orEmpty()}".trim() else "GameStream / Sunshine" + (h.serverVersion?.let { " $it" } ?: ""))
            h.gpu?.let { rows += ReportRow("  GPU", it) }
            if (h.isNova) rows += ReportRow("  Features", h.features.sorted().joinToString(", ").ifEmpty { "none advertised" })
        }
        if (list.isEmpty()) notes += ReportRow("", "No PCs yet. Pair one from Hosts.", Level.INFO)
        if (list.any { it.demo }) notes += ReportRow("", "The demo host's capabilities are canned, not read from a PC.", Level.INFO)
        return ReportSection("hosts", "Hosts (/nova/v1/capabilities)", rows, notes)
    }

    // ---- Helpers ----

    /** The decoder a stream would most likely use: hardware and low latency first, aliases last. */
    fun best(all: List<DecoderFacts>, mime: String): DecoderFacts? = sortDecoders(all.filter { it.mime == mime }).firstOrNull()

    fun sortDecoders(list: List<DecoderFacts>): List<DecoderFacts> = list.sortedWith(
        compareBy<DecoderFacts>(
            { if (it.softwareOnly == true || it.hardware == false) 1 else 0 },
            { if (it.alias == true) 1 else 0 },
            { if (it.lowLatency == true) 0 else 1 },
        ),
    )

    fun hasMain10(d: DecoderFacts): Boolean = d.profiles.any { it.profile.startsWith("Main10") || it.profile.startsWith("Main 10") }

    fun framegenReady(v: VulkanFacts): Boolean =
        (v.hardwareVersion ?: 0) >= FRAMEGEN_MIN_VULKAN && (v.computeLevel ?: -1) >= 0

    /** VK_MAKE_API_VERSION without the variant bits, as PackageManager reports it. */
    fun vulkanVersion(major: Int, minor: Int, patch: Int): Int = (major shl 22) or (minor shl 12) or patch

    fun vulkanName(v: Int): String = "${(v shr 22) and 0x7F}.${(v shr 12) and 0x3FF}.${v and 0xFFF}"

    const val BAND_24 = "2.4 GHz"

    fun band(freqMhz: Int?): String? = when (freqMhz) {
        null -> null
        in 2400..2500 -> BAND_24
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        in 57000..71000 -> "60 GHz"
        else -> null
    }

    fun wifiStandard(s: Int): String? = when (s) {
        1 -> "Legacy (802.11a/b/g)"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> "Wi-Fi 6 (802.11ax)"
        7 -> "802.11ad"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> null
    }

    /** 59.94 → "59.94", 120.00001 → "120". */
    fun hz(v: Float): String {
        val r = (v * 100).roundToInt() / 100f
        return if (r == r.roundToInt().toFloat()) r.roundToInt().toString() else "%.2f".format(Locale.US, r).trimEnd('0').trimEnd('.')
    }

    private fun label(c: SizeCheck) = when {
        c.width == 3840 && c.height == 2160 -> "4K${c.fps}"
        c.width == 2560 && c.height == 1440 -> "1440p${c.fps}"
        c.width == 1920 && c.height == 1080 -> "1080p${c.fps}"
        c.width == 1280 && c.height == 720 -> "720p${c.fps}"
        else -> "${c.width}×${c.height}@${c.fps}"
    }
}
