package io.github.fenyx.nebula.engine

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.view.SurfaceHolder
import com.limelight.LimeLog
import com.limelight.binding.PlatformBinding
import com.limelight.binding.audio.SmartAudioRenderer
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.computers.IdentityManager
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import com.limelight.nvstream.mdns.NsdManagerDiscoveryAgent
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.utils.HdrCapabilityHelper
import io.github.fenyx.nebula.engine.internal.ArtCache
import io.github.fenyx.nebula.engine.internal.DatabaseHostStore
import io.github.fenyx.nebula.engine.internal.DecoderSupport
import io.github.fenyx.nebula.engine.internal.HostRepository
import io.github.fenyx.nebula.engine.internal.NvHttpHostBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Entry point to Nebula's streaming engine: hosts, pairing, apps and artwork, and streams.
 * One instance per process; get it with [create]. Everything is main-safe: suspend functions and
 * flows do their network and disk work on an IO dispatcher.
 */
class NebulaEngine private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val identity by lazy { IdentityManager(appContext) }
    private val crypto by lazy { PlatformBinding.getCryptoProvider(appContext) }

    private val art = ArtCache(File(appContext.cacheDir, "nebula-art"))

    /** Artwork cache size, usage and clearing. */
    val artCache = ArtCacheControl(art)

    private val repository = HostRepository(
        backend = NvHttpHostBackend(uniqueId = { identity.uniqueId }, clientName = ::clientName, crypto = crypto),
        store = DatabaseHostStore(appContext),
        art = art,
        scope = scope,
        io = Dispatchers.IO,
    )

    private var discovery: MdnsDiscoveryAgent? = null

    /** The user's streaming settings (shared with V+). */
    val preferences = EnginePreferences(appContext)

    /** Saved and discovered hosts, sorted by name. Starts with the saved list in UNKNOWN state. */
    val hosts: StateFlow<List<Host>> = repository.hosts

    init {
        scope.launch { repository.load() }
        // Warm up decoder support (GL renderer probe + MediaCodecHelper) off the main thread.
        scope.launch { runCatching { DecoderSupport.ensure(appContext) } }
    }

    // ---- Hosts ----

    /** Starts mDNS discovery and background polling of known hosts. Call from onStart(). */
    @Synchronized
    fun startDiscovery() {
        repository.startPolling()
        if (discovery != null) return
        val listener = object : MdnsDiscoveryListener {
            override fun notifyComputerAdded(computer: MdnsComputer) {
                val candidate = ComputerDetails().apply {
                    computer.getLocalAddress()?.hostAddress?.let { localAddress = ComputerDetails.AddressTuple(it, computer.getPort()) }
                    computer.getIpv6Address()?.hostAddress?.let { ipv6Address = ComputerDetails.AddressTuple(it, computer.getPort()) }
                }
                scope.launch { repository.addDiscovered(candidate) }
            }

            override fun notifyDiscoveryFailure(e: Exception) {
                LimeLog.warning("mDNS discovery failed: ${e.message}")
            }
        }
        // Same split as V+: jmDNS handles multi-address services better before Android 14,
        // NsdManager works behind mDNS proxies (ChromeOS, emulator) from 14 on.
        discovery = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            JmDNSDiscoveryAgent(appContext, listener)
        } else {
            NsdManagerDiscoveryAgent(appContext, listener)
        }.also { it.startDiscovery(DISCOVERY_INTERVAL_MS) }
    }

    /** Stops mDNS and polling. Call from onStop(). */
    @Synchronized
    fun stopDiscovery() {
        discovery?.stopDiscovery()
        discovery = null
        repository.stopPolling()
    }

    /**
     * Adds a host by "address", "address:port" or "[ipv6]:port". Throws IllegalArgumentException for
     * malformed input and IOException when no host answers.
     */
    suspend fun addHostManually(address: String): Host = repository.addManually(address)

    /** Polls a host now; null if the id is unknown. */
    suspend fun refresh(hostId: String): Host? = repository.refresh(hostId)

    /** Sends Wake-on-LAN; false when the host has no known MAC address or sending failed. */
    suspend fun wake(hostId: String): Boolean = repository.wake(hostId)

    /**
     * Puts the host to sleep (`/pcsleep`). Throws [HostRefusedException] when the host refuses and
     * IOException when it can't be reached. Check [NovaCapabilities.has] ([NovaFeature.PC_SLEEP]) first.
     */
    suspend fun sleepHost(hostId: String) = repository.sleep(hostId)

    /** Host-wide commands the host's owner defined; per-app ones are on [HostApp.commands]. */
    suspend fun hostCommands(hostId: String): HostCommandList = repository.commands(hostId)

    /** Runs a host command (`/supercmd?cmdId=`). Same errors as [sleepHost]. */
    suspend fun runHostCommand(hostId: String, commandId: String) = repository.runCommand(hostId, commandId)

    suspend fun unpair(hostId: String) = repository.unpair(hostId)

    /** Removes a host from the saved list. */
    suspend fun forget(hostId: String) = repository.forget(hostId)

    /**
     * Pairs with a host. Emits [PairingState.GeneratingPin] with the PIN to show (or [pin] if you
     * pass one), then [PairingState.WaitingForHost] while the user enters it on the host, then
     * [PairingState.Paired] or [PairingState.Failed]. Cancel collection to abandon the attempt.
     */
    fun pair(hostId: String, pin: String? = null): Flow<PairingState> = repository.pair(hostId, pin)

    // ---- Apps ----

    /** The host's apps, re-fetched every 10 s while collected. Nova hosts add art and play stats. */
    fun apps(hostId: String): Flow<List<HostApp>> = repository.apps(hostId)

    /** Rich metadata; Nova hosts only (null elsewhere). */
    suspend fun details(hostId: String, appId: String): AppDetails? = repository.appDetails(hostId, appId)

    /** Encoded image bytes (decode with BitmapFactory or Coil), disk-cached. */
    suspend fun loadArt(hostId: String, appId: String, kind: ArtKind): ByteArray? = repository.loadArt(hostId, appId, kind)

    /** One of [AppDetails.screenshots], disk-cached. */
    suspend fun loadScreenshot(hostId: String, path: String): ByteArray? = repository.loadScreenshot(hostId, path)

    // ---- Streaming ----

    /**
     * Starts streaming [appId] from [hostId] into [surface]. Call on the main thread once the
     * surface exists (from SurfaceHolder.Callback.surfaceChanged). The session stops itself when the
     * surface is destroyed; [activity] is used for decoder selection and HDR window mode.
     *
     * Settings not covered by [request] come from [preferences].
     */
    fun startStream(
        activity: Activity,
        hostId: String,
        appId: String,
        request: StreamRequest,
        surface: SurfaceHolder,
        listener: StreamListener,
    ): StreamSession {
        val host = requireNotNull(repository.details(hostId)) { "Unknown host $hostId" }
        val address = requireNotNull(host.activeAddress) { "Host ${host.name} hasn't been reached yet; refresh it first" }
        val cert = requireNotNull(host.serverCert) { "Host ${host.name} isn't paired" }
        val gameStreamId = requireNotNull(appId.toIntOrNull()) { "Not a GameStream app id: $appId" }

        DecoderSupport.ensure(activity)
        val prefs = PreferenceConfiguration.readPreferences(activity).also { request.applyTo(it) }
        val session = StreamSession(activity, surface, listener, request.width, request.height, backgroundPolicy(activity))

        val hdrSupport = HdrCapabilityHelper.getHdrTypeSupport(activity)
        var hdr = prefs.enableHdr && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && when (prefs.hdrMode) {
            MoonBridge.HDR_MODE_HLG -> hdrSupport.hasHlg
            else -> hdrSupport.hasHdr10 || hdrSupport.hasHdr10Plus
        }
        // Dynamic-metadata formats need V+'s negotiation path; the engine streams them as static HDR10.
        if (prefs.hdrMode != MoonBridge.HDR_MODE_HLG) prefs.hdrMode = MoonBridge.HDR_MODE_HDR10

        val tombstones = activity.getSharedPreferences("DecoderTombstone", 0)
        val decoder = MediaCodecDecoderRenderer(
            activity,
            prefs,
            { tombstones.edit().putInt("CrashCount", tombstones.getInt("CrashCount", 0) + 1).commit() },
            tombstones.getInt("CrashCount", 0),
            (activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered,
            hdr,
            false,
            DecoderSupport.glRenderer(activity),
            session.perfListener,
        )

        val hevcHdr = if (prefs.hdrMode == MoonBridge.HDR_MODE_HLG) decoder.isHevcMain10Supported() else decoder.isHevcMain10Hdr10Supported()
        val av1Hdr = if (prefs.hdrMode == MoonBridge.HDR_MODE_HLG) decoder.isAv1Main10Supported() else decoder.isAv1Main10Hdr10Supported()
        hdr = hdr && when (prefs.videoFormat) {
            PreferenceConfiguration.FormatOption.FORCE_HEVC -> hevcHdr
            PreferenceConfiguration.FormatOption.FORCE_AV1 -> av1Hdr
            PreferenceConfiguration.FormatOption.FORCE_H264 -> false
            PreferenceConfiguration.FormatOption.AUTO -> hevcHdr || av1Hdr
        }
        decoder.setHdr10PlusRequested(false)

        var formats = MoonBridge.VIDEO_FORMAT_H264
        if (decoder.isHevcSupported()) {
            formats = formats or MoonBridge.VIDEO_FORMAT_H265
            if (hdr && hevcHdr) formats = formats or MoonBridge.VIDEO_FORMAT_H265_MAIN10
        }
        if (decoder.isAv1Supported()) {
            formats = formats or MoonBridge.VIDEO_FORMAT_AV1_MAIN8
            if (hdr && av1Hdr) formats = formats or MoonBridge.VIDEO_FORMAT_AV1_MAIN10
        }

        val caps = repository.hosts.value.firstOrNull { it.id == hostId }?.novaCapabilities
        // Hosts from Nova phase 1 on list the caller's permissions and advertise mic/clipboard as
        // features; older hosts don't, so for them the stream handshake alone decides.
        val phase1 = caps?.permissions != null
        // Asking for a mic the host has turned off makes its RTSP SETUP answer 404, which aborts the
        // whole connection, so only ask when a phase-1 host advertises "mic".
        val micRequested = prefs.enableMic && (!phase1 || caps!!.has(NovaFeature.MIC))

        @Suppress("DEPRECATION")
        val refreshRate = activity.windowManager.defaultDisplay.refreshRate
        val extras = request.extras
        val config = StreamConfiguration.Builder()
            .setResolution(prefs.width, prefs.height)
            .setLaunchRefreshRate(prefs.fps)
            .setRefreshRate(prefs.fps)
            .setApp(NvApp(appId, gameStreamId, hdr))
            .setBitrate(prefs.bitrate)
            .setResolutionScale(prefs.resolutionScale)
            .setEnableSops(prefs.enableSops)
            .enableLocalAudioPlayback(prefs.playHostAudio)
            .setMaxPacketSize(extras[StreamExtras.MAX_PACKET_SIZE] as? Int ?: DEFAULT_PACKET_SIZE)
            .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
            .setSupportedVideoFormats(formats)
            .setAttachedGamepadMask(extras[StreamExtras.GAMEPAD_MASK] as? Int ?: 1)
            .setClientRefreshRateX100((refreshRate * 100).roundToInt())
            .setAudioConfiguration(prefs.audioConfiguration)
            .setAudioCodec(if (prefs.enableAudioPassthrough) prefs.audioCodec else MoonBridge.AUDIO_CODEC_OPUS)
            .setAudioBitrate(prefs.audioCodecBitrate)
            .setHostGamepad(prefs.hostGamepadSelection.resolve(prefs.screenDs5Touchpad, false))
            .setColorSpace(decoder.getPreferredColorSpace())
            .setColorRange(decoder.getPreferredColorRange())
            .setHdrMode(if (hdr) prefs.hdrMode else MoonBridge.HDR_MODE_SDR)
            .setHdrBrightnessOverride(hdr && prefs.hdrBrightnessOverride, prefs.hdrPeakBrightnessNits)
            .setPersistGamepadsAfterDisconnect(!prefs.multiController)
            .setUseVdd(extras[StreamExtras.USE_VDD] as? Boolean)
            .setTouchKeyboard(prefs.touchKeyboardAutoInvoke)
            .setEnableMic(micRequested)
            .setControlOnly(prefs.controlOnly)
            .setCustomScreenMode(prefs.screenCombinationMode)
            .setNovaDisplayMode(request.novaDisplay?.wire)
            .build()

        val connection = NvConnection(
            appContext,
            address,
            host.httpsPort,
            identity.uniqueId,
            repository.pairName(hostId),
            config,
            crypto,
            cert,
        )
        val audio = SmartAudioRenderer(
            context = activity,
            enableAudioFx = prefs.enableAudioFx,
            enableSpatializer = prefs.enableSpatializer,
            passthroughBufferBytes = prefs.audioPassthroughBufferBytes,
            useAc3Iec61937 = prefs.useAc3Iec61937,
        )
        session.linkConfig = StreamHostLink.Config(
            enableMic = micRequested,
            micInitialState = com.limelight.preferences.MicrophoneInitialState.fromPreferenceValue(prefs.micInitialState),
            hostId = hostId,
            clipboardText = prefs.enableClipboardSyncText,
            clipboardImage = prefs.enableClipboardSyncImage,
            requireClipboardFlag = caps != null,
            clipboardDenied = phase1 && !(caps!!.has(NovaFeature.CLIPBOARD) && caps.allows(NovaFeature.PERMISSION_CLIPBOARD)),
        )
        session.start(connection, decoder, audio)
        return session
    }

    /**
     * V+'s "When Moonlight goes to the background" choice; unset means keep the stream connected.
     * The grace period is Nebula's own setting (seconds, default 60).
     */
    private fun backgroundPolicy(context: Context): BackgroundPolicy {
        val sp = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        val grace = (sp.all[BACKGROUND_GRACE_KEY] as? Int ?: DEFAULT_BACKGROUND_GRACE_S).coerceAtLeast(0) * 1000L
        return when (sp.getString("list_background_stream_behavior", null)) {
            "disconnect", "resume" -> BackgroundPolicy(graceMs = 0)
            "keep_audio" -> BackgroundPolicy(graceMs = grace, keepAudio = true)
            "keep_connected" -> BackgroundPolicy(graceMs = grace, keepAudio = false)
            else -> BackgroundPolicy(graceMs = grace, keepAudio = sp.getBoolean("checkbox_background_audio", false))
        }
    }

    private fun clientName(): String =
        Settings.Global.getString(appContext.contentResolver, "device_name") ?: Build.MODEL ?: "Nebula"

    /**
     * Readies hardware decoding (GL renderer probe, decoder whitelists). startStream does this
     * itself; call it early, off the main thread, to avoid the one-time cost at stream start.
     * Returns the decoder that would handle [mimeType], or null if the device has none.
     */
    fun prepareDecoders(mimeType: String = "video/avc"): String? {
        DecoderSupport.ensure(appContext)
        return com.limelight.binding.video.MediaCodecHelper.findProbableSafeDecoder(mimeType, -1)?.name
    }

    companion object {
        private const val DISCOVERY_INTERVAL_MS = 1500
        private const val DEFAULT_PACKET_SIZE = 1392
        /** Seconds a backgrounded stream stays connected (SharedPreferences Int). */
        const val BACKGROUND_GRACE_KEY = "nebula_background_grace_s"
        const val DEFAULT_BACKGROUND_GRACE_S = 60

        @Volatile
        private var instance: NebulaEngine? = null

        /** The process-wide engine; safe to call repeatedly from anywhere. */
        @JvmStatic
        fun create(context: Context): NebulaEngine =
            instance ?: synchronized(this) { instance ?: NebulaEngine(context).also { instance = it } }
    }
}
