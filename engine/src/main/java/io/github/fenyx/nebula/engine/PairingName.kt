package io.github.fenyx.nebula.engine

import java.util.Locale

/** What kind of device Nebula runs on, as sent to the host (`clientform`). */
enum class FormFactor(val wire: String) {
    PHONE("phone"),
    TABLET("tablet"),
    TV("tv"),
}

/**
 * How Nebula introduces itself to a host when pairing. The host shows [displayName]
 * ("Nebula from Ayush's S25 Ultra") as the suggested name; Nova adds its own user as the owner
 * when [deviceName] names none.
 */
data class PairingIdentity(
    /** The device part: "Ayush's S25 Ultra", or the model ("Galaxy S25 Ultra") when no owner is known. */
    val deviceName: String,
    val version: String = "",
    val form: FormFactor = FormFactor.PHONE,
    val app: String = PairingName.APP,
) {
    val displayName: String get() = PairingName.display(app, deviceName)

    /**
     * Query parameters for the /pair request. `devicename` is standard GameStream; the `client*`
     * extras are Nova's, and other hosts ignore them.
     */
    fun queryParameters(): List<Pair<String, String>> = buildList {
        add("devicename" to deviceName.ifEmpty { PairingName.FALLBACK_DEVICE })
        add("clientapp" to app)
        if (version.isNotEmpty()) add("clientver" to version)
        add("clientform" to form.wire)
    }
}

/** Rules for the name Nebula pairs under. Pure functions, so they're unit-tested on the JVM. */
object PairingName {
    const val APP = "Nebula"

    /** Longest device part, in code points; leaves room for "Nebula from " within Nova's 64. */
    const val MAX_DEVICE_CHARS = 48

    /** Used when Android reports neither a device name nor a model. */
    const val FALLBACK_DEVICE = "Android device"

    /** Samsung model-number prefixes (region suffix dropped) to marketing names. */
    private val MODELS = linkedMapOf(
        "SM-S938" to "Galaxy S25 Ultra",
        "SM-S937" to "Galaxy S25 Edge",
        "SM-S936" to "Galaxy S25+",
        "SM-S931" to "Galaxy S25",
        "SM-S928" to "Galaxy S24 Ultra",
        "SM-S926" to "Galaxy S24+",
        "SM-S921" to "Galaxy S24",
        "SM-S918" to "Galaxy S23 Ultra",
        "SM-S916" to "Galaxy S23+",
        "SM-S911" to "Galaxy S23",
        "SM-F966" to "Galaxy Z Fold7",
        "SM-F956" to "Galaxy Z Fold6",
        "SM-F946" to "Galaxy Z Fold5",
        "SM-F766" to "Galaxy Z Flip7",
        "SM-F741" to "Galaxy Z Flip6",
        "SM-F731" to "Galaxy Z Flip5",
        "SM-X92" to "Galaxy Tab S10 Ultra",
        "SM-X82" to "Galaxy Tab S10+",
        "SM-X91" to "Galaxy Tab S9 Ultra",
        "SM-X81" to "Galaxy Tab S9+",
        "SM-X71" to "Galaxy Tab S9",
    )

    private val POSSESSIVE = Regex("""(?<=\S)(?:['’]s|s['’])(?=\s|$)""")

    /**
     * Trims, turns control characters and whitespace runs into single spaces, and cuts to
     * [maxChars] code points.
     */
    fun clean(value: String?, maxChars: Int = MAX_DEVICE_CHARS): String {
        if (value.isNullOrEmpty()) return ""
        val collapsed = buildString {
            var space = false
            var i = 0
            while (i < value.length) {
                val cp = value.codePointAt(i)
                i += Character.charCount(cp)
                if (Character.isWhitespace(cp) || Character.isISOControl(cp) || Character.isSpaceChar(cp)) {
                    space = isNotEmpty()
                    continue
                }
                if (space) append(' ').also { space = false }
                appendCodePoint(cp)
            }
        }
        if (collapsed.codePointCount(0, collapsed.length) <= maxChars) return collapsed
        return collapsed.substring(0, collapsed.offsetByCodePoints(0, maxChars)).trimEnd()
    }

    /** Whether [name] already names its owner: "Ayush's S25", "Ayush’s S25", "James' TV". */
    fun hasOwner(name: String): Boolean = POSSESSIVE.containsMatchIn(name)

    /** Marketing name for a model number ("SM-S938B" → "Galaxy S25 Ultra"); other models as reported. */
    fun marketingModel(model: String?, manufacturer: String?): String {
        val m = clean(model)
        if (m.isEmpty()) return ""
        val upper = m.uppercase(Locale.ROOT)
        MODELS.entries.firstOrNull { upper.startsWith(it.key) }?.let { return it.value }
        val maker = clean(manufacturer).replaceFirstChar { it.titlecase(Locale.ROOT) }
        // Bare model codes ("CPH2581") read better with the maker in front.
        val looksLikeCode = ' ' !in m && m.any { it.isDigit() }
        return if (maker.isNotEmpty() && looksLikeCode && !m.startsWith(maker, ignoreCase = true)) "$maker $m" else m
    }

    /**
     * The device part to pair under: Android's device name when it names an owner
     * ("Ayush's S25 Ultra"), else the marketing model, so Nova can add its own user as the owner.
     */
    fun deviceName(androidDeviceName: String?, model: String?, manufacturer: String?): String {
        val named = clean(androidDeviceName)
        if (named.isNotEmpty() && hasOwner(named)) return named
        return marketingModel(model, manufacturer).ifEmpty { named }.ifEmpty { FALLBACK_DEVICE }
    }

    /** "Nebula from Ayush's S25 Ultra"; a device part that already starts with the app name is kept. */
    fun display(app: String, device: String): String {
        val d = clean(device)
        if (d.isEmpty()) return app
        val startsWithApp = d.startsWith(app, ignoreCase = true) && (d.length == app.length || d[app.length] == ' ')
        return if (startsWithApp) d else "$app from $d"
    }
}
