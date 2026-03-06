package com.github.kaaariyaaa.kontainer_ui_lib.virtual

fun interface VirtualMapping {
    fun resolve(
        position: Int,
        menuSlot: Int,
    ): Int
}

fun sequential(
    start: Int = 0,
    step: Int = 1,
): VirtualMapping {
    require(step > 0) { "step must be > 0" }
    return VirtualMapping { position, _ ->
        start + (position * step)
    }
}

fun customMapping(block: (position: Int, menuSlot: Int) -> Int): VirtualMapping =
    VirtualMapping(block)
