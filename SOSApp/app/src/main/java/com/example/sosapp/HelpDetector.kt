package com.example.sosapp

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.content.res.AssetFileDescriptor
import java.io.FileInputStream

class HelpDetector(context: Context) {

    private var interpreter: Interpreter

    init {
        val tfliteModel = loadModelFile(context, "help_detector.tflite")
        interpreter = Interpreter(tfliteModel)
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val channel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predict(mfccInput: Array<Array<FloatArray>>): Float {
        val output = Array(1) { FloatArray(1) }
        interpreter.run(mfccInput, output)
        return output[0][0]
    }
}
