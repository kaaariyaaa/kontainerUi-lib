package com.github.kaaariyaaa.kontainer_ui_lib.runtime

import com.github.kaaariyaaa.kontainer_ui_lib.context.UiRenderContext
import com.github.kaaariyaaa.kontainer_ui_lib.model.MenuModel
import com.github.kaaariyaaa.kontainer_ui_lib.model.SlotModel
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventoryManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

internal class MenuRenderer(
    private val menus: Map<String, MenuModel>,
    private val virtualInventories: VirtualInventoryManager,
) {
    fun renderFull(
        session: WindowSession,
        player: Player,
    ): RenderResult {
        val inventory = session.inventory
        inventory.clear()

        val slots = IntArray(inventory.size) { it }
        return renderSlots(session, player, slots)
    }

    fun renderSlots(
        session: WindowSession,
        player: Player,
        slots: IntArray,
    ): RenderResult {
        val uniqueSlots = slots.distinct().filter { it in 0 until session.inventory.size }
        if (uniqueSlots.isEmpty()) {
            return RenderResult(intArrayOf(), emptyMap())
        }

        val rootContext = createRenderContext(player, session.menu.id, session.state)
        val resolved = linkedMapOf<Int, ResolvedSlotRuntime>()

        uniqueSlots.forEach { slot ->
            val renderData =
                resolveSlot(
                    menu = session.menu,
                    state = session.state,
                    player = player,
                    requestedSlot = slot,
                    depth = 0,
                )

            val item = renderData?.item ?: session.menu.slots[slot]?.itemProvider(rootContext)
            session.inventory.setItem(slot, item?.clone())

            if (renderData != null) {
                resolved[slot] =
                    ResolvedSlotRuntime(
                        menuId = renderData.menuId,
                        state = renderData.state,
                        onClick = renderData.onClick,
                        cancelClick = renderData.cancelClick,
                        virtualBinding = renderData.virtualBinding,
                    )
            } else {
                val base = session.menu.slots[slot]
                resolved[slot] =
                    ResolvedSlotRuntime(
                        menuId = session.menu.id,
                        state = session.state,
                        onClick = base?.onClick,
                        cancelClick = base?.cancelClick,
                        virtualBinding = null,
                    )
            }
        }

        return RenderResult(uniqueSlots.toIntArray(), resolved)
    }

    private fun resolveSlot(
        menu: MenuModel,
        state: Any?,
        player: Player,
        requestedSlot: Int,
        depth: Int,
    ): ResolvedRenderData? {
        if (depth > MAX_NESTED_DEPTH) return null

        val context = createRenderContext(player, menu.id, state)
        val slotModel = menu.slots[requestedSlot] ?: return null
        if (!slotModel.visibleWhen(context)) return null

        val nested = slotModel.nestedSpec
        if (nested != null) {
            val nestedMenuId = nested.menuIdProvider(context).trim().lowercase()
            val nestedMenu = menus[nestedMenuId]
            if (nestedMenu != null) {
                val nestedState = nested.stateProvider(context)
                val nestedSlot = nested.slotProvider(context, requestedSlot)
                if (nestedSlot in 0 until (nestedMenu.rows * 9)) {
                    val nestedResolved =
                        resolveSlot(
                            menu = nestedMenu,
                            state = nestedState,
                            player = player,
                            requestedSlot = nestedSlot,
                            depth = depth + 1,
                        )
                    if (nestedResolved != null) {
                        return nestedResolved
                    }
                }
            }
        }

        return resolveDirectSlot(
            menu = menu,
            state = state,
            slot = requestedSlot,
            slotModel = slotModel,
            player = player,
        )
    }

    private fun resolveDirectSlot(
        menu: MenuModel,
        state: Any?,
        slot: Int,
        slotModel: SlotModel,
        player: Player,
    ): ResolvedRenderData {
        val context = createRenderContext(player, menu.id, state)

        val virtual = slotModel.virtualSpec
        val virtualBinding =
            virtual?.let { spec ->
                val inventory = spec.inventoryProvider(context)
                val index = spec.indexProvider(context, slot)
                if (index in 0 until inventory.size) {
                    ResolvedVirtualSlot(
                        inventory = inventory,
                        index = index,
                        allowTake = spec.allowTake,
                        allowInsert = spec.allowInsert,
                    )
                } else {
                    null
                }
            }

        val virtualItem = virtualBinding?.let { binding -> binding.inventory.getItem(binding.index) }
        val fallbackItem = slotModel.itemProvider(context)
        val finalItem = (virtualItem ?: fallbackItem)?.clone()

        return ResolvedRenderData(
            menuId = menu.id,
            state = state,
            item = finalItem,
            onClick = slotModel.onClick,
            cancelClick = slotModel.cancelClick,
            virtualBinding = virtualBinding,
        )
    }

    private fun createRenderContext(
        player: Player,
        menuId: String,
        state: Any?,
    ): UiRenderContext<Any?> {
        return UiRenderContext(
            player = player,
            menuId = menuId,
            rawState = state,
            virtualInventoryProvider = { id, size -> virtualInventories.getOrCreate(id, size) },
        )
    }
}

internal data class RenderResult(
    val renderedSlots: IntArray,
    val resolvedSlots: Map<Int, ResolvedSlotRuntime>,
)

private data class ResolvedRenderData(
    val menuId: String,
    val state: Any?,
    val item: ItemStack?,
    val onClick: (com.github.kaaariyaaa.kontainer_ui_lib.context.UiClickContext<Any?>.() -> Unit)?,
    val cancelClick: Boolean?,
    val virtualBinding: ResolvedVirtualSlot?,
)

private const val MAX_NESTED_DEPTH = 8
