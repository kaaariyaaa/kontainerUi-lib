package com.github.kaaariyaaa.kontainer_ui_lib.model

import com.github.kaaariyaaa.kontainer_ui_lib.context.UiClickContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiCloseContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiOpenContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiOutsideClickContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiRenderContext
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack

internal data class MenuModel(
    val id: String,
    val rows: Int,
    val defaultState: Any?,
    val titleProvider: (UiRenderContext<Any?>) -> Component,
    val clickPolicy: ClickPolicy,
    val closeable: Boolean,
    val slots: Map<Int, SlotModel>,
    val lifecycle: MenuLifecycle,
)

internal data class ClickPolicy(
    val cancelTopClicks: Boolean,
    val cancelBottomClicks: Boolean,
    val cancelDrag: Boolean,
)

internal data class MenuLifecycle(
    val onOpenHandlers: List<UiOpenContext<Any?>.() -> Unit>,
    val onCloseHandlers: List<UiCloseContext<Any?>.() -> Unit>,
    val onOutsideClickHandlers: List<UiOutsideClickContext<Any?>.() -> Unit>,
)

internal data class SlotModel(
    val itemProvider: (UiRenderContext<Any?>) -> ItemStack?,
    val visibleWhen: (UiRenderContext<Any?>) -> Boolean,
    val onClick: (UiClickContext<Any?>.() -> Unit)?,
    val cancelClick: Boolean?,
    val virtualSpec: VirtualSlotSpec?,
    val nestedSpec: NestedSlotSpec?,
)

internal data class VirtualSlotSpec(
    val inventoryProvider: (UiRenderContext<Any?>) -> com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventory,
    val indexProvider: (UiRenderContext<Any?>, Int) -> Int,
    val allowTake: Boolean,
    val allowInsert: Boolean,
)

internal data class NestedSlotSpec(
    val menuIdProvider: (UiRenderContext<Any?>) -> String,
    val stateProvider: (UiRenderContext<Any?>) -> Any?,
    val slotProvider: (UiRenderContext<Any?>, Int) -> Int,
)
