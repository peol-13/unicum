package io.github.peol13.unicum.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.github.peol13.unicum.ui.theme.UnicumBackground
import io.github.peol13.unicum.ui.theme.UnicumDark
import io.github.peol13.unicum.ui.theme.UnicumPrimary
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.compose.runtime.rememberUpdatedState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import io.github.peol13.unicum.ui.theme.UnicumType

/** Schermata della fotocamera che legge un codice a barre (EAN/UPC) e lo restituisce una sola volta. */
@Composable
fun BarcodeScannerDialog(onCode: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).height(520.dp),
            shape = RoundedCornerShape(28.dp),
            color = UnicumBackground
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        "Scansiona il codice a barre", color = UnicumDark, style = UnicumType.Heading,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(end = 40.dp).align(Alignment.CenterStart)
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Chiudi", tint = UnicumDark) }
                }
                Spacer(Modifier.height(10.dp))
                if (granted) {
                    Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
                        CameraPreview(onCode)
                        Box(
                            Modifier.align(Alignment.Center).fillMaxWidth(0.8f).height(130.dp)
                                .border(2.dp, UnicumPrimary, RoundedCornerShape(16.dp))
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Inquadra il codice del prodotto: si legge da solo.",
                        color = UnicumDark.copy(alpha = .6f), style = UnicumType.Detail,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (denied) "Serve il permesso della fotocamera per scansionare. Puoi concederlo dalle impostazioni dell'app."
                            else "Attendo il permesso della fotocamera…",
                            color = UnicumDark.copy(alpha = .7f), style = UnicumType.Body, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
        )
    }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    // L'analisi gira su un altro thread: il flag atomico garantisce che il codice arrivi una volta sola
    val done = remember { AtomicBoolean(false) }
    val currentOnCode by rememberUpdatedState(onCode)

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || done.get()) {
                    proxy.close()
                } else {
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes ->
                            val value = codes.firstNotNullOfOrNull { it.rawValue }
                            if (value != null && done.compareAndSet(false, true)) currentOnCode(value)
                        }
                        .addOnCompleteListener { proxy.close() }
                }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}
