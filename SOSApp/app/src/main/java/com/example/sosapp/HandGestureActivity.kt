package com.example.sosapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
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
import com.example.sosapp.ml.GestureClassifier
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class HandGestureActivity : ComponentActivity() {

    private lateinit var classifier: GestureClassifier
    private lateinit var handLandmarker: HandLandmarker
    private var sosTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        classifier = GestureClassifier(this)
        setupMediaPipe()

        val req = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            req.launch(Manifest.permission.CAMERA)
        }

        setContent { HandGestureUI() }
    }

    private fun setupMediaPipe() {
        val base = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setNumHands(1)
            .setRunningMode(RunningMode.IMAGE)
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    @Composable
    fun HandGestureUI() {
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

            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                val bitmap = imageProxy.toBitmap()
                val mpImage = BitmapImageBuilder(bitmap).build()

                val result = try {
                    handLandmarker.detect(mpImage)
                } catch (e: Exception) {
                    Log.e("HAND", "ERROR: $e")
                    imageProxy.close()
                    return@setAnalyzer
                }

                if (result != null) handleResult(result, onSOS)
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

    private fun handleResult(result: HandLandmarkerResult, onSOS: () -> Unit) {
        if (result.landmarks().isEmpty()) return

        val lm = result.landmarks()[0]
        val arr = FloatArray(63)
        var i = 0
        for (p in lm) {
            arr[i++] = p.x()
            arr[i++] = p.y()
            arr[i++] = p.z()
        }

        val probability = classifier.classifyLandmarks(arr)
        Log.d("GESTURE", "HELP probability = $probability")

        if (probability > 0.85 && !sosTriggered) {
            sosTriggered = true
            runOnUiThread {
                onSOS()
                triggerEmergency()
            }
        }
    }

    private fun triggerEmergency() {
        CoroutineScope(Dispatchers.Main).launch {
            val contacts = withContext(Dispatchers.IO) {
                ContactDataStore.getContacts(this@HandGestureActivity).first()
            }

            if (contacts.isNotEmpty()) {
                LocationHelper.getCurrentLocation(this@HandGestureActivity) { lat, lon ->
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
                    // Call the first contact
                    val callIntent = Intent(Intent.ACTION_CALL)
                        .setData(android.net.Uri.parse("tel:${contact.phoneNumber}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        startActivity(callIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("HandGestureSOS", "Failed to contact ${contact.phoneNumber}", e)
            }
        }
    }
}

/** Safe method to convert ImageProxy to Bitmap */
fun ImageProxy.toBitmap(): Bitmap {
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

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val jpegBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}
