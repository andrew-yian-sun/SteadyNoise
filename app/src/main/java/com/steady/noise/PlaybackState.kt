package com.steady.noise

import kotlinx.coroutines.flow.MutableStateFlow

data class PlaybackSnapshot(
    val playing: Boolean = false,
    val noiseType: NoiseType = NoiseType.WHITE,
    val endElapsedRealtime: Long = 0L,
    val detail: String = "",
)

object PlaybackState {
    val snapshot = MutableStateFlow(PlaybackSnapshot())
}
