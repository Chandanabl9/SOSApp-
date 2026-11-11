package com.example.sosapp

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.content.res.AssetFileDescriptor

class AudioHelpDetector(
    private val context: Context,
    modelAssetName: String = "audio_help_model.tflite"
) {

    private val interpreter: Interpreter

    init {
        val buffer = loadModelFile(modelAssetName)
        interpreter = Interpreter(buffer)
    }

    fun close() {
        interpreter.close()
    }

    /**
     * Predict from spectrogram.
     * spectrogram: [nMels][framesWindow]
     * Returns: Pair(predictedClassIndex, scoresArray)
     */
    fun predictFromSpectrogram(spectrogram: Array<FloatArray>): Pair<Int, FloatArray> {
        val nMels = spectrogram.size
        val frames = spectrogram[0].size

        // Prepare input [1, nMels, frames, 1]
        val input = Array(1) { Array(nMels) { Array(frames) { FloatArray(1) } } }
        for (m in 0 until nMels) {
            for (t in 0 until frames) {
                input[0][m][t][0] = spectrogram[m][t]
            }
        }

        // Output: 3-class probabilities
        val output = Array(1) { FloatArray(3) }

        interpreter.run(input, output)

        // Find the class with the highest probability
        val scores = output[0]
        val bestClass = scores.indices.maxByOrNull { scores[it] } ?: 0

        return Pair(bestClass, scores)
    }

    /**
     * Load TFLite vosk-model-small-en-us-0.15 from assets
     */
    private fun loadModelFile(name: String): MappedByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(name)
        val fis = FileInputStream(afd.fileDescriptor)
        val fc = fis.channel
        return fc.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }
}
