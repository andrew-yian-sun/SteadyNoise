package com.steady.noise

enum class NoiseType {
    WHITE,
    BROWN,
}

fun NoiseType.label(): String = when (this) {
    NoiseType.WHITE -> "White noise"
    NoiseType.BROWN -> "Brown noise"
}
