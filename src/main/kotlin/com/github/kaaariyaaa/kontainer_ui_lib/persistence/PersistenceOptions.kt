package com.github.kaaariyaaa.kontainer_ui_lib.persistence

data class PersistenceOptions(
    val repository: VirtualInventoryRepository? = null,
    val autoSaveIntervalTicks: Long = 100L,
)
