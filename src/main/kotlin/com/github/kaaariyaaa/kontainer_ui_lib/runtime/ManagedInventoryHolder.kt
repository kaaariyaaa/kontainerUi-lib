package com.github.kaaariyaaa.kontainer_ui_lib.runtime

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

internal class ManagedInventoryHolder(
    val sessionId: UUID,
) : InventoryHolder {
    private var boundInventory: Inventory? = null

    fun bind(inventory: Inventory) {
        boundInventory = inventory
    }

    override fun getInventory(): Inventory = boundInventory ?: Bukkit.createInventory(null, 9)
}
