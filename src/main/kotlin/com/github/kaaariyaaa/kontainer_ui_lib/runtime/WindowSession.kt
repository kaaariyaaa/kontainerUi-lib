package com.github.kaaariyaaa.kontainer_ui_lib.runtime

import com.github.kaaariyaaa.kontainer_ui_lib.model.MenuModel
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventory
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventoryListener
import org.bukkit.inventory.Inventory
import java.util.UUID

internal data class WindowSession(
    val id: UUID,
    val playerId: UUID,
    var menu: MenuModel,
    var state: Any?,
    val holder: ManagedInventoryHolder,
    val inventory: Inventory,
    val resolvedSlotsByMenuSlot: MutableMap<Int, ResolvedSlotRuntime> = mutableMapOf(),
    val virtualBindingsBySlot: MutableMap<Int, ResolvedVirtualSlot> = mutableMapOf(),
    val virtualSubscriptions: MutableMap<UUID, VirtualSubscription> = mutableMapOf(),
)

internal data class ResolvedSlotRuntime(
    val menuId: String,
    val state: Any?,
    val onClick: (com.github.kaaariyaaa.kontainer_ui_lib.context.UiClickContext<Any?>.() -> Unit)?,
    val cancelClick: Boolean?,
    val virtualBinding: ResolvedVirtualSlot?,
)

internal data class ResolvedVirtualSlot(
    val inventory: VirtualInventory,
    val index: Int,
    val allowTake: Boolean,
    val allowInsert: Boolean,
)

internal data class VirtualSubscription(
    val inventory: VirtualInventory,
    val indexToMenuSlots: Map<Int, Set<Int>>,
    val listener: VirtualInventoryListener,
)
