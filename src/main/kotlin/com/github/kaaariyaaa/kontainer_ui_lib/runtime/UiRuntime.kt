package com.github.kaaariyaaa.kontainer_ui_lib.runtime

import com.github.kaaariyaaa.kontainer_ui_lib.animation.AnimationPreset
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiClickContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiCloseContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiCloseReason
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiOpenContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiOutsideClickContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiRenderContext
import com.github.kaaariyaaa.kontainer_ui_lib.model.MenuModel
import com.github.kaaariyaaa.kontainer_ui_lib.persistence.PersistenceOptions
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventory
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventoryListener
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventoryManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

internal class UiRuntime(
    private val plugin: JavaPlugin,
    private val menus: Map<String, MenuModel>,
    persistenceOptions: PersistenceOptions,
) : Listener {
    private val sessions = SessionStore()
    private val virtualInventories = VirtualInventoryManager(plugin, persistenceOptions)
    private val renderer = MenuRenderer(menus, virtualInventories)
    private val animations = AnimationEngine(plugin)

    private val forcedCloseReasons = mutableMapOf<UUID, UiCloseReason>()
    private val processingClicks = mutableSetOf<UUID>()
    private val pendingVirtualSync = mutableSetOf<UUID>()

    private var registered = false

    fun register() {
        if (registered) return
        plugin.server.pluginManager.registerEvents(this, plugin)
        registered = true
    }

    fun unregister() {
        ensurePrimaryThread("unregister")
        if (!registered) return

        sessions.all().map { it.id }.forEach { sessionId ->
            requestClose(sessionId, UiCloseReason.UNREGISTER)
        }

        animations.shutdown()
        virtualInventories.shutdown()
        HandlerList.unregisterAll(this)
        registered = false
    }

    fun open(
        player: Player,
        menuId: String,
        state: Any?,
    ) {
        ensurePrimaryThread("open")
        val menu = resolveMenu(menuId)

        sessions.byPlayerId(player.uniqueId)?.let { existing ->
            requestClose(existing.id, UiCloseReason.REPLACED, fallbackPlayer = player)
        }

        val resolvedState = state ?: menu.defaultState
        val sessionId = UUID.randomUUID()
        val holder = ManagedInventoryHolder(sessionId)
        val title = menu.titleProvider(createRenderContext(player, menu.id, resolvedState))
        val inventory = Bukkit.createInventory(holder, menu.rows * 9, title)
        holder.bind(inventory)

        val session =
            WindowSession(
                id = sessionId,
                playerId = player.uniqueId,
                menu = menu,
                state = resolvedState,
                holder = holder,
                inventory = inventory,
            )

        sessions.put(session)

        player.openInventory(inventory)
        renderFull(session, player)
        invokeOpenHandlers(session, player)
    }

    fun navigate(
        player: Player,
        menuId: String,
        state: Any?,
        reopen: Boolean,
    ): Boolean {
        ensurePrimaryThread("navigate")
        val targetMenu = resolveMenu(menuId)
        val session = sessions.byPlayerId(player.uniqueId)
        if (session == null) {
            open(player, menuId, state)
            return true
        }

        val nextState = state ?: targetMenu.defaultState
        if (reopen || session.menu.rows != targetMenu.rows) {
            open(player, menuId, nextState)
            return true
        }

        val previousMenu = session.menu
        val previousState = session.state

        session.menu = targetMenu
        session.state = nextState
        animations.cancel(session.id)
        renderFull(session, player)

        if (previousMenu.id != targetMenu.id) {
            invokeCloseHandlers(
                menu = previousMenu,
                player = player,
                state = previousState,
                reason = UiCloseReason.REPLACED,
            )
            invokeOpenHandlers(session, player)
        }

        return true
    }

    fun refresh(player: Player): Boolean {
        ensurePrimaryThread("refresh")
        val session = sessions.byPlayerId(player.uniqueId) ?: return false
        return renderFull(session, player)
    }

    fun refresh(
        player: Player,
        slots: IntArray,
    ): Boolean {
        ensurePrimaryThread("refresh(slots)")
        val session = sessions.byPlayerId(player.uniqueId) ?: return false
        return renderSlots(session, player, slots)
    }

    fun setState(
        player: Player,
        state: Any?,
    ): Boolean {
        ensurePrimaryThread("setState")
        val session = sessions.byPlayerId(player.uniqueId) ?: return false
        session.state = state
        return true
    }

    fun updateState(
        player: Player,
        transform: (Any?) -> Any?,
    ): Boolean {
        ensurePrimaryThread("updateState")
        val session = sessions.byPlayerId(player.uniqueId) ?: return false
        session.state = transform(session.state)
        return true
    }

    fun close(player: Player): Boolean {
        ensurePrimaryThread("close")
        val session = sessions.byPlayerId(player.uniqueId) ?: return false
        return requestClose(session.id, UiCloseReason.API, fallbackPlayer = player)
    }

    fun virtualInventory(
        id: UUID,
        size: Int,
    ): VirtualInventory = virtualInventories.getOrCreate(id, size)

    fun animate(
        player: Player,
        slots: IntArray,
        preset: AnimationPreset,
        intervalTicks: Long,
    ): Boolean {
        ensurePrimaryThread("animate")
        val session = sessions.byPlayerId(player.uniqueId) ?: return false
        val target =
            if (slots.isEmpty()) {
                IntArray(session.inventory.size) { it }
            } else {
                slots
            }
        return animations.play(session, target, preset, intervalTicks)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = resolveSession(event.view.topInventory) ?: return
        if (session.playerId != player.uniqueId) return
        if (!processingClicks.add(player.uniqueId)) return

        try {
            if (event.slotType == InventoryType.SlotType.OUTSIDE) {
                invokeOutsideClickHandlers(session, player, event)
                return
            }

            val topSize = event.view.topInventory.size
            val clickedTop = event.rawSlot in 0 until topSize
            if (clickedTop) {
                handleTopClick(session, player, event)
            } else {
                handleBottomClick(session, player, event)
            }
        } finally {
            processingClicks.remove(player.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val session = resolveSession(event.view.topInventory) ?: return
        val player = event.whoClicked as? Player ?: return
        if (session.playerId != player.uniqueId) return

        val topSize = event.view.topInventory.size
        val touchedTop = event.rawSlots.filter { it in 0 until topSize }
        if (touchedTop.isEmpty()) return

        if (session.menu.clickPolicy.cancelDrag) {
            val touchedNonVirtual = touchedTop.any { slot -> slot !in session.virtualBindingsBySlot }
            if (touchedNonVirtual) {
                event.isCancelled = true
                return
            }
        }

        scheduleVirtualSync(session.id, player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val session = resolveSession(event.view.topInventory) ?: return

        val reason = forcedCloseReasons.remove(session.id) ?: UiCloseReason.PLAYER
        if (reason == UiCloseReason.PLAYER && !session.menu.closeable) {
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    val current = Bukkit.getPlayer(player.uniqueId) ?: return@Runnable
                    if (current.openInventory.topInventory.holder !is ManagedInventoryHolder) {
                        current.openInventory(session.inventory)
                    }
                },
            )
            return
        }

        finalizeClose(session.id, player, reason)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val session = sessions.byPlayerId(event.player.uniqueId) ?: return
        finalizeClose(session.id, event.player, UiCloseReason.QUIT)
    }

    private fun handleTopClick(
        session: WindowSession,
        player: Player,
        event: InventoryClickEvent,
    ) {
        val slot = event.rawSlot
        val resolved = session.resolvedSlotsByMenuSlot[slot]
        val virtualBinding = resolved?.virtualBinding ?: session.virtualBindingsBySlot[slot]

        var cancelled = resolved?.cancelClick ?: if (virtualBinding != null) false else session.menu.clickPolicy.cancelTopClicks
        if (virtualBinding != null && !isVirtualInteractionAllowed(virtualBinding, event, clickedTop = true)) {
            cancelled = true
        }

        event.isCancelled = cancelled

        if (virtualBinding != null && !event.isCancelled) {
            scheduleVirtualSync(session.id, player)
        }

        val clickHandler = resolved?.onClick ?: return
        val context =
            createClickContext(
                session = session,
                player = player,
                slot = slot,
                event = event,
                menuId = resolved.menuId,
                state = resolved.state,
            )
        safeInvoke("click handler for menu '${session.menu.id}'") {
            clickHandler.invoke(context)
        }
    }

    private fun handleBottomClick(
        session: WindowSession,
        player: Player,
        event: InventoryClickEvent,
    ) {
        if (session.menu.clickPolicy.cancelBottomClicks) {
            event.isCancelled = true
            return
        }

        if (event.isShiftClick && session.virtualBindingsBySlot.isNotEmpty()) {
            scheduleVirtualSync(session.id, player)
        }
    }

    private fun invokeOutsideClickHandlers(
        session: WindowSession,
        player: Player,
        event: InventoryClickEvent,
    ) {
        if (session.menu.lifecycle.onOutsideClickHandlers.isEmpty()) return

        val context =
            UiOutsideClickContext<Any?>(
                player = player,
                menuId = session.menu.id,
                rawState = session.state,
                event = event,
                virtualInventoryProvider = ::virtualInventory,
                refreshAction = { slots ->
                    if (slots == null) renderFull(session, player) else renderSlots(session, player, slots)
                },
                closeAction = { close(player) },
            )

        session.menu.lifecycle.onOutsideClickHandlers.forEach { handler ->
            safeInvoke("outside click handler for menu '${session.menu.id}'") {
                handler.invoke(context)
            }
        }
    }

    private fun resolveSession(inventory: Inventory): WindowSession? {
        val holder = inventory.holder as? ManagedInventoryHolder ?: return null
        return sessions.bySessionId(holder.sessionId)
    }

    private fun renderFull(
        session: WindowSession,
        player: Player,
    ): Boolean {
        if (currentSessionId(player) != session.id) {
            return false
        }
        val result = renderer.renderFull(session, player)
        applyRenderResult(session, result, full = true)
        return true
    }

    private fun renderSlots(
        session: WindowSession,
        player: Player,
        slots: IntArray,
    ): Boolean {
        if (currentSessionId(player) != session.id) {
            return false
        }
        val result = renderer.renderSlots(session, player, slots)
        applyRenderResult(session, result, full = false)
        return true
    }

    private fun applyRenderResult(
        session: WindowSession,
        result: RenderResult,
        full: Boolean,
    ) {
        if (full) {
            session.resolvedSlotsByMenuSlot.clear()
            session.virtualBindingsBySlot.clear()
        }

        result.renderedSlots.forEach { slot ->
            session.resolvedSlotsByMenuSlot.remove(slot)
            session.virtualBindingsBySlot.remove(slot)
        }

        session.resolvedSlotsByMenuSlot.putAll(result.resolvedSlots)
        result.resolvedSlots.forEach { (slot, resolved) ->
            val virtual = resolved.virtualBinding
            if (virtual != null) {
                session.virtualBindingsBySlot[slot] = virtual
            }
        }

        rebuildVirtualSubscriptions(session)
    }

    private fun rebuildVirtualSubscriptions(session: WindowSession) {
        clearVirtualSubscriptions(session)
        if (session.virtualBindingsBySlot.isEmpty()) return

        val grouped =
            linkedMapOf<UUID, Pair<VirtualInventory, MutableMap<Int, MutableSet<Int>>>>()

        session.virtualBindingsBySlot.forEach { (menuSlot, binding) ->
            val pair = grouped.getOrPut(binding.inventory.id) {
                binding.inventory to linkedMapOf()
            }
            val indexMap = pair.second
            indexMap.getOrPut(binding.index) { linkedSetOf() }.add(menuSlot)
        }

        grouped.forEach { (inventoryId, pair) ->
            val (inventory, mutableMap) = pair
            val indexToSlots = mutableMap.mapValues { (_, slots) -> slots.toSet() }

            val listener: VirtualInventoryListener = { _, changedSlots ->
                val slotsToRefresh =
                    if (changedSlots == null) {
                        indexToSlots.values.flatten().distinct()
                    } else {
                        changedSlots
                            .flatMap { changed -> indexToSlots[changed].orEmpty() }
                            .distinct()
                    }

                if (slotsToRefresh.isNotEmpty()) {
                    if (Bukkit.isPrimaryThread()) {
                        refreshSessionSlots(session.id, slotsToRefresh.toIntArray())
                    } else {
                        Bukkit.getScheduler().runTask(
                            plugin,
                            Runnable {
                                refreshSessionSlots(session.id, slotsToRefresh.toIntArray())
                            },
                        )
                    }
                }
            }

            inventory.addListener(listener)
            session.virtualSubscriptions[inventoryId] =
                VirtualSubscription(
                    inventory = inventory,
                    indexToMenuSlots = indexToSlots,
                    listener = listener,
                )
        }
    }

    private fun clearVirtualSubscriptions(session: WindowSession) {
        session.virtualSubscriptions.values.forEach { subscription ->
            subscription.inventory.removeListener(subscription.listener)
        }
        session.virtualSubscriptions.clear()
    }

    private fun refreshSessionSlots(
        sessionId: UUID,
        slots: IntArray,
    ): Boolean {
        val session = sessions.bySessionId(sessionId) ?: return false
        val player = Bukkit.getPlayer(session.playerId) ?: return false
        return renderSlots(session, player, slots)
    }

    private fun scheduleVirtualSync(
        sessionId: UUID,
        player: Player,
    ) {
        if (!pendingVirtualSync.add(sessionId)) return

        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                pendingVirtualSync.remove(sessionId)
                syncVirtualSlotsFromView(sessionId, player)
            },
        )
    }

    private fun syncVirtualSlotsFromView(
        sessionId: UUID,
        player: Player,
    ) {
        val session = sessions.bySessionId(sessionId) ?: return
        if (currentSessionId(player) != session.id) return

        val top = player.openInventory.topInventory
        session.virtualBindingsBySlot.forEach { (menuSlot, binding) ->
            if (binding.index !in 0 until binding.inventory.size) return@forEach
            val current = top.getItem(menuSlot)
            binding.inventory.setItem(binding.index, current)
        }
    }

    private fun isVirtualInteractionAllowed(
        binding: ResolvedVirtualSlot,
        event: InventoryClickEvent,
        clickedTop: Boolean,
    ): Boolean {
        if (binding.allowInsert && binding.allowTake) return true

        val cursorEmpty = event.cursor.isNullOrAir()
        val currentEmpty = event.currentItem.isNullOrAir()

        if (!binding.allowTake && !currentEmpty && cursorEmpty) return false
        if (!binding.allowInsert && !cursorEmpty) return false

        if (!binding.allowTake && event.isShiftClick && clickedTop) return false
        if (!binding.allowInsert && event.isShiftClick && !clickedTop) return false

        return true
    }

    private fun requestClose(
        sessionId: UUID,
        reason: UiCloseReason,
        fallbackPlayer: Player? = null,
    ): Boolean {
        val session = sessions.bySessionId(sessionId) ?: return false
        val player = fallbackPlayer ?: Bukkit.getPlayer(session.playerId)
        if (player != null && currentSessionId(player) == sessionId) {
            forcedCloseReasons[sessionId] = reason
            player.closeInventory()
            return true
        }

        finalizeClose(sessionId, player, reason)
        return true
    }

    private fun currentSessionId(player: Player): UUID? {
        return (player.openInventory.topInventory.holder as? ManagedInventoryHolder)?.sessionId
    }

    private fun finalizeClose(
        sessionId: UUID,
        player: Player?,
        reason: UiCloseReason,
    ) {
        val session = sessions.remove(sessionId) ?: return
        pendingVirtualSync.remove(sessionId)
        forcedCloseReasons.remove(sessionId)

        animations.cancel(session.id)
        clearVirtualSubscriptions(session)

        val closePlayer = player ?: Bukkit.getPlayer(session.playerId) ?: return
        invokeCloseHandlers(
            menu = session.menu,
            player = closePlayer,
            state = session.state,
            reason = reason,
        )
    }

    private fun invokeOpenHandlers(
        session: WindowSession,
        player: Player,
    ) {
        if (session.menu.lifecycle.onOpenHandlers.isEmpty()) return

        val context =
            UiOpenContext<Any?>(
                player = player,
                menuId = session.menu.id,
                rawState = session.state,
                virtualInventoryProvider = ::virtualInventory,
                refreshAction = { slots ->
                    if (slots == null) renderFull(session, player) else renderSlots(session, player, slots)
                },
                openAction = { menuId, state -> open(player, menuId, state) },
                navigateAction = { menuId, state, reopen -> navigate(player, menuId, state, reopen) },
                setStateAction = { next -> setState(player, next) },
                updateStateAction = { transform -> updateState(player, transform) },
                closeAction = { close(player) },
                animateAction = { slots, preset, interval ->
                    val target = if (slots.isEmpty()) IntArray(session.inventory.size) { it } else slots
                    animations.play(session, target, preset, interval)
                },
            )

        session.menu.lifecycle.onOpenHandlers.forEach { handler ->
            safeInvoke("open handler for menu '${session.menu.id}'") {
                handler.invoke(context)
            }
        }
    }

    private fun invokeCloseHandlers(
        menu: MenuModel,
        player: Player,
        state: Any?,
        reason: UiCloseReason,
    ) {
        if (menu.lifecycle.onCloseHandlers.isEmpty()) return

        val context =
            UiCloseContext<Any?>(
                player = player,
                menuId = menu.id,
                rawState = state,
                reason = reason,
                virtualInventoryProvider = ::virtualInventory,
            )

        menu.lifecycle.onCloseHandlers.forEach { handler ->
            safeInvoke("close handler for menu '${menu.id}'") {
                handler.invoke(context)
            }
        }
    }

    private fun createClickContext(
        session: WindowSession,
        player: Player,
        slot: Int,
        event: InventoryClickEvent,
        menuId: String,
        state: Any?,
    ): UiClickContext<Any?> {
        return UiClickContext(
            player = player,
            menuId = menuId,
            rawState = state,
            slot = slot,
            click = event.click,
            event = event,
            virtualInventoryProvider = ::virtualInventory,
            refreshAction = { slots ->
                if (slots == null) renderFull(session, player) else renderSlots(session, player, slots)
            },
            openAction = { menuId, state -> open(player, menuId, state) },
            navigateAction = { menuId, state, reopen -> navigate(player, menuId, state, reopen) },
            setStateAction = { next -> setState(player, next) },
            updateStateAction = { transform -> updateState(player, transform) },
            closeAction = { close(player) },
            animateAction = { slots, preset, interval ->
                val target = if (slots.isEmpty()) IntArray(session.inventory.size) { it } else slots
                animations.play(session, target, preset, interval)
            },
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
            virtualInventoryProvider = ::virtualInventory,
        )
    }

    private fun resolveMenu(menuId: String): MenuModel {
        return menus[menuId.lowercase()] ?: error("Menu '$menuId' is not registered")
    }

    private fun ensurePrimaryThread(operation: String) {
        check(Bukkit.isPrimaryThread()) {
            "kontainer-ui-lib operation '$operation' must run on main thread"
        }
    }

    private fun safeInvoke(
        description: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (error: Throwable) {
            plugin.logger.severe("kontainer-ui-lib failed in $description: ${error.message}")
            error.printStackTrace()
        }
    }
}

private fun ItemStack?.isNullOrAir(): Boolean = this == null || type.isAir
