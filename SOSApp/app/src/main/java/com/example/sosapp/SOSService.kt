package com.example.sosapp

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import kotlin.math.*

// Service for voice detection and SOS trigger
class SOSService : Service() {

    companion object {
        private const val TAG = "SOSService"
        const val EXTRA_ENABLE_VOICE = "extra_enable_voice"
        const val EXTRA_TRIGGER_SOS = "extra_trigger_sos"

        // Audio detection parameters
        private const val SAMPLE_RATE = 16000
        private const val AUDIO_SECONDS = 1
        private const val CHANNELS = android.media.AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = android.media.AudioFormat.ENCODING_PCM_16BIT

        private const val IN_ROWS = 40
        private const val IN_COLS = 44
        private const val IN_DEPTH = 1

        private const val MODEL_FILE = "audio_help_model.tflite"
        private const val NUM_CLASSES = 3
        private const val HELP_CLASS_INDEX = 2
        private const val HELP_THRESHOLD = 0.75f

        private const val TRIGGER_COOLDOWN_MS = 30000L
    }

    private var interpreter: Interpreter? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var listeningJob: Job? = null
    private var audioRecord: android.media.AudioRecord? = null
    private val frameBuffer = ArrayDeque<FloatArray>()
    private var lastTriggerTime = 0L

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        loadModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val enableVoice = intent?.getBooleanExtra(EXTRA_ENABLE_VOICE, true) ?: true
        val triggerSOS = intent?.getBooleanExtra(EXTRA_TRIGGER_SOS, false) ?: false

