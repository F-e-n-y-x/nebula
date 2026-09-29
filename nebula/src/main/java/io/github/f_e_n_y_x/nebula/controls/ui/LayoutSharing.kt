package io.github.f_e_n_y_x.nebula.controls.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.f_e_n_y_x.nebula.BuildConfig
import io.github.f_e_n_y_x.nebula.R
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.ControlsFormatException
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.DeviceClass
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.GameRef
import io.github.f_e_n_y_x.nebula.controls.LayoutFetcher
import io.github.f_e_n_y_x.nebula.controls.LayoutFile
import io.github.f_e_n_y_x.nebula.controls.LayoutFit
import io.github.f_e_n_y_x.nebula.controls.LayoutIndex
import io.github.f_e_n_y_x.nebula.controls.LayoutMeta
import io.github.f_e_n_y_x.nebula.controls.LayoutTarget
import io.github.f_e_n_y_x.nebula.controls.LibraryEntry
import io.github.f_e_n_y_x.nebula.controls.LayoutGenres
import io.github.f_e_n_y_x.nebula.controls.LibraryIndex
import io.github.f_e_n_y_x.nebula.controls.LibrarySource
import io.github.f_e_n_y_x.nebula.controls.PreviewBox
import io.github.f_e_n_y_x.nebula.controls.ProfileImport
import io.github.f_e_n_y_x.nebula.controls.UrlPolicy
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.screens.ToggleRow
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.min

/** Serves shared layout files and preview images from cache/layouts (see the manifest). */
class LayoutFileProvider : FileProvider(R.xml.layout_paths)

// ---------------------------------------------------------------- thumbnails

/** One box of a thumbnail: zones are shares of the area, everything else dp. */
internal data class ThumbItem(val kind: ElementKind, val x: Float, val y: Float, val w: Float, val h: Float, val round: Boolean, val label: String = "")

internal fun ControlElement.thumb() = ThumbItem(kind, x, y, width, height, shape != io.github.f_e_n_y_x.nebula.controls.ElementShape.SQUARE, label)
internal fun PreviewBox.thumb() = ThumbItem(kind, x, y, w, h, round)

/**
 * Draws a layout the way it looks on a phone, into any Android canvas: the in-app thumbnail and
 * the PNG attached when sharing use the same drawing. The area stands for a landscape screen
 * [LayoutFit.REF_W_DP] dp wide, so dp sizes shrink with it.
 */
internal object LayoutThumb {
    fun draw(c: android.graphics.Canvas, items: List<ThumbItem>, w: Float, h: Float, labels: Boolean) {
        val k = w / LayoutFit.REF_W_DP
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.RadialGradient(w * 0.5f, h * 0.4f, w * 0.8f, 0xFF1D1838.toInt(), 0xFF0A0A0B.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, w, h), 10f * k * 2, 10f * k * 2, p)
        p.shader = null
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEDEDEF.toInt(); textAlign = Paint.Align.CENTER; textSize = 11f * k * 1.4f }
        for (it in items.sortedBy { it.kind != ElementKind.ZONE }) {
            val bw = if (it.kind == ElementKind.ZONE) it.w * w else it.w * k
            val bh = if (it.kind == ElementKind.ZONE) it.h * h else it.h * k
            val r = RectF(it.x * w - bw / 2, it.y * h - bh / 2, it.x * w + bw / 2, it.y * h + bh / 2)
            if (it.kind == ElementKind.ZONE) {
                p.style = Paint.Style.FILL; p.color = 0x14FFFFFF
                c.drawRoundRect(r, 12f * k, 12f * k, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = maxOf(1f, 1.2f * k); p.color = 0x59FFFFFF
                p.pathEffect = DashPathEffect(floatArrayOf(10f * k, 7f * k), 0f)
                c.drawRoundRect(r, 12f * k, 12f * k, p)
                p.pathEffect = null
                continue
            }
            val corner = if (it.round) min(bw, bh) / 2 else 10f * k
            p.style = Paint.Style.FILL; p.color = 0xCC17171A.toInt()
            c.drawRoundRect(r, corner, corner, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = maxOf(1f, 1.2f * k)
            p.color = if (it.kind == ElementKind.STICK || it.kind == ElementKind.DPAD) 0x99B7A2FF.toInt() else 0x80FFFFFF.toInt()
            c.drawRoundRect(r, corner, corner, p)
            if (labels && it.label.isNotBlank() && bw > 20f) {
                val t = if (it.label.length > 7) it.label.take(6) + "…" else it.label
                c.drawText(t, r.centerX(), r.centerY() + text.textSize / 3, text)
            }
        }
    }

    fun bitmap(items: List<ThumbItem>, aspect: Float = 2.17f, width: Int = 1200): Bitmap {
        val h = (width / aspect.coerceIn(1.2f, 2.6f)).toInt()
        val b = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        draw(android.graphics.Canvas(b), items, width.toFloat(), h.toFloat(), labels = true)
        return b
    }
}

