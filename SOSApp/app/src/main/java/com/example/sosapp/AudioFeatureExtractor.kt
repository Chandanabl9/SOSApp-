package com.example.sosapp

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.absoluteValue


class AudioFeatureExtractor(
    private val sampleRate: Int = 16000,
    private val nMels: Int = 64,
    private val nFft: Int = 512,
    private val hopLength: Int = 160
) {

    fun extractMelSpectrogram(signal: FloatArray): Array<Array<FloatArray>> {
        val stft = computeSTFT(signal)
        val melFilter = createMelFilter()
        val melSpectrogram = applyMelFilter(stft, melFilter)
        return normalize(melSpectrogram)
    }

    // -----------------------------
    // Short-Time Fourier Transform
    // -----------------------------
    private fun computeSTFT(signal: FloatArray): Array<FloatArray> {
        val frames = (signal.size - nFft) / hopLength
        val stft = Array(frames) { FloatArray(nFft / 2 + 1) }

        for (i in 0 until frames) {
            val start = i * hopLength
            val windowed = FloatArray(nFft)

            for (j in 0 until nFft) {
                if (start + j < signal.size)
                    windowed[j] = signal[start + j] * hann(j, nFft)
            }

            val spectrum = fftMagnitude(windowed)
            stft[i] = spectrum
        }

        return stft
    }

    private fun hann(i: Int, n: Int): Float =
        (0.5 * (1 - cos(2 * PI * i / (n - 1)))).toFloat()

    // Very small FFT implementation
    private fun fftMagnitude(x: FloatArray): FloatArray {
        val n = x.size
        val real = x.copyOf()
        val imag = FloatArray(n)

        var step = 1
        while (step < n) {
            val jump = step * 2
            val delta = (-PI / step)
            var wReal = 1.0
            var wImag = 0.0
            val sReal = cos(delta)
            val sImag = sin(delta)

            for (i in 0 until step) {
                var j = i
                while (j < n) {
                    val k = j + step
                    val tReal = (wReal * real[k] - wImag * imag[k]).toFloat()
                    val tImag = (wReal * imag[k] + wImag * real[k]).toFloat()

                    real[k] = (real[j] - tReal)
                    imag[k] = (imag[j] - tImag)
                    real[j] = (real[j] + tReal)
                    imag[j] = (imag[j] + tImag)

                    j += jump
                }

                val temp = wReal
                wReal = temp * sReal - wImag * sImag
                wImag = temp * sImag + wImag * sReal
            }

            step = jump
        }

        val mag = FloatArray(n / 2 + 1)
        for (i in mag.indices)
            mag[i] = kotlin.math.sqrt(real[i] * real[i] + imag[i] * imag[i])

        return mag
    }

    // -----------------------------
    // Mel filter bank
    // -----------------------------
    private fun createMelFilter(): Array<FloatArray> {
        val melFilter = Array(nMels) { FloatArray(nFft / 2 + 1) }

        for (m in 0 until nMels) {
            for (k in 0 until melFilter[0].size) {
                melFilter[m][k] = 1f / (1 + (k - m * 2).absoluteValue)
            }
        }
        return melFilter
    }

    private fun applyMelFilter(
        stft: Array<FloatArray>,
        melFilter: Array<FloatArray>
    ): Array<FloatArray> {

        val mel = Array(nMels) { FloatArray(stft.size) }

        for (m in 0 until nMels) {
            for (t in stft.indices) {
                var sum = 0f
                for (f in melFilter[0].indices) {
                    sum += stft[t][f] * melFilter[m][f]
                }
                mel[m][t] = log10(sum + 1e-6f)
            }
        }
        return mel
    }

    private fun normalize(mel: Array<FloatArray>): Array<Array<FloatArray>> {
        val out = Array(1) { Array(nMels) { FloatArray(101) } }

        for (i in 0 until nMels) {
            val row = mel[i]
            val slice = row.take(101).toFloatArray()
            out[0][i] = slice
        }

        return out
    }
}
