package com.github.kaaariyaaa.kontainer_ui_lib.runtime

import com.github.kaaariyaaa.kontainer_ui_lib.animation.AnimationPreset
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import kotlin.random.Random
import java.util.UUID

internal class AnimationEngine(
    private val plugin: JavaPlugin,
) {
    private val tasks = mutableMapOf<UUID, BukkitTask>()

    fun play(
        session: WindowSession,
        targetSlots: IntArray,
        preset: AnimationPreset,
        intervalTicks: Long,
    ): Boolean {
        val slots =
            targetSlots
                .distinct()
                .filter { it in 0 until session.inventory.size }

        if (slots.isEmpty()) return false

        cancel(session.id)

        val frames = linkedMapOf<Int, ItemStack?>()
        slots.forEach { slot ->
            frames[slot] = session.inventory.getItem(slot)?.clone()
            session.inventory.setItem(slot, null)
        }

        val orderedSlots = order(slots, preset)
        var pointer = 0

        val task =
            plugin.server.scheduler.runTaskTimer(
                plugin,
                Runnable {
                    if (pointer >= orderedSlots.size) {
                        cancel(session.id)
                        return@Runnable
                    }

                    val slot = orderedSlots[pointer++]
                    session.inventory.setItem(slot, frames[slot]?.clone())
                },
                0L,
                intervalTicks.coerceAtLeast(1L),
            )

        tasks[session.id] = task
        return true
    }

    fun cancel(sessionId: UUID) {
        tasks.remove(sessionId)?.cancel()
    }

    fun shutdown() {
        tasks.values.forEach(BukkitTask::cancel)
        tasks.clear()
    }

    private fun order(
        slots: List<Int>,
        preset: AnimationPreset,
    ): List<Int> {
        return when (preset) {
            AnimationPreset.STAGGER -> slots
            AnimationPreset.SNAKE -> orderSnake(slots)
            AnimationPreset.RANDOM -> slots.shuffled(Random(System.nanoTime()))
        }
    }

    private fun orderSnake(slots: List<Int>): List<Int> {
        val grouped = slots.groupBy { it / 9 }
        val rows = grouped.keys.sorted()
        val ordered = mutableListOf<Int>()

        rows.forEach { row ->
            val rowSlots = grouped[row].orEmpty().sortedBy { it % 9 }
            if (row % 2 == 0) {
                ordered += rowSlots
            } else {
                ordered += rowSlots.reversed()
            }
        }

        return ordered
    }
}