@Composable
internal fun LayoutThumbnail(items: List<ThumbItem>, aspect: Float? = null, modifier: Modifier = Modifier, labels: Boolean = false) {
    Canvas(modifier.fillMaxWidth().aspectRatio((aspect ?: 2.17f).coerceIn(1.2f, 2.6f))) {
        drawIntoCanvas { LayoutThumb.draw(it.nativeCanvas, items, size.width, size.height, labels) }
    }
}

// ---------------------------------------------------------------- QR

internal fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2)
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}

// ---------------------------------------------------------------- helpers

/** The landscape controls area of this device in dp (the screen less the controls padding). */
@Composable
internal fun areaDp(): Pair<Float, Float> {
    val c = LocalConfiguration.current
    val long = maxOf(c.screenWidthDp, c.screenHeightDp) - 24f
    val short = min(c.screenWidthDp, c.screenHeightDp) - 24f
    return long to short
}

@Composable
internal fun deviceClass(): DeviceClass = DeviceClass.forSmallestWidth(LocalConfiguration.current.smallestScreenWidthDp)

internal fun copyText(ctx: Context, label: String, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun writeCache(ctx: Context, name: String, bytes: ByteArray): Uri {
    val dir = File(ctx.cacheDir, "layouts").apply { mkdirs() }
    val f = File(dir, name)
    f.writeBytes(bytes)
    return FileProvider.getUriForFile(ctx, ctx.packageName + ".layouts", f)
}

/** Where the library is: the saved choice (debug builds: any http LAN address too), else GitHub. */
internal fun libraryUrl(prefs: LegacyPrefs): String {
    val saved = prefs.prefs.getString(LibrarySource.PREF_KEY, null)?.trim()
    return saved?.takeIf { runCatching { UrlPolicy(BuildConfig.DEBUG).check(it) }.isSuccess } ?: LibrarySource.DEFAULT_URL
}

internal fun fetcher(ctx: Context) = LayoutFetcher(UrlPolicy(allowLanHttp = BuildConfig.DEBUG), File(ctx.cacheDir, "layout-library"))

private fun newProfileId() = "p-" + UUID.randomUUID().toString().take(12)

// ---------------------------------------------------------------- import preview

/**
 * What is about to be imported, before anything is saved: the thumbnail, who made it and for
 * which game, what it sends, and anything left out. [onAdd] gets the profile, fitted to this
 * screen when asked.
 */
@Composable
internal fun LayoutPreviewDialog(outcome: ProfileImport.Outcome, onAdd: (ControlsProfile) -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val (aw, ah) = areaDp()
    val p = outcome.profile
    val meta = outcome.meta ?: p.meta
    val here = deviceClass()
    var fit by remember { mutableStateOf(true) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        modifier = Modifier.testTag("layout-preview"),
        title = { Text(p.name, style = Nebula.type.heading, color = NebulaColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = s.dp(420)).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                LayoutThumbnail(p.landscape.map { it.thumb() }, meta?.aspect, Modifier.widthIn(max = s.dp(340)).border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(10))), labels = true)
                val facts = buildList {
                    add(outcome.source.label)
                    meta?.author?.takeIf { it.isNotBlank() }?.let { add("by $it") }
                    meta?.game?.name?.let { add(it) }
                    add((meta?.target ?: LayoutTarget.detect(p.landscape)).label)
                    meta?.device?.takeIf { it != DeviceClass.ANY }?.let { add("made on a ${it.label.lowercase()}") }
                    add("${p.landscape.size} controls")
                }
                Text(facts.joinToString(" · "), style = Nebula.type.label, color = NebulaColors.textSecondary)
                meta?.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = Nebula.type.body, color = NebulaColors.textSecondary) }
                if (outcome.skipped.isNotEmpty()) {
                    Text("Left out (${outcome.skipped.size}):", style = Nebula.type.bodyStrong, color = NebulaColors.text)
                    outcome.skipped.forEach { k -> Text("• ${k.what}: ${k.reason}", style = Nebula.type.label, color = NebulaColors.textMuted) }
                }
                ToggleRow(
                    "Fit to this screen",
                    if (meta?.device != null && meta.device != DeviceClass.ANY && meta.device != here) "Made on a ${meta.device.label.lowercase()}: button sizes are scaled for this ${here.label.lowercase()}." else "Scales button sizes to this screen and keeps every control on it.",
                    fit,
                ) { fit = it }
            }
        },
        confirmButton = {
            NebulaButton("Add to my profiles", onClick = { onAdd(if (fit) LayoutFit.fit(p, aw, ah) else p) }, modifier = Modifier.testTag("layout-add"))
        },
        dismissButton = { NebulaButton("Cancel", onClick = onDismiss, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}

// ---------------------------------------------------------------- share

/**
 * Share a profile as a layout file: metadata to fill in, then the share sheet, a file, the
 * clipboard (file or one-line share code), a QR code for small layouts, a preview image, and
 * "Share to library", which opens a pre-filled GitHub page (no account or token in the app).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShareLayoutDialog(profile: ControlsProfile, onMeta: (LayoutMeta) -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val (aw, ah) = areaDp()
    val device = deviceClass()
    val base = profile.meta
    var name by remember { mutableStateOf(base?.name?.takeIf { it.isNotBlank() } ?: profile.name) }
    var author by remember { mutableStateOf(base?.author.orEmpty()) }
    var game by remember { mutableStateOf(base?.game?.name.orEmpty()) }
    var appId by remember { mutableStateOf(base?.game?.steamAppId?.toString().orEmpty()) }
    var description by remember { mutableStateOf(base?.description.orEmpty()) }
    var qr by remember { mutableStateOf<String?>(null) }
    var library by remember { mutableStateOf(false) }

    fun meta() = LayoutMeta(
        name = name.trim().ifBlank { profile.name }.take(LayoutFile.MAX_NAME),
        author = author.trim().take(LayoutFile.MAX_AUTHOR),
        game = game.trim().takeIf { it.isNotBlank() }?.let { GameRef(it.take(LayoutFile.MAX_GAME), appId.trim().toIntOrNull()?.takeIf { id -> id in 1..99_999_999 }) },
        target = LayoutTarget.detect(profile.landscape, profile.look),
        device = device,
        aspect = aw / ah,
        description = description.trim().take(LayoutFile.MAX_DESCRIPTION),
        tags = base?.tags.orEmpty(),
    )
    fun file() = LayoutFile.encode(profile, meta().also(onMeta))

    val saveDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(file().toByteArray()) } }.isSuccess
            Toast.makeText(ctx, if (ok) "Saved ${meta().name}" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.systemBarsPadding().padding(s.dp(16)).widthIn(max = s.dp(620)).background(NebulaColors.raised, RoundedCornerShape(s.dp(18)))
                .verticalScroll(rememberScrollState()).padding(s.dp(20)).testTag("share-layout"),
            verticalArrangement = Arrangement.spacedBy(s.dp(12)),
        ) {
            PanelHeader("Share layout", profile.name, onDismiss)
            LayoutThumbnail(profile.landscape.map { it.thumb() }, aw / ah, Modifier.align(Alignment.CenterHorizontally).widthIn(max = s.dp(340)).border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(10))), labels = true)
            Field("Name") { TextInput(name, { name = it.take(LayoutFile.MAX_NAME) }, "Layout name") }
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                Box(Modifier.weight(1f)) { Field("Author") { TextInput(author, { author = it.take(LayoutFile.MAX_AUTHOR) }, "Your name (optional)") } }
                Box(Modifier.weight(1f)) { Field("Game") { TextInput(game, { game = it.take(LayoutFile.MAX_GAME) }, "Any game") } }
            }
            Field("Steam app id", "Optional, from the game's Steam page address (GTA V is 271590).") {
                TextInput(appId, { v -> appId = v.filter { it.isDigit() }.take(8) }, "None")
            }
            Field("Description") { TextInput(description, { description = it.take(LayoutFile.MAX_DESCRIPTION) }, "How it plays, which in-game settings it expects") }
            Text(
                "${LayoutTarget.detect(profile.landscape, profile.look).label} · ${device.label} · ${profile.landscape.size} controls. Positions are shares of the screen, so it fits other devices.",
                style = Nebula.type.label, color = NebulaColors.textMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                SmallAction("Share", Icons.Rounded.Share, {
                    val m = meta()
                    val json = file()
                    val uri = runCatching { writeCache(ctx, LayoutFile.fileName(m), json.toByteArray()) }.getOrNull()
                    val send = Intent(Intent.ACTION_SEND).setType("application/json")
                        .putExtra(Intent.EXTRA_SUBJECT, "${m.name} · Nebula layout")
                        .putExtra(Intent.EXTRA_TEXT, LayoutFile.shareCode(profile, m))
                        .apply { if (uri != null) { putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                    ctx.startActivity(Intent.createChooser(send, "Share layout"))
                })
                SmallAction("Save file", Icons.Rounded.FileDownload, { saveDoc.launch(LayoutFile.fileName(meta())) })
                SmallAction("Copy file", Icons.Rounded.ContentCopy, {
                    copyText(ctx, "Nebula layout", file())
                    Toast.makeText(ctx, "Layout copied", Toast.LENGTH_SHORT).show()
                })
                SmallAction("Copy code", Icons.Rounded.ContentCopy, {
                    copyText(ctx, "Nebula layout", LayoutFile.shareCode(profile, meta().also(onMeta)))
                    Toast.makeText(ctx, "Share code copied: paste it into Import on the other device", Toast.LENGTH_SHORT).show()
                })
                SmallAction("QR code", Icons.Rounded.QrCode2, { qr = LayoutFile.shareCode(profile, meta().also(onMeta)) })
                SmallAction("Preview image", Icons.Rounded.Image, {
                    val m = meta()
                    val png = java.io.ByteArrayOutputStream().also { LayoutThumb.bitmap(profile.landscape.map { it.thumb() }, aw / ah).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    val uri = runCatching { writeCache(ctx, LayoutFile.slug(m.name).ifBlank { "layout" } + ".png", png) }.getOrNull() ?: return@SmallAction
                    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share preview image"))
                })
                SmallAction("Share to library", Icons.Rounded.OpenInNew, { library = true })
            }
        }
    }

    qr?.let { code ->
        QrDialog(code, meta().name) { qr = null }
    }
    if (library) {
        ShareToLibraryDialog(profile, meta().also(onMeta), onDismiss = { library = false })
    }
}

@Composable
private fun QrDialog(code: String, name: String, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val fits = LayoutFile.fitsQr(code)
    val bmp = remember(code) { if (fits) runCatching { qrBitmap(code) }.getOrNull() else null }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        title = { Text(name, style = Nebula.type.heading, color = NebulaColors.text) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), "QR code for $name", Modifier.size(s.dp(210)).testTag("layout-qr"))
                    Text("On the other device: Controls editor → Profiles → Scan QR.", style = Nebula.type.label, color = NebulaColors.textSecondary)
                } else {
                    Text(
                        "This layout is too big for one QR code (${code.length} characters; up to ${LayoutFile.MAX_QR_CHARS}). Share it as a file or copy its code instead.",
                        style = Nebula.type.body, color = NebulaColors.textSecondary,
                    )
                }
            }
        },
        confirmButton = { NebulaButton("Done", onClick = onDismiss) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}

/**
 * Library submissions go through GitHub in the browser: a "new file" page with the layout
 * filled in (a pull request in two taps), or a new issue for people without a fork. Long
 * layouts don't fit in a link; then the JSON goes to the clipboard to paste.
 */
@Composable
private fun ShareToLibraryDialog(profile: ControlsProfile, meta: LayoutMeta, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val json = remember(profile, meta) { LayoutFile.encode(profile, meta) }
    val path = remember(meta) { io.github.f_e_n_y_x.nebula.controls.LibraryPaths.pathFor(meta) }
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        .onFailure { Toast.makeText(ctx, "No browser to open GitHub", Toast.LENGTH_SHORT).show() }
    fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        title = { Text("Share to the layout library", style = Nebula.type.heading, color = NebulaColors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                Text(
                    "Opens ${LibrarySource.REPO.removePrefix("https://")} in your browser with the layout filled in as $path. " +
                        "You'll need a GitHub account; a maintainer checks it before it appears in Browse layouts. Attach the preview image (Share → Preview image) to help review.",
                    style = Nebula.type.body, color = NebulaColors.textSecondary,
                )
            }
        },
        confirmButton = {
            NebulaButton("New file (pull request)", onClick = {
                val url = "${LibrarySource.REPO}/new/main?filename=${enc(path)}&value=${enc(json)}"
                if (url.length > MAX_URL) {
                    copyText(ctx, "Nebula layout", json)
                    Toast.makeText(ctx, "The layout is on the clipboard: paste it into the file", Toast.LENGTH_LONG).show()
                    open("${LibrarySource.REPO}/new/main?filename=${enc(path)}")
                } else {
                    open(url)
                }
                onDismiss()
            })
        },
        dismissButton = {
            NebulaButton("Open an issue", style = ButtonStyle.Secondary, onClick = {
                val title = "Layout: ${meta.name}"
                val body = "**Game:** ${meta.game?.name ?: "any"}\n**Target:** ${meta.target.label}\n**Device:** ${meta.device.label}\n**Path:** `$path`\n\n${meta.description}\n\n```json\n$json\n```\n"
                val url = "${LibrarySource.REPO}/issues/new?labels=layout&title=${enc(title)}&body=${enc(body)}"
                if (url.length > MAX_URL) {
                    copyText(ctx, "Nebula layout", json)
                    Toast.makeText(ctx, "The layout is on the clipboard: paste it into the issue", Toast.LENGTH_LONG).show()
                    open("${LibrarySource.REPO}/issues/new?labels=layout&title=${enc(title)}&body=${enc("**Path:** `$path`\n\nPaste the layout JSON here.\n")}")
                } else {
                    open(url)
                }
                onDismiss()
            })
        },
        shape = RoundedCornerShape(s.dp(18)),
    )
}

