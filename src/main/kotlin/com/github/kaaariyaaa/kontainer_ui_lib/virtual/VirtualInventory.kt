package com.github.kaaariyaaa.kontainer_ui_lib.virtual

import com.github.kaaariyaaa.kontainer_ui_lib.persistence.PersistedVirtualInventory
import org.bukkit.inventory.ItemStack
import java.util.UUID

typealias VirtualInventoryListener = (VirtualInventory, IntArray?) -> Unit

class VirtualInventory internal constructor(
    val id: UUID,
    size: Int,
    private val dirtyCallback: (UUID) -> Unit,
) {
    private var items: Array<ItemStack?> = arrayOfNulls(size)
    private var maxStackSizes: IntArray = IntArray(size) { 64 }
    private val listeners = linkedSetOf<VirtualInventoryListener>()

    val size: Int
        get() = items.size

    fun getItem(slot: Int): ItemStack? {
        checkSlot(slot)
        return items[slot]?.clone()
    }

    fun setItem(
        slot: Int,
        item: ItemStack?,
    ) {
        checkSlot(slot)
        val normalized = normalize(item, slot)
        val previous = items[slot]
        if (isSame(previous, normalized)) return

        items[slot] = normalized
        publishChange(intArrayOf(slot), markDirty = true)
    }

    fun clear(slot: Int) {
        setItem(slot, null)
    }

    fun clearAll() {
        var changed = false
        for (index in items.indices) {
            if (items[index] != null) {
                items[index] = null
                changed = true
            }
        }
        if (changed) {
            publishChange(changedSlots = null, markDirty = true)
        }
    }

    fun resize(newSize: Int) {
        require(newSize > 0) { "VirtualInventory size must be > 0" }
        if (newSize == size) return

        val previousSize = size
        items = items.copyOf(newSize)
        maxStackSizes = maxStackSizes.copyOf(newSize)
        if (newSize > previousSize) {
            for (index in previousSize until newSize) {
                maxStackSizes[index] = 64
            }
        }

        publishChange(changedSlots = null, markDirty = true)
    }

    fun getMaxStackSize(slot: Int): Int {
        checkSlot(slot)
        return maxStackSizes[slot]
    }

    fun setMaxStackSize(
        slot: Int,
        maxStackSize: Int,
    ) {
        checkSlot(slot)
        require(maxStackSize > 0) { "maxStackSize must be > 0" }

        if (maxStackSizes[slot] == maxStackSize) return
        maxStackSizes[slot] = maxStackSize

        val current = items[slot]
        if (current != null && current.amount > maxStackSize) {
            current.amount = maxStackSize
        }

        publishChange(changedSlots = intArrayOf(slot), markDirty = true)
    }

    fun setMaxStackSizes(values: IntArray) {
        require(values.size == size) {
            "Expected maxStackSizes length ${size}, but got ${values.size}"
        }
        if (maxStackSizes.contentEquals(values)) return

        maxStackSizes = values.copyOf()
        for (index in items.indices) {
            val current = items[index]
            val max = maxStackSizes[index]
            if (current != null && current.amount > max) {
                current.amount = max
            }
        }

        publishChange(changedSlots = null, markDirty = true)
    }

    fun addListener(listener: VirtualInventoryListener) {
        listeners += listener
    }

    fun removeListener(listener: VirtualInventoryListener) {
        listeners -= listener
    }

    internal fun applyPersisted(data: PersistedVirtualInventory) {
        val targetSize = data.size
        if (targetSize <= 0) return

        items = arrayOfNulls(targetSize)
        maxStackSizes =
            if (data.maxStackSizes.size == targetSize) {
                data.maxStackSizes.copyOf()
            } else {
                IntArray(targetSize) { 64 }
            }

        val source = data.items
        for (index in 0 until targetSize) {
            val bytes = source.getOrNull(index)
            if (bytes != null) {
                items[index] = normalize(ItemStack.deserializeBytes(bytes), index)
            }
        }

        publishChange(changedSlots = null, markDirty = false)
    }

    internal fun snapshot(revision: Long): PersistedVirtualInventory =
        PersistedVirtualInventory(
            id = id,
            size = size,
            maxStackSizes = maxStackSizes.copyOf(),
            items = items.map { stack -> stack?.clone()?.serializeAsBytes() },
            revision = revision,
        )

    private fun normalize(
        item: ItemStack?,
        slot: Int,
    ): ItemStack? {
        val cloned = item?.clone() ?: return null
        val max = maxStackSizes.getOrNull(slot) ?: 64
        if (cloned.amount > max) {
            cloned.amount = max
        }
        return cloned
    }

    private fun isSame(
        left: ItemStack?,
        right: ItemStack?,
    ): Boolean {
        if (left === right) return true
        if (left == null || right == null) return false
        return left.amount == right.amount && left.isSimilar(right)
    }

    private fun publishChange(
        changedSlots: IntArray?,
        markDirty: Boolean,
    ) {
        if (markDirty) {
            dirtyCallback(id)
        }

        if (listeners.isEmpty()) return
        val snapshot = listeners.toList()
        snapshot.forEach { listener ->
            listener(this, changedSlots)
        }
    }

    private fun checkSlot(slot: Int) {
        require(slot in 0 until size) {
            "Slot out of bounds: $slot (size=$size)"
        }
    }
}
