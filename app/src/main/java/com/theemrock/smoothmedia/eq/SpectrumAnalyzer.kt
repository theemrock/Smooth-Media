package com.theemrock.smoothmedia.eq

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 24 log-spaced analysis bands, 31.5 Hz to 16 kHz. Same centers will drive the 24-band EQ. */
object EqBands24 {
    const val COUNT = 24
    val centersHz: DoubleArray = DoubleArray(COUNT) { 31.5 * (16_000.0 / 31.5).pow(it / (COUNT - 1.0)) }

    /** Lower edge of band [i]; index COUNT is the upper edge of the last band. */
    fun edgeHz(i: Int): Double = when {
        i <= 0 -> centersHz[0] / sqrt(centersHz[1] / centersHz[0])
        i >= COUNT -> centersHz[COUNT - 1] * sqrt(centersHz[COUNT - 1] / centersHz[COUNT - 2])
        else -> sqrt(centersHz[i - 1] * centersHz[i])
    }
}

/** Band levels in dB for one analysis window; [bandDb] is null when the window is too quiet to judge. */
class SpectrumWindow(val centerMs: Long, val bandDb: DoubleArray?)

/**
 * Streaming band analyzer: feed mono PCM, get one [SpectrumWindow] per hop.
 * Band level is the energy inside the band, so pink noise reads flat.
 * shortcut: FFT is radix-2 on doubles, no SIMD; fine for offline scans, revisit if a scan is too slow.
 */
class SpectrumAnalyzer(
    private val sampleRate: Int,
    private val hopSeconds: Double = 5.0,
    private val windowBlocks: Int = 2,
    private val silenceDbFs: Double = -55.0,
) {
    private val fftSize = 16_384
    private val hann = DoubleArray(fftSize) { 0.5 - 0.5 * cos(2 * PI * it / fftSize) }
    private val hannPower = hann.sumOf { it * it }
    private val binBand = IntArray(fftSize / 2 + 1) { bandOf(it * sampleRate.toDouble() / fftSize) }
    private val frame = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val pending = FloatArray(fftSize)
    private var pendingCount = 0
    private val framesPerBlock = maxOf(1, (hopSeconds * sampleRate / (fftSize / 2)).toInt())
    private var framesInBlock = 0
    private var blockPower = DoubleArray(EqBands24.COUNT)
    private val recent = ArrayDeque<Pair<DoubleArray, Int>>()
    private val out = mutableListOf<SpectrumWindow>()
    private var blockIndex = 0

    fun feed(mono: FloatArray, count: Int) {
        for (i in 0 until count) {
            pending[pendingCount++] = mono[i]
            if (pendingCount == fftSize) {
                processFrame()
                System.arraycopy(pending, fftSize / 2, pending, 0, fftSize / 2)
                pendingCount = fftSize / 2
            }
        }
    }

    fun finish(): List<SpectrumWindow> {
        if (framesInBlock > 0) closeBlock()
        return out.toList()
    }

    private fun processFrame() {
        for (i in 0 until fftSize) {
            frame[i] = pending[i] * hann[i]
            im[i] = 0.0
        }
        fft(frame, im)
        for (k in 1..fftSize / 2) {
            val band = binBand[k]
            if (band < 0) continue
            // One-sided spectrum: the factor 2 folds in the mirrored negative-frequency half.
            blockPower[band] += 2 * (frame[k] * frame[k] + im[k] * im[k]) / (fftSize * hannPower)
        }
        if (++framesInBlock == framesPerBlock) closeBlock()
    }

    private fun closeBlock() {
        recent.addLast(blockPower to framesInBlock)
        if (recent.size > windowBlocks) recent.removeFirst()
        val frames = recent.sumOf { it.second }
        val mean = DoubleArray(EqBands24.COUNT) { b -> recent.sumOf { it.first[b] } / frames }
        val totalDb = 10 * log10(mean.sum() + 1e-12)
        val blockMs = (hopSeconds * 1000).toLong()
        val endMs = (blockIndex + 1) * blockMs
        val centerMs = endMs - recent.size * blockMs / 2
        val bandDb = if (totalDb < silenceDbFs) null else DoubleArray(EqBands24.COUNT) { 10 * log10(mean[it] + 1e-12) }
        out += SpectrumWindow(centerMs, bandDb)
        blockIndex++
        blockPower = DoubleArray(EqBands24.COUNT)
        framesInBlock = 0
    }

    private fun bandOf(hz: Double): Int {
        if (hz < EqBands24.edgeHz(0) || hz >= EqBands24.edgeHz(EqBands24.COUNT)) return -1
        for (i in 0 until EqBands24.COUNT) if (hz < EqBands24.edgeHz(i + 1)) return i
        return -1
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