/** GitHub (and browsers) reject much longer addresses. */
private const val MAX_URL = 8000

// ---------------------------------------------------------------- browse

/**
 * "Browse layouts": the library's index with a genre filter (chips, from the genre tags and the
 * `genre-*` folders) and a search over games, names and authors. Genre templates come first,
 * then one section per game, each layout with a drawn preview. Picking one downloads it (https,
 * size cap, checksum) and shows [LayoutPreviewDialog].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayoutBrowserDialog(store: ControlsStore, onAdded: (ControlsProfile) -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val prefs = remember { LegacyPrefs(ctx) }
    var url by remember { mutableStateOf(libraryUrl(prefs)) }
    var editingUrl by remember { mutableStateOf(url) }
    var index by remember { mutableStateOf<LibraryIndex?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ProfileImport.Outcome?>(null) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<LibraryEntry?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(url, reload) {
        loading = true; error = null; note = null
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val res = fetcher(ctx).fetch(url, LayoutIndex.MAX_BYTES, cache = true)
                LayoutIndex.parse(res.body) to res
            }
        }
        r.onSuccess { (idx, res) ->
            index = idx
            note = listOfNotNull(
                if (res.stale) "Offline: showing the copy from last time." else null,
                if (idx.skipped > 0) "${idx.skipped} entr${if (idx.skipped == 1) "y" else "ies"} in the library couldn't be read and are hidden." else null,
            ).joinToString(" ").ifBlank { null }
        }.onFailure { e ->
            index = null
            error = when (e) {
                is ControlsFormatException -> e.message
                is java.io.IOException -> if (e.message?.contains("404") == true) "The library isn't there yet ($url)." else "Couldn't reach the layout library: ${e.message ?: e.javaClass.simpleName}"
                else -> "Couldn't reach the layout library: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        loading = false
    }
    LaunchedEffect(pick) {
        val e = pick ?: return@LaunchedEffect
        downloading = e.id
        val r = withContext(Dispatchers.IO) { runCatching { fetcher(ctx).layout(url, e, newProfileId(), System.currentTimeMillis()) } }
        r.onSuccess { parsed -> pending = ProfileImport.Outcome(parsed.profile, ProfileImport.Source.LAYOUT, meta = parsed.meta) }
            .onFailure { ex -> error = (ex as? ControlsFormatException)?.message ?: "Couldn't download ${e.meta.name}: ${ex.message ?: ex.javaClass.simpleName}" }
        downloading = null
        pick = null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(s.dp(12)).background(NebulaColors.raised, RoundedCornerShape(s.dp(18)))
                .padding(s.dp(18)).testTag("layout-browser"),
            verticalArrangement = Arrangement.spacedBy(s.dp(10)),
        ) {
            PanelHeader("Layout library", "Browse layouts", onDismiss)
            TextInput(query, { query = it.take(60) }, "Search games, layouts or authors")
            val genres = remember(index) { index?.genres().orEmpty() }
            if (genres.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("library-genres"),
                    horizontalArrangement = Arrangement.spacedBy(s.dp(6)),
                ) {
                    Chip("All genres", genre == null, Modifier.testTag("genre:all")) { genre = null }
                    genres.forEach { g ->
                        Chip(LayoutGenres.label(g), genre == g, Modifier.testTag("genre:$g")) { genre = if (genre == g) null else g }
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                // Debug builds can use a LAN copy of the library (the owner's sample at :8765).
                Row(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TextInput(editingUrl, { editingUrl = it.take(300) }, "Library index address") }
                    SmallAction("Use", null, {
                        val ok = runCatching { UrlPolicy(true).check(editingUrl) }
                        if (ok.isSuccess) { prefs.prefs.edit().putString(LibrarySource.PREF_KEY, editingUrl.trim()).apply(); url = editingUrl.trim() } else error = ok.exceptionOrNull()?.message
                    })
                    SmallAction("LAN sample", null, { editingUrl = LibrarySource.LAN_SAMPLE; prefs.prefs.edit().putString(LibrarySource.PREF_KEY, LibrarySource.LAN_SAMPLE).apply(); url = LibrarySource.LAN_SAMPLE })
                    SmallAction("GitHub", null, { editingUrl = LibrarySource.DEFAULT_URL; prefs.prefs.edit().remove(LibrarySource.PREF_KEY).apply(); url = LibrarySource.DEFAULT_URL })
                }
            }
            note?.let { Text(it, style = Nebula.type.label, color = NebulaColors.textMuted) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading -> CircularProgressIndicator(color = NebulaColors.accentText, modifier = Modifier.align(Alignment.Center))
                    index == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
                        Text(error ?: "Nothing here yet.", style = Nebula.type.body, color = NebulaColors.textSecondary)
                        NebulaButton("Try again", onClick = { reload++ }, style = ButtonStyle.Secondary)
                    }
                    else -> {
                        val g = genre?.takeIf { it in genres }
                        val sections = remember(index, query, g) { index!!.browse(query, g) }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
                            if (sections.isEmpty()) {
                                val q = query.trim()
                                val what = g?.let { "${LayoutGenres.label(it).lowercase()} layouts" } ?: "layouts"
                                Text(if (q.isEmpty()) "No $what yet." else "No $what match \"$q\".", style = Nebula.type.body, color = NebulaColors.textSecondary)
                            }
                            sections.forEach { sec ->
                                Column(Modifier.testTag("library-section:${sec.title}"), verticalArrangement = Arrangement.spacedBy(s.dp(2))) {
                                    Text(sec.title.uppercase(), style = Nebula.type.eyebrow, color = NebulaColors.accentText)
                                    if (sec.subtitle.isNotBlank()) Text(sec.subtitle, style = Nebula.type.label, color = NebulaColors.textMuted)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
                                    sec.entries.forEach { e -> LibraryCard(e, busy = downloading == e.id) { if (downloading == null) pick = e } }
                                }
                            }
                        }
                    }
                }
            }
            if (index != null && error != null) Text(error!!, style = Nebula.type.label, color = NebulaColors.danger)
        }
    }

    pending?.let { o ->
        LayoutPreviewDialog(o, onAdd = { p ->
            var added: ControlsProfile? = null
            store.update { l -> l.add(p).also { added = it.second }.first }
            pending = null
            added?.let(onAdded)
        }, onDismiss = { pending = null })
    }
}

@Composable
private fun LibraryCard(e: LibraryEntry, busy: Boolean, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    Column(
        Modifier.width(s.dp(220)).nebulaClickable(shape, onClick).background(NebulaColors.surface, shape).border(1.dp, NebulaColors.border, shape)
            .padding(s.dp(10)).testTag("library:${e.id}"),
        verticalArrangement = Arrangement.spacedBy(s.dp(6)),
    ) {
        Box {
            LayoutThumbnail(e.preview.map { it.thumb() }, e.meta.aspect)
            if (busy) CircularProgressIndicator(color = NebulaColors.accentText, modifier = Modifier.align(Alignment.Center).size(s.dp(28)))
        }
        Text(e.meta.name, style = Nebula.type.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(e.meta.author.takeIf { it.isNotBlank() }?.let { "by $it" }, e.meta.target.label, "${e.controls} controls").joinToString(" · "),
            style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (e.meta.description.isNotBlank()) {
            Text(e.meta.description, style = Nebula.type.label, color = NebulaColors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- nebula://layout links

/** Links received by the activity (`nebula://layout?url=…`), waiting to be previewed. Main thread. */
object LayoutInbox {
    val url = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Takes only the link's layout address from a VIEW intent; everything else in it is ignored. */
    fun offer(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val link = io.github.f_e_n_y_x.nebula.controls.LayoutLink.urlOf(intent.dataString) ?: return
        url.value = link
    }
}

