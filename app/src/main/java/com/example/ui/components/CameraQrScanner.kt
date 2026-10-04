package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Robust CameraX + ZXing QR Code Scanner.
 * - Handles camera rotation (0, 90, 180, 270 degrees)
 * - Corrects row-stride mismatch on all Android camera sensors
 * - Dedicated QR_CODE format hint with TRY_HARDER
 * - Dual Hybrid & Global Histogram binarizers for low-contrast screen scans
 * - Gallery / Screenshot QR image picker
 */
@Composable
fun CameraQrScanner(
    onQrCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Photo picker to scan QR codes sent via Messenger / WhatsApp / Viber
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val scannedText = decodeQrFromUri(context, uri)
            if (!scannedText.isNullOrBlank()) {
                onQrCodeScanned(scannedText)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = modifier) {
        if (!hasCameraPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .testTag("camera_permission_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(ElectricBlue.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Camera Access Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Grant camera permission to scan the Parent Controller's QR code directly.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Enable Camera")
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("From Photo")
                        }
                    }
                }
            }
        } else {
            // Real Live Camera Viewfinder with ZXing Stride & Rotation Normalization
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
            var isScanningActive by remember { mutableStateOf(true) }

            DisposableEffect(Unit) {
                onDispose {
                    cameraExecutor.shutdown()
                }
            }

            // Animated scanning laser line
            val transition = rememberInfiniteTransition(label = "scan_laser")
            val laserOffset by transition.animateFloat(
                initialValue = 0f,
                targetValue = 260f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "laser_y"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, CyanAccent, RoundedCornerShape(20.dp))
                    .testTag("camera_qr_scanner_viewfinder")
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val reader = MultiFormatReader().apply {
                                setHints(
                                    mapOf(
                                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                        DecodeHintType.TRY_HARDER to true,
                                        DecodeHintType.CHARACTER_SET to "UTF-8"
                                    )
                                )
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (!isScanningActive) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                try {
                                    val scannedText = decodeImageProxy(imageProxy, reader)
                                    if (!scannedText.isNullOrBlank()) {
                                        isScanningActive = false
                                        Log.d("CameraQrScanner", "Scanned QR Code Successfully: $scannedText")
                                        previewView.post {
                                            onQrCodeScanned(scannedText.trim())
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraQrScanner", "QR parse error: ${e.message}")
                                } finally {
                                    imageProxy.close()
                                }
                            }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("CameraQrScanner", "Camera bind failed: ${e.message}")
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Scanning Target Reticle Overlay
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .align(Alignment.Center)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                )

                // Laser scan indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(3.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = laserOffset.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, CyanAccent, Color.White, CyanAccent, Color.Transparent)
                            )
                        )
                )

                // Bottom banner instructions + Gallery Picker Shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Align Parent QR code inside frame",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Photo", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Extracts Y-plane bytes accounting for row-stride and rotation, then decodes using ZXing.
 */
private fun decodeImageProxy(image: ImageProxy, reader: MultiFormatReader): String? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height

    // Pack bytes tightly row by row to eliminate stride artifacts
    val yData = ByteArray(width * height)
    for (row in 0 until height) {
        buffer.position(row * rowStride)
        buffer.get(yData, row * width, width)
    }

    val rotation = image.imageInfo.rotationDegrees
    val (rotatedData, dims) = rotateYuv(yData, width, height, rotation)
    val rotWidth = dims.first
    val rotHeight = dims.second

    val source = PlanarYUVLuminanceSource(rotatedData, rotWidth, rotHeight, 0, 0, rotWidth, rotHeight, false)

    // Try Hybrid Binarizer
    val hybridBitmap = BinaryBitmap(HybridBinarizer(source))
    try {
        val result = reader.decodeWithState(hybridBitmap)
        return result.text
    } catch (_: Exception) {
        reader.reset()
    }

    // Fallback: Global Histogram Binarizer (for screens with glare or dim lighting)
    val globalBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
    try {
        val result = reader.decodeWithState(globalBitmap)
        return result.text
    } catch (_: Exception) {
        reader.reset()
    }

    return null
}

/**
 * Rotates YUV data by 0, 90, 180, or 270 degrees.
 */
private fun rotateYuv(data: ByteArray, width: Int, height: Int, rotation: Int): Pair<ByteArray, Pair<Int, Int>> {
    if (rotation == 0) return Pair(data, Pair(width, height))
    val rotated = ByteArray(data.size)
    return when (rotation) {
        90 -> {
            var k = 0
            for (x in 0 until width) {
                for (y in height - 1 downTo 0) {
                    rotated[k++] = data[y * width + x]
                }
            }
            Pair(rotated, Pair(height, width))
        }
        270 -> {
            var k = 0
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    rotated[k++] = data[y * width + x]
                }
            }
            Pair(rotated, Pair(height, width))
        }
        180 -> {
            for (i in data.indices) {
                rotated[i] = data[data.size - 1 - i]
            }
            Pair(rotated, Pair(width, height))
        }
        else -> Pair(data, Pair(width, height))
    }
}

/**
 * Decodes QR code from a picked gallery image Uri.
 */
private fun decodeQrFromUri(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        if (bitmap == null) return null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
        try {
            reader.decodeWithState(binaryBitmap).text
        } catch (_: Exception) {
            reader.reset()
            val globalBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            reader.decodeWithState(globalBitmap).text
        }
    } catch (e: Exception) {
        Log.e("CameraQrScanner", "Failed to decode QR from photo: ${e.message}")
        null
    }
}
