package com.limelight.binding.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build

import com.limelight.LimeLog
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.jni.MoonBridge

/**
 * Bit-perfect AC3 / E-AC3 passthrough renderer.
 *
 * Hands the encoded bitstream straight to the platform [AudioTrack] in
 * [AudioFormat.ENCODING_AC3] / [AudioFormat.ENCODING_E_AC3] mode. The OS /
 * receiver then decodes (or further forwards via SPDIF / HDMI to an AVR).
 *
 * Implementation cribbed from Kodi's `AESinkAUDIOTRACK.cpp` which is the
 * battle-tested reference for raw AC3/EAC3 passthrough on Android:
 *   - capability probed via [AudioTrack.getMinBufferSize] (works since API 1
 *     unlike `isDirectPlaybackSupported` which is API 29+ and known to lie
 *     on many Android TV firmwares),
 *   - `AudioAttributes` set to USAGE_MEDIA + CONTENT_TYPE_MUSIC so the
 *     framework routes to the HDMI/SPDIF passthrough sink instead of the
 *     internal speaker mixer (USAGE_GAME breaks routing on many TVs),
 *   - channel mask hard-pinned to STEREO for raw passthrough — the actual
 *     channel layout lives inside the AC3 bitstream header, the AVR decodes
 *     and routes; sending CHANNEL_OUT_5POINT1 with ENCODING_AC3 is rejected
 *     by some firmwares,
 *   - no offloaded playback (Kodi avoids it for raw PT; offload semantics
 *     for compressed bitstreams are inconsistent across vendors and can
 *     drop frames silently).
 *
 * Only [setup] for non-Opus codecs succeeds; Opus must use [AndroidAudioRenderer].
 */
