package com.github.kaaariyaaa.kontainer_ui_lib.persistence

import java.util.UUID

data class PersistedVirtualInventory(
    val id: UUID,
    val size: Int,
    val maxStackSizes: IntArray,
    val items: List<ByteArray?>,
    val revision: Long = 0L,
)
