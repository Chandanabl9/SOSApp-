package com.example.sosapp.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GestureClassifier(context: Context) {

    private val interpreter: Interpreter

    init {
        val modelBytes = context.assets.open("gesture.tflite").readBytes()
        val buffer = ByteBuffer.allocateDirect(modelBytes.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(modelBytes)
        buffer.rewind()

        interpreter = Interpreter(buffer)
    }

    fun classifyLandmarks(landmarks63: FloatArray): Float {
        if (landmarks63.size != 63) return 0f

        val input = ByteBuffer.allocateDirect(63 * 4).order(ByteOrder.nativeOrder())
        for (v in landmarks63) input.putFloat(v)
        input.rewind()

        val output = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())

        interpreter.run(input, output)

        output.rewind()
        return output.float
    }
}
