package io.github.f_e_n_y_x.nebula.controls.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.f_e_n_y_x.nebula.controls.LayoutFile
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decodes QR codes from CameraX luminance frames with ZXing (no Play services): only the Y
 * plane is read, and only codes that hold a Nebula layout share code are reported.
 */
internal class LayoutQrAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
    private val done = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        try {
            if (done.get()) return
            val plane = image.planes[0]
            val buf = plane.buffer
            val stride = plane.rowStride
            val w = image.width
            val h = image.height
            val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
            val source = PlanarYUVLuminanceSource(bytes, stride, h, 0, 0, w, h, false)
            val text = try {
                reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
            } catch (e: NotFoundException) {
                null
            } catch (e: com.google.zxing.ChecksumException) {
                null
            } catch (e: com.google.zxing.FormatException) {
                null
            } finally {
                reader.reset()
            }
            if (text != null && LayoutFile.looksLikeShareCode(text) && done.compareAndSet(false, true)) onCode(text)
        } finally {
            image.close()
        }
    }
}

/**
 * Scans a layout QR code with the back camera. The camera permission is asked for here, the
 * first time; nothing is recorded or kept. [onCode] gets the share code (validated by the caller).
 */
@Composable
internal fun QrScanDialog(onCode: (String) -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; denied = !ok }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    val codeNow by rememberUpdatedState(onCode)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.systemBarsPadding().padding(s.dp(16)).widthIn(max = s.dp(520)).background(NebulaColors.raised, RoundedCornerShape(s.dp(18))).padding(s.dp(18)),
            verticalArrangement = Arrangement.spacedBy(s.dp(12)),
        ) {
            PanelHeader("Import", "Scan a layout QR code", onDismiss)
            when {
                granted -> CameraPreview(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(s.dp(14))).border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(14)))) { codeNow(it) }
                denied -> Text("Nebula needs the camera to read the QR code. You can also paste the layout's share code instead (Import → Paste).", style = Nebula.type.body, color = NebulaColors.textSecondary)
                else -> Text("Waiting for camera permission…", style = Nebula.type.body, color = NebulaColors.textSecondary)
            }
            Text("Point the camera at the QR code from Share → QR code on the other device.", style = Nebula.type.label, color = NebulaColors.textMuted)
            NebulaButton("Cancel", onClick = onDismiss, style = ButtonStyle.Ghost)
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier, onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    val view = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, LayoutQrAnalyzer { code -> main.execute { onCode(code) } }) }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, main)
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    Box(modifier) { AndroidView({ view }, Modifier.fillMaxWidth().aspectRatio(1f)) }
}
