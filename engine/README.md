# :engine — Nebula streaming engine

`:engine` is the streaming core taken from V+, packaged as an Android library with **no UI**. It contains:

- the GameStream/Nova protocol: `nvstream`, and moonlight-common-c through JNI (`libmoonlight-core`)
- pairing and client identity
- host discovery (mDNS)
- the MediaCodec video renderer and the audio renderers
- the saved-host database

On top of that sits one small facade, `io.github.fenyx.nebula.engine`. Apps should use only the facade. The `com.limelight.*` classes are still public so that V+ (`:app`) can keep compiling unchanged, but treat them as internal.

```groovy
dependencies { implementation project(':engine') }
```

Build requirements:

- NDK 28.2.13676358
- `audioHapticsSdkDir`, set in `local.properties` or the `AUDIO_HAPTICS_SDK_DIR` environment variable
- the `moonlight-common-c` submodule at `engine/src/main/jni/moonlight-core/moonlight-common-c`

## Getting the engine

```kotlin
val engine = NebulaEngine.create(context)   // process-wide singleton
```

Settings, paired hosts, the client certificate and the unique id live in the same files V+ uses. An app that shares V+'s `applicationId` sees V+'s hosts. Any other app starts with an empty list.

## Hosts

```kotlin
// onStart / onStop of whatever screen shows hosts
engine.startDiscovery()      // mDNS + polling every 5 s
engine.stopDiscovery()

engine.hosts.collect { hosts: List<Host> -> render(hosts) }

val host = engine.addHostManually("192.168.1.20")        // or "desk.local:47989", "[fe80::1]:47989"
engine.refresh(host.id)
engine.wake(host.id)        // Wake-on-LAN; false if no MAC is known
engine.unpair(host.id)
engine.forget(host.id)      // remove from the saved list
```

A `Host` has these fields:

- `id` (the host UUID) and `name`
- `addresses` / `activeAddress`
- `state`: `ONLINE`, `OFFLINE` or `UNKNOWN`
- `paired`
- `isNova` / `novaCapabilities`
- `runningAppId`
- `macAddress`

Nova capabilities are probed once the host is paired, because the `/nova/v1` endpoints need the client certificate.

### Pairing

```kotlin
engine.pair(host.id).collect { state ->
    when (state) {
        is PairingState.GeneratingPin -> showPin(state.pin)   // user types it on the host
        PairingState.WaitingForHost -> showSpinner()
        PairingState.Paired -> done()
        is PairingState.Failed -> showError(state.reason)     // WRONG_PIN, HOST_BUSY, UNREACHABLE, …
    }
}
```

- To use your own PIN, pass it: `pair(id, pin = "1234")`.
- Cancelling the collection abandons the attempt.
- Polling skips a host while it is pairing.

## Apps and artwork

```kotlin
engine.apps(host.id).collect { apps: List<HostApp> -> … }   // re-fetched every 10 s while collected

val details = engine.details(host.id, app.id)                // Nova only; null on plain Sunshine
val poster = engine.loadArt(host.id, app.id, ArtKind.POSTER) // ByteArray? — PNG/JPEG
val hero   = engine.loadArt(host.id, app.id, ArtKind.HERO)
details?.screenshots?.forEach { engine.loadScreenshot(host.id, it) }
```

- Nova metadata is matched to GameStream apps by the `appid` that Nova reports, and by name when there is no `appid`.
- `loadArt` uses the Nova art when the host has it. For `POSTER` it falls back to GameStream box art.
- Artwork is cached on disk, in `cacheDir/nebula-art` (64 MB, least recently used entries are evicted first).
- Decode the bytes with `BitmapFactory` or Coil.

## Streaming

A stream needs an `Activity`, which is used to pick the decoder and set the HDR window mode, plus a `SurfaceHolder` whose surface already exists.

```kotlin
class StreamActivity : ComponentActivity(), SurfaceHolder.Callback, StreamListener {
    private var session: StreamSession? = null

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        if (session != null) return
        val request = engine.preferences.defaultRequest()
            .copy(novaDisplay = NovaDisplayMode.VIRTUAL)       // Nova: virtual display vs mirror
        session = engine.startStream(this, hostId, appId, request, holder, this)
        lifecycleScope.launch { session!!.stats.collect { overlay.show(it) } }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        session = null      // the session already disconnected itself
    }

    override fun dispatchKeyEvent(event: KeyEvent) =
        session?.input?.sendKey(event) == true || super.dispatchKeyEvent(event)

    override fun onEnded(reason: StreamEndReason, errorCode: Int) = finish()
}
```

- `StreamListener` callbacks arrive on the main thread, and every one is optional:
  - stage started / failed
  - connected
  - ended
  - messages
  - poor connection
  - rumble
  - HDR change
  - resolution change
- `session.stats` is a `StateFlow<StreamStats>`, updated about once per second. It carries:
  - fps and received fps
  - measured bitrate
  - host / network / decode / render latency
  - frame loss %
  - decoder name, resolution and HDR
- `session.setBitrate(kbps)` is a suspend call. It returns `true` once the host accepts the new bitrate.
- `session.quit()` ends the stream and closes the app on the host. `session.disconnect()` leaves the app running so it can be resumed.
- `session.input` (`InputBridge`) handles:
  - keys through V+'s `KeyboardTranslator`
  - text
  - relative and absolute mouse, buttons, high-resolution scroll
  - native touch
  - full gamepad state and controller arrival

### Request vs saved settings

`StreamRequest` sets these for one stream:

- resolution, fps and bitrate
- codec (`AUTO`, `H264`, `HEVC`, `AV1`)
- HDR
- audio layout
- Nova display mode

Everything else comes from the saved V+ settings, such as frame pacing, decoder tweaks, the audio codec and the host gamepad type. `extras` passes a few more options (see `StreamExtras`):

- `enableMic`, `playHostAudio`, `enableSops`, `controlOnly`
- `useVdd`, `touchKeyboard`, `customScreenMode`
- `maxPacketSize`, `gamepadMask`

## Settings

```kotlin
val prefs = engine.preferences
val request = prefs.defaultRequest()          // saved resolution/fps/bitrate/codec/HDR/audio
prefs.save(request.copy(fps = 120))           // write back with V+'s keys
prefs.edit { it.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED }   // full V+ config
prefs.sharedPreferences                       // raw access with V+ key names
```

## Current limits

- HDR is limited to static HDR10 and HLG. HDR10+ and Dolby Vision selections fall back to HDR10, because dynamic-metadata negotiation still lives in V+'s `Game`.
- These V+ features stay in `:app` for now and aren't wired into `StreamSession`:
  - frame generation
  - the microphone
  - adaptive bitrate
  - audio-coupled haptics
  - cursor passthrough
  - DS5 triggers and LEDs
- Polling is simpler than V+'s `ComputerManagerService`: all addresses are tried in parallel with an 8 s cap. V+'s adaptive timeouts and STUN remote-address discovery are not included.
- Streaming stops when the surface is destroyed. V+'s "extreme resume" background mode isn't available.
