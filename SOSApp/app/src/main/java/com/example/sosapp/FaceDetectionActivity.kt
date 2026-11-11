package com.example.sosapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.Executors

class FaceDetectionActivity : ComponentActivity() {

    private lateinit var faceLandmarker: FaceLandmarker
    private var sosTriggered = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Camera permission
        val req = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            req.launch(Manifest.permission.CAMERA)
        }

        setupMediaPipe()
        setContent { FaceDetectionUI() }
    }

    private fun setupMediaPipe() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(this, options)
    }

    @Composable
    fun FaceDetectionUI() {
        var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
        var sosVisible by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx -> PreviewView(ctx).also { previewViewRef = it } },
                modifier = Modifier.fillMaxSize()
            )

            if (sosVisible) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x55000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SOS Triggered!",
                        color = Color.Red,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }

        LaunchedEffect(previewViewRef) {
            previewViewRef?.let { startCamera(it) { sosVisible = true } }
        }
    }

    private fun startCamera(previewView: PreviewView, onSOS: () -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val bitmap = imageProxy.toBitmap()
                val mpImage = BitmapImageBuilder(bitmap).build()

                val result: FaceLandmarkerResult = try {
                    faceLandmarker.detect(mpImage)
                } catch (e: Exception) {
                    Log.e("FACE", "Error detecting face: $e")
                    imageProxy.close()
                    return@setAnalyzer
                }

                handleFaceResult(result, onSOS)
                imageProxy.close()
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleFaceResult(result: FaceLandmarkerResult, onSOS: () -> Unit) {
        if (result.faceLandmarks().isEmpty()) return
        if (!sosTriggered) {
            sosTriggered = true
            runOnUiThread {
                onSOS()
                triggerEmergency()
            }
        }
    }

    private fun triggerEmergency() {
        CoroutineScope(Dispatchers.Main).launch {
            // Correctly get the first emitted list from Flow
            val contactsFlow = ContactDataStore.getContacts(this@FaceDetectionActivity)
            val contacts = contactsFlow.first() // Collect first emitted value

            if (contacts.isNotEmpty()) {
                LocationHelper.getCurrentLocation(this@FaceDetectionActivity) { lat, lon ->
                    sendSOSWithCallAndSMS(contacts, lat, lon)
                }
            }
        }
    }

    private fun sendSOSWithCallAndSMS(contacts: List<EmergencyContact>, lat: Double, lon: Double) {
        val locationUrl = "https://maps.google.com/?q=$lat,$lon"
        val smsManager = SmsManager.getDefault()

        contacts.forEachIndexed { index, contact ->
            try {
                smsManager.sendTextMessage(contact.phoneNumber, null, "HELP! My location: $locationUrl", null, null)
                if (index == 0) {
                    val callIntent = Intent(Intent.ACTION_CALL)
                        .setData(android.net.Uri.parse("tel:${contact.phoneNumber}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        startActivity(callIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("FaceDetectionSOS", "Failed to contact ${contact.phoneNumber}", e)
            }
        }
    }
}

/** Convert ImageProxy to Bitmap safely */
fun ImageProxy.toBitmapFromYuv(): android.graphics.Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val jpegBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}
