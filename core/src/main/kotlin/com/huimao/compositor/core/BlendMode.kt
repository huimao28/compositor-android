package com.huimao.compositor.core

/**
 * Blend modes from upstream .comp format v3.
 * Serial names match the Mac app's manifest values exactly.
 */
enum class BlendMode(val serialName: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    DIFFERENCE("Difference"),
    COLOR_DODGE("Color Dodge"),
    COLOR_BURN("Color Burn");

    companion object {
        fun fromSerialName(name: String): BlendMode =
            entries.firstOrNull { it.serialName == name } ?: NORMAL
    }

    /**
     * Separable blend function B(Cb, Cs) per PDF 32000 §11.3.5 ("Blend modes").
     * Cb = backdrop channel, Cs = source channel, all in [0, 1].
     * Upstream draws most of these through Core Graphics; the formulas below
     * are the spec values both should agree on.
     */
    fun blend(cb: Double, cs: Double): Double = when (this) {
        NORMAL -> cs
        MULTIPLY -> cb * cs
        SCREEN -> 1.0 - (1.0 - cb) * (1.0 - cs)
        OVERLAY -> if (cb <= 0.5) 2.0 * cb * cs else 1.0 - 2.0 * (1.0 - cb) * (1.0 - cs)
        DARKEN -> minOf(cb, cs)
        LIGHTEN -> maxOf(cb, cs)
        COLOR_DODGE -> when {
            cb == 0.0 -> 0.0
            cs >= 1.0 -> 1.0
            else -> minOf(1.0, cb / (1.0 - cs))
        }
        COLOR_BURN -> when {
            cb >= 1.0 -> 1.0
            cs <= 0.0 -> 0.0
            else -> 1.0 - minOf(1.0, (1.0 - cb) / cs)
        }
        DIFFERENCE -> kotlin.math.abs(cb - cs)
    }
}
