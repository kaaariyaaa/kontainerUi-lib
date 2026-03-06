package com.github.kaaariyaaa.kontainer_ui_lib.persistence

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

internal class PersistenceCoordinator(
    private val plugin: JavaPlugin,
    private val repository: VirtualInventoryRepository,
    private val autoSaveIntervalTicks: Long,
    private val snapshotProvider: (UUID) -> PersistedVirtualInventory?,
    private val applyLoaded: (UUID, PersistedVirtualInventory) -> Unit,
) {
    private val dirtyIds = linkedSetOf<UUID>()
    private val deleteIds = linkedSetOf<UUID>()
    private var autoSaveTask: BukkitTask? = null
    private var stopped = false

    fun start() {
        if (autoSaveTask != null || autoSaveIntervalTicks <= 0L) return
        autoSaveTask =
            plugin.server.scheduler.runTaskTimer(
                plugin,
                Runnable { flushAsync() },
                autoSaveIntervalTicks,
                autoSaveIntervalTicks,
            )
    }

    fun markDirty(id: UUID) {
        if (stopped) return
        synchronized(this) {
            dirtyIds += id
        }
    }

    fun markDeleted(id: UUID) {
        if (stopped) return
        synchronized(this) {
            dirtyIds -= id
            deleteIds += id
        }
    }

    fun requestLoad(id: UUID) {
        if (stopped) return
        plugin.server.scheduler.runTaskAsynchronously(
            plugin,
            Runnable {
                val loaded = runCatching { repository.load(id) }
                    .onFailure { error ->
                        plugin.logger.warning("Failed loading virtual inventory $id: ${error.message}")
                    }
                    .getOrNull()
                    ?: return@Runnable

                Bukkit.getScheduler().runTask(
                    plugin,
                    Runnable {
                        if (stopped) return@Runnable
                        applyLoaded(id, loaded)
                    },
                )
            },
        )
    }

    fun flushNowBlocking() {
        persist(collectPayloads(clear = true))
    }

    fun shutdown() {
        if (stopped) return
        stopped = true
        autoSaveTask?.cancel()
        autoSaveTask = null
        flushNowBlocking()
    }

    private fun flushAsync() {
        val payloads = collectPayloads(clear = true)
        if (payloads.saves.isEmpty() && payloads.deleteIds.isEmpty()) return

        plugin.server.scheduler.runTaskAsynchronously(
            plugin,
            Runnable {
                persist(payloads)
            },
        )
    }

    private fun collectPayloads(clear: Boolean): PersistencePayload {
        val saveIds: Set<UUID>
        val removedIds: Set<UUID>
        synchronized(this) {
            saveIds = dirtyIds.toSet()
            removedIds = deleteIds.toSet()
            if (clear) {
                dirtyIds.clear()
                deleteIds.clear()
            }
        }

        val snapshots = saveIds.mapNotNull(snapshotProvider)
        return PersistencePayload(snapshots, removedIds)
    }

    private fun persist(payload: PersistencePayload) {
        payload.deleteIds.forEach { id ->
            runCatching { repository.delete(id) }
                .onFailure { error ->
                    plugin.logger.warning("Failed deleting virtual inventory $id: ${error.message}")
                }
        }

        payload.saves.forEach { snapshot ->
            runCatching { repository.save(snapshot) }
                .onFailure { error ->
                    plugin.logger.warning("Failed saving virtual inventory ${snapshot.id}: ${error.message}")
                }
        }
    }
}

private data class PersistencePayload(
    val saves: List<PersistedVirtualInventory>,
    val deleteIds: Set<UUID>,
)
