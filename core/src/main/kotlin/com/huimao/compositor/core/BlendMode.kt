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
}
