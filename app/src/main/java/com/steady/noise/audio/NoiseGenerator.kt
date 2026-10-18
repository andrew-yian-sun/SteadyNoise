package com.steady.noise.audio

import com.steady.noise.NoiseType
import kotlin.random.Random

/**
 * Fills PCM buffers in place. Brown noise keeps its filter state across calls so
 * successive buffers are one continuous signal.
 */
class NoiseGenerator {
    private val random = Random(System.nanoTime())
    private var brown = 0.0

    fun fill(buffer: ShortArray, type: NoiseType) {
        when (type) {
            NoiseType.WHITE -> fillWhite(buffer)
            NoiseType.BROWN -> fillBrown(buffer)
        }
    }

    private fun fillWhite(buffer: ShortArray) {
        for (index in buffer.indices) {
            buffer[index] = random.nextInt(-WHITE_PEAK, WHITE_PEAK + 1).toShort()
        }
    }

    private fun fillBrown(buffer: ShortArray) {
        var state = brown
        for (index in buffer.indices) {
            val white = random.nextDouble() * 2.0 - 1.0
            state = (state + BROWN_STEP * white) / BROWN_LEAK
            val scaled = (state * BROWN_GAIN).coerceIn(-1.0, 1.0)
            buffer[index] = (scaled * Short.MAX_VALUE).toInt().toShort()
        }
        brown = state
    }

    private companion object {
        const val WHITE_PEAK = 12_000
        const val BROWN_STEP = 0.02
        const val BROWN_LEAK = 1.02
        const val BROWN_GAIN = 3.5
    }
}
