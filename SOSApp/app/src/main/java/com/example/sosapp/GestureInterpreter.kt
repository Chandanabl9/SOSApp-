package com.yourapp.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GestureInterpreter(context: Context) {

    private val interpreter: Interpreter

    init {
        val assetFile = context.assets.open("gesture.tflite")
        val modelBytes = assetFile.readBytes()
        val buffer = ByteBuffer.allocateDirect(modelBytes.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(modelBytes)
        interpreter = Interpreter(buffer)
    }

    fun predict(input: FloatArray): Float {
        val inputBuffer = ByteBuffer.allocateDirect(63 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (v in input) inputBuffer.putFloat(v)

        val output = Array(1) { FloatArray(1) }
        interpreter.run(inputBuffer, output)
        return output[0][0]
    }
}
