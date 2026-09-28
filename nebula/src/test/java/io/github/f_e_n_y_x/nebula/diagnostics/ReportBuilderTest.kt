package io.github.f_e_n_y_x.nebula.diagnostics

import io.github.f_e_n_y_x.nebula.diagnostics.ReportBuilder.AV1
import io.github.f_e_n_y_x.nebula.diagnostics.ReportBuilder.AVC
import io.github.f_e_n_y_x.nebula.diagnostics.ReportBuilder.HEVC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {
    private val s25 = DeviceFacts("samsung", "SM-S938B", "pa3q", "16", 36, soc = "Qualcomm SM8750", abis = listOf("arm64-v8a"), glRenderer = "Adreno (TM) 830", ramMb = 12_000, appVersion = "0.3.0-dev6")

    private val hw = DecoderFacts(
        "c2.qti.hevc.decoder.low_latency", HEVC, hardware = true, softwareOnly = false, lowLatency = true, adaptivePlayback = true,
        profiles = listOf(ProfileFact("Main", "6.2"), ProfileFact("Main10", "6.2"), ProfileFact("Main10 HDR10", "6.2")),
        maxWidth = 8192, maxHeight = 4320,
        sizeChecks = listOf(SizeCheck(1920, 1080, 120, true), SizeCheck(3840, 2160, 60, true), SizeCheck(3120, 1440, 120, false)),
    )
    private val sw = DecoderFacts("c2.android.hevc.decoder", HEVC, hardware = false, softwareOnly = true, lowLatency = false, profiles = listOf(ProfileFact("Main", "5.1")))
    private val avc = DecoderFacts("c2.qti.avc.decoder", AVC, hardware = true, softwareOnly = false, lowLatency = true, profiles = listOf(ProfileFact("High", "6.2")))

    private fun input(
        decoders: List<DecoderFacts> = listOf(sw, hw, avc),
        vulkan: VulkanFacts = VulkanFacts(ReportBuilder.vulkanVersion(1, 3, 128), 1, 0),
        network: NetworkFacts = NetworkFacts("Wi-Fi", wifi = WifiFacts(linkMbps = 1201, frequencyMhz = 5180, rssiDbm = -48, standard = 6)),
        hosts: List<HostFacts> = listOf(HostFacts("atom", "192.168.1.20:47989 (local)", "Online", paired = true, isNova = true, novaVersion = "0.3.0", features = listOf("details", "apps", "art"))),
        tombstones: Map<String, String> = emptyMap(),
    ) = ReportInput(
        generatedAtMs = 0L, device = s25, decoders = decoders,
        displays = listOf(DisplayFacts(0, "Built-in screen", DisplayModeFact(3120, 1440, 120f), listOf(DisplayModeFact(3120, 1440, 60f), DisplayModeFact(3120, 1440, 120f), DisplayModeFact(2340, 1080, 120f)), listOf("HDR10", "HDR10+"))),
        vulkan = vulkan, network = network, hosts = hosts, tombstones = tombstones,
    )

    private fun CapabilityReport.section(id: String) = sections.first { it.id == id }
    private fun ReportSection.value(label: String) = rows.first { it.label == label }.value

    @Test
    fun `hardware low-latency decoder sorts first and is the one the headline names`() {
        val r = ReportBuilder.build(input())
        val hevc = r.section("decoders-hevc")
        assertEquals("c2.qti.hevc.decoder.low_latency", hevc.rows.first().label)
        assertEquals("hardware · low latency · adaptive", hevc.rows.first().value)
        assertEquals(Level.OK, hevc.rows.first().level)
        assertTrue(r.highlights.any { it.text == "HEVC Main10 in hardware · low latency" && it.level == Level.OK })
    }

    @Test
    fun `profiles, max size and mode checks are listed per decoder`() {
        val hevc = ReportBuilder.build(input()).section("decoders-hevc")
        assertEquals("up to 6.2", hevc.value("  Main10"))
        assertEquals("8192×4320", hevc.value("  Max size"))
        assertEquals("1080p120 yes, 4K60 yes, 3120×1440@120 no", hevc.rows.first { it.label == "  Modes" }.value)
    }

    @Test
    fun `no Main10 and no low latency are called out`() {
        val r = ReportBuilder.build(input(decoders = listOf(sw, avc)))
        assertTrue(r.highlights.any { it.text.startsWith("HEVC Main only") })
        val notes = r.section("decoders-hevc").notes.map { it.value }
        assertTrue(notes.any { "No HEVC Main10" in it })
        assertTrue(notes.any { "no low-latency mode" in it })
        assertTrue(r.section("decoders-av01").notes.single().value.contains("No decoder for av01"))
    }

    @Test
    fun `missing HEVC warns, missing H264 is bad`() {
        val r = ReportBuilder.build(input(decoders = emptyList()))
        assertEquals(Level.WARN, r.highlights.first().level)
        assertEquals(Level.BAD, r.section("decoders-avc").notes.single().level)
    }

    @Test
    fun `tombstoned decoders are flagged, also ones no longer listed`() {
        val r = ReportBuilder.build(input(tombstones = mapOf("c2.qti.hevc.decoder.low_latency" to "3", "OMX.gone" to "1")))
        assertTrue(r.section("decoders-hevc").notes.any { it.level == Level.BAD && "disabled after crashing" in it.value })
        assertEquals("OMX.gone", r.section("tombstones").rows.single().label)
    }

    @Test
    fun `vulkan version decodes and gates frame generation`() {
        assertEquals("1.3.128", ReportBuilder.vulkanName(ReportBuilder.vulkanVersion(1, 3, 128)))
        assertTrue(ReportBuilder.framegenReady(VulkanFacts(ReportBuilder.vulkanVersion(1, 1, 0), 0, 0)))
        assertFalse(ReportBuilder.framegenReady(VulkanFacts(ReportBuilder.vulkanVersion(1, 0, 61), 0, 0)))
        assertFalse("compute is required", ReportBuilder.framegenReady(VulkanFacts(ReportBuilder.vulkanVersion(1, 3, 0), 1, null)))
        val bad = ReportBuilder.build(input(vulkan = VulkanFacts(null, null, null)))
        assertEquals("not supported", bad.section("vulkan").value("Vulkan"))
        assertTrue(bad.highlights.any { it.level == Level.WARN && "Frame generation" in it.text })
    }

    @Test
    fun `wifi band, speed and the 2_4 GHz warning`() {
        assertEquals("2.4 GHz", ReportBuilder.band(2437))
        assertEquals("5 GHz", ReportBuilder.band(5745))
        assertEquals("6 GHz", ReportBuilder.band(5955))
        val good = ReportBuilder.build(input())
        assertTrue(good.highlights.any { it.text == "Wi-Fi 5 GHz · 1201 Mbps" && it.level == Level.OK })
        assertEquals("Wi-Fi 6 (802.11ax)", good.section("network").value("Standard"))
        val slow = ReportBuilder.build(input(network = NetworkFacts("Wi-Fi", wifi = WifiFacts(linkMbps = 72, frequencyMhz = 2412, rssiDbm = -78))))
        val notes = slow.section("network").notes.map { it.value }
        assertTrue(notes.any { "2.4 GHz" in it })
        assertTrue(notes.any { "Weak Wi-Fi" in it })
        assertTrue(notes.any { "72 Mbps" in it })
        assertTrue(slow.highlights.any { it.level == Level.WARN && it.text.startsWith("Wi-Fi 2.4 GHz") })
    }

    @Test
    fun `offline and ethernet networks`() {
        assertEquals(Level.BAD, ReportBuilder.build(input(network = NetworkFacts(null))).highlights.last().level)
        assertTrue(ReportBuilder.build(input(network = NetworkFacts("Ethernet", vpn = true))).highlights.any { it.text == "Ethernet · VPN" })
    }

    @Test
    fun `displays list refresh rates for the current size, sorted`() {
        val d = ReportBuilder.build(input()).section("displays")
        assertEquals("3120×1440 @ 120 Hz", d.value("Built-in screen"))
        assertEquals("60 Hz, 120 Hz", d.value("  Refresh rates"))
        assertEquals("HDR10, HDR10+", d.value("  HDR"))
        assertEquals("59.94", ReportBuilder.hz(59.94f))
        assertEquals("120", ReportBuilder.hz(120.00001f))
    }

    @Test
    fun `host capabilities show Nova features sorted, demo hosts are labelled`() {
        val r = ReportBuilder.build(input())
        val h = r.section("hosts")
        assertEquals("Online · paired", h.value("atom"))
        assertEquals("Nova 0.3.0", h.value("  Server"))
        assertEquals("apps, art, details", h.value("  Features"))
        val demo = ReportBuilder.build(input(hosts = listOf(HostFacts("atom", null, "Online", true, true, demo = true)))).section("hosts")
        assertNotNull(demo.rows.firstOrNull { it.label == "atom (demo)" })
        assertTrue(demo.notes.any { "canned" in it.value })
        assertTrue(ReportBuilder.build(input(hosts = emptyList())).section("hosts").notes.single().value.startsWith("No PCs"))
    }

    @Test
    fun `text export is line oriented and complete`() {
        val text = ReportBuilder.build(input()).toText()
        assertTrue(text.startsWith("Nebula diagnostics report\n"))
        assertTrue(text.contains("== HEVC decoders ==\nc2.qti.hevc.decoder.low_latency: hardware · low latency · adaptive\n"))
        assertTrue(text.contains("[ok] HEVC Main10 in hardware · low latency\n"))
        assertTrue(text.contains("Model: samsung SM-S938B (pa3q)\n"))
        assertTrue(text.contains("== Vulkan (frame generation) ==\nVulkan: 1.3.128\n"))
        assertTrue(text.endsWith("\n") && !text.endsWith("\n\n"))
    }

    @Test
    fun `secret settings keys are hidden from the export`() {
        listOf("server_cert", "client_private_key", "pairing_state", "uniqueid", "mac_address", "pin").forEach {
            assertTrue(it, DiagnosticsExport.isSecret(it))
        }
        listOf("seekbar_deadzone", "checkbox_enable_pip", "list_resolution", "keyboard_page", "pinch_zoom").forEach {
            assertFalse(it, DiagnosticsExport.isSecret(it))
        }
    }
}
