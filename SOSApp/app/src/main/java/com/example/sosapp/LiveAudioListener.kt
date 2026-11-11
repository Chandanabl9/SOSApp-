package com.example.sosapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.log10
import kotlin.math.max

class LiveAudioListener(
    private val context: Context,
    private val sampleRate: Int,
    private val bufferSize: Int,
    private val bufferOverlap: Int,
    private val nMels: Int,
    private val framesWindow: Int,
    private val onSpectrogramReady: (Array<FloatArray>) -> Unit
) {

    private var recorder: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val audioBuffer = FloatArray(bufferSize)
    private val melSpectrogram = Array(framesWindow) { FloatArray(nMels) }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LiveAudioListener", "RECORD_AUDIO permission denied!")
            return
        }

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            recorder?.startRecording()

            scope.launch {
                val shortArray = ShortArray(bufferSize)
                while (isActive) {
                    val read = recorder?.read(shortArray, 0, shortArray.size) ?: 0
                    if (read > 0) {
                        for (i in 0 until read) audioBuffer[i] = shortArray[i] / 32768f
                        val frame = computeMelFrame(audioBuffer)

                        for (i in 0 until framesWindow - 1) melSpectrogram[i] = melSpectrogram[i + 1]
                        melSpectrogram[framesWindow - 1] = frame

                        onSpectrogramReady(melSpectrogram)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("LiveAudioListener", "AudioRecord start failed", e)
        } catch (e: Exception) {
            Log.e("LiveAudioListener", "Unexpected error", e)
        }
    }

    fun stop() {
        recorder?.stop()
        recorder?.release()
        recorder = null
        scope.cancel()
    }

    private fun computeMelFrame(frame: FloatArray): FloatArray {
        val result = FloatArray(nMels)
        for (i in 0 until nMels) {
            val energy = frame.sumOf { (it * it).toDouble() } / (i + 1)
            result[i] = (10 * log10(max(energy, 1e-8))).toFloat()
        }
        return result
    }
}