        if (enableVoice) startListening() else stopListening()
        if (triggerSOS) triggerSOSManually() // hand gesture or manual trigger
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    // ---- Notification ----
    private fun startForegroundNotification() {
        val channelId = "sos_voice_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId, "SOS Voice Detection",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SOS Listening")
            .setContentText("Voice detection active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(99, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(99, notification)
        }
    }

    // ---- Load TFLite Model ----
    private fun loadModel() {
        try {
            val modelFile = FileUtil.loadMappedFile(this, MODEL_FILE)
            interpreter = Interpreter(modelFile, Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            })
            Log.d(TAG, "Model loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vosk-model-small-en-us-0.15", e)
        }
    }

    // ---- Audio Listening ----
    private fun startListening() {
        if (listeningJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        listeningJob = serviceScope.launch {
            val samples = SAMPLE_RATE * AUDIO_SECONDS
            var minBuf = android.media.AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
            if (minBuf < samples) minBuf = samples * 2

            audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNELS, ENCODING, minBuf
            )

            if (audioRecord?.state != android.media.AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return@launch
            }

            val buffer = ShortArray(samples)
            audioRecord!!.startRecording()
            Log.d(TAG, "Audio recording started")

            while (isActive) {
                val read = audioRecord!!.read(buffer, 0, samples)
                if (read <= 0) continue
                val floatBuffer = FloatArray(read) { i -> buffer[i] / 32768f }
                val mel = melSpec(floatBuffer)

                synchronized(frameBuffer) {
                    frameBuffer.clear()
                    for (c in 0 until IN_COLS) {
                        val colFrame = FloatArray(IN_ROWS)
                        for (m in 0 until IN_ROWS) colFrame[m] = mel[m][c]
                        frameBuffer.add(colFrame)
                    }
                }

                runInference()
                yield()
            }
        }
    }

    private fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        frameBuffer.clear()
    }

    // ---- Inference ----
    private fun runInference() {
        val intr = interpreter ?: return
        val ready = synchronized(frameBuffer) { frameBuffer.size == IN_COLS }
        if (!ready) return

        val input = Array(1) { Array(IN_ROWS) { Array(IN_COLS) { FloatArray(IN_DEPTH) } } }
        synchronized(frameBuffer) {
            val list = frameBuffer.toList()
            for (c in 0 until IN_COLS) {
                for (r in 0 until IN_ROWS) {
                    input[0][r][c][0] = list[c][r]
                }
            }
        }

        val output = Array(1) { FloatArray(NUM_CLASSES) }
        try {
            intr.run(input, output)
            val helpProb = output[0][HELP_CLASS_INDEX]
            if (helpProb >= HELP_THRESHOLD && System.currentTimeMillis() - lastTriggerTime > TRIGGER_COOLDOWN_MS) {
                lastTriggerTime = System.currentTimeMillis()
                onHelpDetected()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
        }
    }

    // ---- SOS Actions ----
    private fun onHelpDetected() {
        Log.w(TAG, "HELP detected! Sending SOS...")
        serviceScope.launch(Dispatchers.Main) { sendSOS() }
    }

    private fun triggerSOSManually() {
        Log.w(TAG, "Manual SOS triggered! Sending SOS...")
        serviceScope.launch(Dispatchers.Main) { sendSOS() }
    }

    private suspend fun sendSOS() {
        val contacts = getContacts()
        if (contacts.isEmpty()) return

        val location = getCurrentLocation() ?: return

        contacts.forEachIndexed { index, number ->
            try {
                val sms = SmsManager.getDefault()
                sms.sendTextMessage(number, null, "HELP! My location: $location", null, null)
                Log.d(TAG, "SMS sent to $number")

                if (index == 0) callContact(number) // First contact gets a call
            } catch (e: Exception) {
                Log.e(TAG, "Failed SMS/Call to $number", e)
            }
        }
    }

    private fun getContacts(): List<String> {
        val prefs = getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)
        val s = prefs.getString("emergency_contacts", null) ?: return emptyList()
        return s.split(",").map { it.trim() }
    }

    private suspend fun getCurrentLocation(): String? {
        return withContext(Dispatchers.Main) {
            try {
                if (ContextCompat.checkSelfPermission(this@SOSService, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) return@withContext null

                val client = LocationServices.getFusedLocationProviderClient(this@SOSService)
                val loc = kotlinx.coroutines.suspendCancellableCoroutine<android.location.Location?> { cont ->
                    client.lastLocation.addOnSuccessListener { location -> cont.resume(location, null) }
                        .addOnFailureListener { cont.resume(null, null) }
                }
                loc?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }
            } catch (e: Exception) {
                Log.e(TAG, "Location error", e)
                null
            }
        }
    }

    private fun callContact(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Call failed", e)
        }
    }

    // ---- Mel Spectrogram ----
    private fun melSpec(audio: FloatArray): Array<FloatArray> {
        val frameSize = 400
        val hop = 160
        val frames = ArrayList<FloatArray>()
        var i = 0
        while (i + frameSize <= audio.size && frames.size < IN_COLS) {
            frames.add(audio.copyOfRange(i, i + frameSize))
            i += hop
        }
        while (frames.size < IN_COLS) frames.add(FloatArray(frameSize))

        val mel = Array(IN_ROWS) { FloatArray(IN_COLS) }
        for (c in 0 until IN_COLS) {
            val frame = frames[c]
            val ham = FloatArray(frameSize) { j ->
                frame[j] * (0.54f - 0.46f * cos(2 * Math.PI * j / (frameSize - 1))).toFloat()
            }
            val (re, im) = rfft(ham)
            val power = FloatArray(re.size) { k -> re[k] * re[k] + im[k] * im[k] }
            val filt = melFilter(power, SAMPLE_RATE, IN_ROWS)
            for (m in 0 until IN_ROWS)
                mel[m][c] = kotlin.math.log10(filt[m] + 1e-6f)
        }
        return mel
    }

    private fun rfft(frame: FloatArray): Pair<FloatArray, FloatArray> {
        val n = frame.size
        val re = frame.copyOf()
        val im = FloatArray(n)
        return Pair(re, im)
    }

    private fun melFilter(power: FloatArray, sr: Int, numMel: Int): FloatArray {
        val mel = FloatArray(numMel)
        val n = power.size
        val step = n / numMel
        for (i in 0 until numMel) {
            for (j in 0 until step) mel[i] += power[i * step + j]
        }
        return mel
    }
}
