package com.github.kaaariyaaa.kontainer_ui_lib

import com.github.kaaariyaaa.kontainer_ui_lib.animation.AnimationPreset
import com.github.kaaariyaaa.kontainer_ui_lib.animation.stagger
import com.github.kaaariyaaa.kontainer_ui_lib.dsl.KontainerRegistry
import com.github.kaaariyaaa.kontainer_ui_lib.runtime.UiRuntime
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventory
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

fun kontainerUi(
    plugin: JavaPlugin,
    block: KontainerRegistry.() -> Unit,
): KontainerUiLib {
    val registry = KontainerRegistry()
    registry.block()
    val built = registry.build()

    return KontainerUiLib(
        plugin = plugin,
        menus = built.menus,
        persistenceOptions = built.persistenceOptions,
    )
}

class KontainerUiLib internal constructor(
    plugin: JavaPlugin,
    menus: Map<String, com.github.kaaariyaaa.kontainer_ui_lib.model.MenuModel>,
    persistenceOptions: com.github.kaaariyaaa.kontainer_ui_lib.persistence.PersistenceOptions,
) {
    private val runtime = UiRuntime(plugin, menus, persistenceOptions)
    private val menuIds = menus.keys.toSet()

    init {
        runtime.register()
    }

    fun open(
        player: Player,
        menuId: String,
        state: Any? = null,
    ) {
        runtime.open(player, menuId, state)
    }

    fun navigate(
        player: Player,
        menuId: String,
        state: Any? = null,
        reopen: Boolean = false,
    ): Boolean = runtime.navigate(player, menuId, state, reopen)

    fun refresh(player: Player): Boolean = runtime.refresh(player)

    fun refresh(
        player: Player,
        slots: IntArray,
    ): Boolean = runtime.refresh(player, slots)

    fun setState(
        player: Player,
        state: Any?,
    ): Boolean = runtime.setState(player, state)

    fun updateState(
        player: Player,
        transform: (Any?) -> Any?,
    ): Boolean = runtime.updateState(player, transform)

    fun updateStateAndRefresh(
        player: Player,
        transform: (Any?) -> Any?,
    ): Boolean {
        val updated = updateState(player, transform)
        if (!updated) return false
        return refresh(player)
    }

    fun animate(
        player: Player,
        slots: IntArray = intArrayOf(),
        preset: AnimationPreset = stagger(),
        intervalTicks: Long = 1L,
    ): Boolean = runtime.animate(player, slots, preset, intervalTicks)

    fun close(player: Player): Boolean = runtime.close(player)

    fun virtualInventory(
        id: UUID,
        size: Int,
    ): VirtualInventory = runtime.virtualInventory(id, size)

    fun unregister() {
        runtime.unregister()
    }

    fun hasMenu(menuId: String): Boolean = menuIds.contains(menuId.lowercase())

    fun menuIds(): Set<String> = menuIds
}
