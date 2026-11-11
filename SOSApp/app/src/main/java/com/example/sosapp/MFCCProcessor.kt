package com.example.sosapp

import kotlin.math.*

class MFCCProcessor(
    private val sampleRate: Int = 16000,
    private val numCoefficients: Int = 64,
    private val numFilters: Int = 101
) {

    fun computeMFCC(signal: ShortArray): Array<FloatArray> {

        val frameSize = 512
        val frameStep = 256

        val frames = framing(signal, frameSize, frameStep)
        val window = hammingWindow(frameSize)

        val mfccOutput = Array(numCoefficients) { FloatArray(numFilters) }

        for (i in 0 until numCoefficients) {
            if (i < frames.size) {
                val frame = frames[i]
                for (j in frame.indices) frame[j] *= window[j]

                val powerSpectrum = powerSpectrum(frame)
                val mel = melFilterBank(powerSpectrum)

                for (j in 0 until min(numFilters, mel.size)) {
                    mfccOutput[i][j] = log10(max(mel[j], 1e-6f))
                }
            }
        }

        return mfccOutput
    }

    private fun framing(signal: ShortArray, frameSize: Int, step: Int): Array<FloatArray> {
        val numFrames = (signal.size - frameSize) / step
        val frames = Array(numFrames) { FloatArray(frameSize) }

        for (i in 0 until numFrames) {
            for (j in 0 until frameSize) {
                frames[i][j] = signal[i * step + j].toFloat()
            }
        }

        return frames
    }

    private fun hammingWindow(size: Int): FloatArray {
        val window = FloatArray(size)
        for (i in 0 until size) {
            window[i] = (0.54 - 0.46 * cos(2 * Math.PI * i / (size - 1))).toFloat()
        }
        return window
    }

    private fun powerSpectrum(frame: FloatArray): FloatArray {
        val n = frame.size
        val re = frame.copyOf()
        val im = FloatArray(n)

        fft(re, im)

        val ps = FloatArray(n / 2)
        for (i in ps.indices) {
            ps[i] = re[i].pow(2) + im[i].pow(2)
        }
        return ps
    }

    private fun melFilterBank(powerSpectrum: FloatArray): FloatArray {
        val mel = FloatArray(numFilters)
        val step = powerSpectrum.size / numFilters
        for (i in mel.indices) {
            mel[i] = powerSpectrum.copyOfRange(i * step, min(powerSpectrum.size, (i + 1) * step)).sum()
        }
        return mel
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var i = 1
        var j = 0
        while (i < n) {
            var k = n shr 1
            while (j >= k) {
                j -= k
                k = k shr 1
            }
            j += k

            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            i++
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wlenR = cos(angle).toFloat()
            val wlenI = sin(angle).toFloat()

            for (p in 0 until n step len) {
                var wR = 1f
                var wI = 0f
                for (q in 0 until len / 2) {
                    val uR = real[p + q]
                    val uI = imag[p + q]

                    val vR = real[p + q + len / 2] * wR - imag[p + q + len / 2] * wI
                    val vI = real[p + q + len / 2] * wI + imag[p + q + len / 2] * wR

                    real[p + q] = uR + vR
                    imag[p + q] = uI + vI

                    real[p + q + len / 2] = uR - vR
                    imag[p + q + len / 2] = uI - vI

                    val nextWR = wR * wlenR - wI * wlenI
                    val nextWI = wR * wlenI + wI * wlenR
                    wR = nextWR
                    wI = nextWI
                }
            }

            len = len shl 1
        }
    }
}