/**
 * Shown over any screen when a layout link arrives: downloads it (https only, size-capped, no
 * redirect to http), validates it strictly, and previews it. Nothing is added until Add.
 */
@Composable
fun LayoutLinkHost() {
    val ctx = LocalContext.current
    val pendingUrl by LayoutInbox.url.collectAsState()
    var outcome by remember { mutableStateOf<ProfileImport.Outcome?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val url = pendingUrl
    if (url != null) {
        LaunchedEffect(url) {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val body = fetcher(ctx).fetch(url, LayoutFile.MAX_BYTES).body
                    LayoutFile.parse(body, newProfileId(), System.currentTimeMillis())
                }
            }
            LayoutInbox.url.value = null
            r.onSuccess { outcome = ProfileImport.Outcome(it.profile, ProfileImport.Source.LAYOUT, meta = it.meta) }
                .onFailure { e -> error = (e as? ControlsFormatException)?.message ?: "Couldn't download the layout: ${e.message ?: e.javaClass.simpleName}" }
        }
    }
    outcome?.let { o ->
        LayoutPreviewDialog(o, onAdd = { p ->
            var added: ControlsProfile? = null
            ControlsStore.get(ctx).update { l -> l.add(p).also { added = it.second }.first }
            outcome = null
            Toast.makeText(ctx, "Added ${added?.name}: pick it in the controls editor's Profiles", Toast.LENGTH_LONG).show()
        }, onDismiss = { outcome = null })
    }
    error?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { error = null },
            containerColor = NebulaColors.raised,
            title = { Text("Couldn't open the layout", style = Nebula.type.heading, color = NebulaColors.text) },
            text = { Text(msg, style = Nebula.type.body, color = NebulaColors.textSecondary) },
            confirmButton = { NebulaButton("OK", onClick = { error = null }) },
            shape = RoundedCornerShape(Nebula.scale.dp(18)),
        )
    }
}
