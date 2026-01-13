// --- mobile/src/main/java/com/samsung/health/mobile/pipeline/DspUtils.kt ---
package com.samsung.health.mobile.pipeline

import kotlin.math.*

object DspUtils {

    /**
     * Filter coefficients container.
     * b: numerator, a: denominator (a[0] must be non-zero)
     */
    data class Coefficients(val b: DoubleArray, val a: DoubleArray)

    /**
     * Butterworth bandpass placeholder.
     * Architectural decision: coefficients should be hardcoded from Python/Scipy
     * for exact numerical parity on mobile.
     */
    fun butterBandpass(
        order: Int,
        lowCutoff: Double,
        highCutoff: Double,
        fs: Double
    ): Coefficients {
        // Intentionally left as a stub.
        // Replace with hardcoded b/a arrays generated from Python.
        return Coefficients(doubleArrayOf(), doubleArrayOf())
    }

    /**
     * Scipy-compatible filtfilt with reflection padding.
     * Forward filter -> reverse -> filter -> reverse back.
     */
    fun filtfilt(b: DoubleArray, a: DoubleArray, x: DoubleArray): DoubleArray {
        val n = x.size
        if (n == 0) return x

        // If too short, fall back to naive forward-backward
        val edge = 3 * max(b.size, a.size)
        if (n <= edge) {
            val fwd = lfilter(b, a, x)
            val bwd = lfilter(b, a, fwd.reversedArray())
            return bwd.reversedArray()
        }

        // Reflection padding
        val padLen = edge
        val padded = DoubleArray(n + 2 * padLen)

        // Start reflection: 2*x[0] - x[padLen..1]
        val startVal = x[0]
        for (i in 0 until padLen) {
            padded[i] = 2.0 * startVal - x[padLen - i]
        }

        // Original signal
        System.arraycopy(x, 0, padded, padLen, n)

        // End reflection: 2*x[n-1] - x[n-2..n-padLen-1]
        val endVal = x[n - 1]
        for (i in 0 until padLen) {
            padded[n + padLen + i] = 2.0 * endVal - x[n - 2 - i]
        }

        // Forward filter
        val forward = lfilter(b, a, padded)

        // Reverse and filter again
        val backward = lfilter(b, a, forward.reversedArray())

        // Reverse back and extract valid center
        val result = backward.reversedArray()
        return result.copyOfRange(padLen, n + padLen)
    }

    /**
     * Standard IIR filter (Direct Form I).
     * Matches the textbook difference equation and basic Python loops.
     */
    fun lfilter(b: DoubleArray, a: DoubleArray, x: DoubleArray): DoubleArray {
        val y = DoubleArray(x.size)
        if (x.isEmpty()) return y
        require(a.isNotEmpty() && a[0] != 0.0) { "Invalid filter coefficients" }

        for (i in x.indices) {
            var acc = 0.0

            for (j in b.indices) {
                if (i - j >= 0) {
                    acc += b[j] * x[i - j]
                }
            }

            for (j in 1 until a.size) {
                if (i - j >= 0) {
                    acc -= a[j] * y[i - j]
                }
            }

            y[i] = acc / a[0]
        }
        return y
    }

    /**
     * Simplified peak detection.
     * Local maxima + minimum distance constraint.
     */
    fun findPeaks(
        x: DoubleArray,
        distance: Int,
        prominence: Double
    ): List<Int> {
        if (x.size < 3) return emptyList()

        val candidates = ArrayList<Int>()
        for (i in 1 until x.size - 1) {
            val xi = x[i]
            if (!xi.isFinite()) continue
            if (xi > x[i - 1] && xi > x[i + 1]) {
                candidates.add(i)
            }
        }

        if (candidates.isEmpty()) return emptyList()

        val kept = ArrayList<Int>()
        kept.add(candidates[0])

        for (i in 1 until candidates.size) {
            if (candidates[i] - kept.last() >= distance) {
                kept.add(candidates[i])
            }
        }

        // Prominence is intentionally approximated / ignored here.
        return kept
    }

    /**
     * Linear interpolation (np.interp equivalent).
     */
    fun interp(x: DoubleArray, xp: DoubleArray, fp: DoubleArray): DoubleArray {
        val out = DoubleArray(x.size)
        for (i in x.indices) {
            out[i] = linearInterp(x[i], xp, fp)
        }
        return out
    }

    private fun linearInterp(x: Double, xp: DoubleArray, fp: DoubleArray): Double {
        if (xp.isEmpty()) return 0.0
        if (x <= xp.first()) return fp.first()
        if (x >= xp.last()) return fp.last()

        var idx = xp.binarySearch(x)
        if (idx >= 0) return fp[idx]
        idx = -(idx + 1) - 1

        val x0 = xp[idx]
        val x1 = xp[idx + 1]
        val y0 = fp[idx]
        val y1 = fp[idx + 1]

        return y0 + (x - x0) * (y1 - y0) / (x1 - x0)
    }

    /**
     * Windowed periodogram (Welch-like, single segment).
     */
    fun welchPsd(signal: DoubleArray, fs: Double): Pair<DoubleArray, DoubleArray> {
        val n = signal.size
        if (n == 0) return Pair(doubleArrayOf(), doubleArrayOf())

        var m = 1
        while (m < n) m = m shl 1

        val real = DoubleArray(m)
        val imag = DoubleArray(m)

        // Hann window
        for (i in 0 until n) {
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            real[i] = signal[i] * w
        }

        fft(real, imag)

        val half = m / 2
        val freqs = DoubleArray(half + 1)
        val psd = DoubleArray(half + 1)

        for (i in 0..half) {
            freqs[i] = i * fs / m
            val mag2 = real[i] * real[i] + imag[i] * imag[i]
            psd[i] = mag2 / (fs * n)
        }

        return Pair(freqs, psd)
    }

    /**
     * Recursive radix-2 FFT.
     * Correct but allocation-heavy; not suitable for high-frequency calls.
     */
    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        if (n <= 1) return

        val half = n / 2
        val evenReal = DoubleArray(half) { real[2 * it] }
        val evenImag = DoubleArray(half) { imag[2 * it] }
        val oddReal = DoubleArray(half) { real[2 * it + 1] }
        val oddImag = DoubleArray(half) { imag[2 * it + 1] }

        fft(evenReal, evenImag)
        fft(oddReal, oddImag)

        for (k in 0 until half) {
            val angle = -2.0 * PI * k / n
            val cosA = cos(angle)
            val sinA = sin(angle)

            val tr = cosA * oddReal[k] - sinA * oddImag[k]
            val ti = sinA * oddReal[k] + cosA * oddImag[k]

            real[k] = evenReal[k] + tr
            imag[k] = evenImag[k] + ti
            real[k + half] = evenReal[k] - tr
            imag[k + half] = evenImag[k] - ti
        }
    }
}
