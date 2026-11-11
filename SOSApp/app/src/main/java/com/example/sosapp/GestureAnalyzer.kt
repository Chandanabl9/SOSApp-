package com.example.sosapp

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GestureAnalyzer(
    context: Context,
    private val onHelpDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    private val interpreter: Interpreter

    init {
        // Load vosk-model-small-en-us-0.15 as ByteBuffer
        val modelBytes = context.assets.open("gesture.tflite").readBytes()
        val byteBuffer = ByteBuffer.allocateDirect(modelBytes.size)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.put(modelBytes)
        byteBuffer.rewind()

        interpreter = Interpreter(byteBuffer)
    }

    override fun analyze(image: ImageProxy) {
        val bitmap = imageProxyToBitmap(image)

        if (bitmap != null) {
            val input = bitmapToInput(bitmap)
            val output = Array(1) { FloatArray(1) }

            interpreter.run(input, output)

            // If gesture detected with 80% confidence
            if (output[0][0] > 0.80f) {
                onHelpDetected()
            }
        }

        image.close()
    }

    private fun bitmapToInput(original: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(original, 224, 224, true)

        val input = ByteBuffer.allocateDirect(224 * 224 * 3 * 4)
        input.order(ByteOrder.nativeOrder())

        val pixels = IntArray(224 * 224)
        resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)

        for (px in pixels) {
            input.putFloat(((px shr 16) and 0xFF) / 255f) // R
            input.putFloat(((px shr 8) and 0xFF) / 255f)  // G
            input.putFloat((px and 0xFF) / 255f)          // B
        }

        return input
    }
}
