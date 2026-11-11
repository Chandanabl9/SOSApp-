package com.example.sosapp.voice

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sosapp.MainActivity
import com.example.sosapp.EmergencyHelper
import com.example.sosapp.Prefs

class VoiceDetectionService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private lateinit var handler: Handler  // initialize in onCreate

    private val CHANNEL_ID = "voice_detection_channel"
    private val NOTIF_ID = 101

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, VoiceDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Prefs.setVoiceEnabled(context, true)
        }

        fun stop(context: Context) {
            Prefs.setVoiceEnabled(context, false)
            val intent = Intent(context, VoiceDetectionService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())  // Fixed: mainLooper safe now
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        setupSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.isVoiceEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }

    override fun onBind(intent: Intent?) = null

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("VoiceService", "Speech recognition not available")
            stopSelf()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let { checkForHelp(it) }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let { checkForHelp(it) }
                restartListeningWithDelay()
            }
            override fun onError(error: Int) {
                restartListeningWithDelay()
            }
        })
    }

    private fun startListening() {
        try {
            handler.post {
                speechRecognizer?.startListening(recognizerIntent)
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "startListening failed", e)
        }
    }

    private fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) { }
        finally { speechRecognizer = null }
    }

    private fun restartListeningWithDelay(delayMs: Long = 300) {
        handler.postDelayed({
            if (Prefs.isVoiceEnabled(this)) {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.startListening(recognizerIntent)
                } catch (e: Exception) {
                    Log.e("VoiceService", "restart error", e)
                }
            } else stopSelf()
        }, delayMs)
    }

    private fun checkForHelp(matches: List<String>) {
        for (match in matches) {
            if (match.contains("help", ignoreCase = true)) {
                Log.i("VoiceService", "Detected: $match")
                EmergencyHelper.triggerKeywordSOS(this)
                break
            }
        }
    }

    // Notification
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chan = NotificationChannel(CHANNEL_ID, "Voice detection", NotificationManager.IMPORTANCE_LOW)
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            manager.createNotificationChannel(chan)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice detection active")
            .setContentText("Listening for the keyword 'HELP'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
