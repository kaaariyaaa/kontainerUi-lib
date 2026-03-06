package com.github.kaaariyaaa.kontainer_ui_lib.persistence

import java.util.UUID

interface VirtualInventoryRepository {
    fun load(id: UUID): PersistedVirtualInventory?

    fun save(data: PersistedVirtualInventory)

    fun delete(id: UUID)

    fun loadAllIds(): Collection<UUID> = emptyList()
}
