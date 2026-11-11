package com.example.sosapp.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first


class AudioRecorder(private val context: Context) {

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var recorder: AudioRecord? = null

    fun hasPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(onData: (ShortArray) -> Unit) {
        if (!hasPermission()) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            recorder?.startRecording()

        } catch (e: SecurityException) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun readAudioData(onData: (ShortArray) -> Unit) {
        val buffer = ShortArray(bufferSize)

        withContext(Dispatchers.IO) {
            while (isActive && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val result = recorder?.read(buffer, 0, buffer.size)

                if (result != null && result > 0) {
                    onData(buffer.copyOf())
                }
            }
        }
    }

    fun stopRecording() {
        recorder?.stop()
        recorder?.release()
        recorder = null
    }
}