class Ac3PassthroughRenderer(
    private val context: Context,
    private val bufferBytes: Int = 16 * 1024
) : AudioRenderer {

    private var track: AudioTrack? = null
    private var encoding: Int = 0
    private var codecName: String = ""

    override fun setup(
        audioConfiguration: MoonBridge.AudioConfiguration,
        sampleRate: Int,
        samplesPerFrame: Int,
        codec: Int,
        bitrate: Int
    ): Int {
        if (codec == MoonBridge.AUDIO_CODEC_OPUS) {
            return -1
        }

        encoding = when (codec) {
            MoonBridge.AUDIO_CODEC_AC3 -> {
                codecName = "AC3"
                AudioFormat.ENCODING_AC3
            }
            MoonBridge.AUDIO_CODEC_EAC3 -> {
                codecName = "E-AC3"
                AudioFormat.ENCODING_E_AC3
            }
            else -> {
                LimeLog.severe("Ac3PassthroughRenderer: unknown codec=$codec")
                return -1
            }
        }

        // Reject configs the encoder couldn't handle; the actual channel
        // layout is carried in the AC3 bitstream header so the AudioTrack
        // mask must always be STEREO for raw passthrough (Kodi convention).
        if (audioConfiguration.channelCount !in 1..6) {
            LimeLog.severe("Ac3PassthroughRenderer: unsupported channels=${audioConfiguration.channelCount}")
            return -1
        }
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO

        // USAGE_MEDIA + CONTENT_TYPE_MUSIC matches what Kodi sends; this is
        // what causes the HDMI/SPDIF route on every Android TV firmware
        // we've seen. USAGE_GAME would route to the internal mixer.
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        // Real capability probe: getMinBufferSize returns < 0 / ERROR_BAD_VALUE
        // when the framework has no codec/route able to consume this encoding.
        // This is the exact check Kodi uses (VerifySinkConfiguration).
        val minBuffer = try {
            AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        } catch (e: Throwable) {
            LimeLog.warning("Ac3PassthroughRenderer: getMinBufferSize threw: ${e.message}")
            -1
        }
        if (minBuffer <= 0) {
            LimeLog.warning("Ac3PassthroughRenderer: $codecName not supported on this device/route (minBuffer=$minBuffer)")
            return -1
        }

        // Buffer sizing — game streaming is latency-critical, so we deviate
        // from Kodi's HTPC-oriented values:
        //   Kodi AC3:  max(minBuffer * 3, frame * 8)   ≈ 256 ms
        //   Kodi EAC3: 2 * 10752                       ≈ 443 ms
        // We target ~4 frames (~128 ms) which is enough headroom for one
        // typical AVR HDMI decode round-trip + jitter while keeping audio
        // delay below human-perceptible-during-gaming threshold.
        //
        // The user-tunable bufferBytes pref now acts as a CEILING (let the
        // user trade latency for stability) rather than a hard FLOOR (which
        // forced everyone to ≥200 ms regardless of preference).
        val frameBytes = if (bitrate > 0) bitrate * 1536 / sampleRate / 8 else 1536
        val targetFrames = 4
        val computedBuffer = maxOf(minBuffer, frameBytes * targetFrames)
        // Apply the user pref as a ceiling, but never less than computedBuffer
        // (so the framework's own minimum is always respected) and never
        // above 16 frames (~512 ms — diminishing returns past that).
        val bufferSize = computedBuffer
            .coerceAtLeast(bufferBytes.coerceAtMost(frameBytes * 16))

        try {
            val builder = AudioTrack.Builder()
                .setAudioFormat(format)
                .setAudioAttributes(attributes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)
            // Hint the framework to pick a low-latency audio path. May be
            // ignored for compressed encodings on some vendor stacks, but
            // it's harmless and helps where it does work.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            track = builder.build()

            if (track!!.state != AudioTrack.STATE_INITIALIZED) {
                LimeLog.severe("Ac3PassthroughRenderer: AudioTrack state=${track!!.state}, not INITIALIZED")
                track?.release()
                track = null
                return -2
            }

            // Match Kodi's idle-then-play sequencing: pause+flush before
            // play() makes some TV firmwares more willing to accept the
            // first frame without a startup glitch.
            track!!.pause()
            track!!.flush()
            track!!.play()

            val bufferMs = if (frameBytes > 0) bufferSize.toLong() * 32 / frameBytes else 0
            LimeLog.info("Ac3PassthroughRenderer: $codecName initialized @${sampleRate} Hz, ${audioConfiguration.channelCount}ch (mask=STEREO for raw PT), bitrate=$bitrate bps, buffer=${bufferSize}B (~${bufferMs} ms, min=$minBuffer, frame=$frameBytes)")
            return 0
        } catch (e: Exception) {
            LimeLog.severe("Ac3PassthroughRenderer: AudioTrack create failed: ${e.message}")
            try {
                track?.release()
            } catch (_: Exception) {}
            track = null
            return -2
        }
    }

    override fun start() {
        // play() already called in setup(); nothing additional.
    }

    override fun stop() {
        try {
            track?.pause()
            track?.flush()
        } catch (_: Exception) {}
    }

    override fun playDecodedAudio(audioData: ShortArray) {
        // Unused: native side never calls this for non-Opus codecs.
    }

    override fun playEncodedAudio(audioData: ByteArray, length: Int) {
        val t = track ?: return
        if (length <= 0) return
        try {
            // AC3 is frame-aligned (sync word 0x0B77 marks frame start). A short
            // write would desync the receiver / AVR. Loop with WRITE_BLOCKING
            // semantics until the entire frame is committed (or the track
            // returns an error).
            var offset = 0
            while (offset < length) {
                val written = t.write(audioData, offset, length - offset, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) {
                    LimeLog.warning("Ac3PassthroughRenderer.write returned $written, dropping rest of frame")
                    break
                }
                offset += written
            }
        } catch (e: Exception) {
            LimeLog.warning("Ac3PassthroughRenderer.write failed: ${e.message}")
        }
    }

    override fun cleanup() {
        try {
            track?.stop()
            track?.release()
        } catch (_: Exception) {}
        track = null
    }
}
