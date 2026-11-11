package com.example.sosapp
import kotlin.math.log10

class MFCCExtractor {

    fun extractMFCC(signal: FloatArray): FloatArray {
        // Very simple placeholder MFCC
        // Replace with full MFCC calculator if needed

        val energy = signal.sumOf { (it * it).toDouble() }
        val logEnergy = 10 * log10(energy)

        return floatArrayOf(logEnergy.toFloat(), 0f, 0f, 0f, 0f)
    }
}
