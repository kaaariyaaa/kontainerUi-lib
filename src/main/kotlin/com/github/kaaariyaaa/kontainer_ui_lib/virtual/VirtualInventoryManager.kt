package com.github.kaaariyaaa.kontainer_ui_lib.virtual

import com.github.kaaariyaaa.kontainer_ui_lib.persistence.PersistenceCoordinator
import com.github.kaaariyaaa.kontainer_ui_lib.persistence.PersistenceOptions
import com.github.kaaariyaaa.kontainer_ui_lib.persistence.PersistedVirtualInventory
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class VirtualInventoryManager internal constructor(
    private val plugin: JavaPlugin,
    private val persistenceOptions: PersistenceOptions,
) {
    private val inventories = mutableMapOf<UUID, VirtualInventory>()
    private val revisions = mutableMapOf<UUID, Long>()
    private val pendingLoadIds = mutableSetOf<UUID>()
    private val localChangesBeforeLoad = mutableSetOf<UUID>()

    private val persistence =
        persistenceOptions.repository?.let { repository ->
            PersistenceCoordinator(
                plugin = plugin,
                repository = repository,
                autoSaveIntervalTicks = persistenceOptions.autoSaveIntervalTicks,
                snapshotProvider = ::snapshotFor,
                applyLoaded = ::applyLoaded,
            )
        }

    init {
        persistence?.start()
    }

    fun getOrCreate(
        id: UUID,
        size: Int,
    ): VirtualInventory {
        require(size > 0) { "VirtualInventory size must be > 0" }

        val existing = inventories[id]
        if (existing != null) return existing

        val inventory =
            VirtualInventory(
                id = id,
                size = size,
                dirtyCallback = ::markDirty,
            )

        inventories[id] = inventory
        revisions.putIfAbsent(id, 0L)

        if (persistence != null) {
            pendingLoadIds += id
            persistence.requestLoad(id)
        }

        return inventory
    }

    fun get(id: UUID): VirtualInventory? = inventories[id]

    fun all(): Collection<VirtualInventory> = inventories.values.toList()

    fun remove(id: UUID): Boolean {
        val removed = inventories.remove(id) ?: return false
        removed.clearAll()
        pendingLoadIds -= id
        localChangesBeforeLoad -= id
        revisions.remove(id)
        persistence?.markDeleted(id)
        return true
    }

    fun shutdown() {
        persistence?.shutdown()
    }

    private fun markDirty(id: UUID) {
        if (id in pendingLoadIds) {
            localChangesBeforeLoad += id
        }
        persistence?.markDirty(id)
    }

    private fun snapshotFor(id: UUID): PersistedVirtualInventory? {
        val inventory = inventories[id] ?: return null
        val revision = (revisions[id] ?: 0L) + 1L
        revisions[id] = revision
        return inventory.snapshot(revision)
    }

    private fun applyLoaded(
        id: UUID,
        persisted: PersistedVirtualInventory,
    ) {
        if (id !in pendingLoadIds) return
        pendingLoadIds -= id

        if (localChangesBeforeLoad.remove(id)) {
            return
        }

        val inventory = inventories[id] ?: return
        revisions[id] = maxOf(revisions[id] ?: 0L, persisted.revision)
        inventory.applyPersisted(persisted)
    }
}
