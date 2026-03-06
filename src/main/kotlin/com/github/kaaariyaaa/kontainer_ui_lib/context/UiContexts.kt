package com.github.kaaariyaaa.kontainer_ui_lib.context

import com.github.kaaariyaaa.kontainer_ui_lib.animation.AnimationPreset
import com.github.kaaariyaaa.kontainer_ui_lib.animation.stagger
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventory
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.UUID

enum class UiCloseReason {
    PLAYER,
    REPLACED,
    API,
    UNREGISTER,
    QUIT,
}

class UiRenderContext<S> internal constructor(
    val player: Player,
    val menuId: String,
    private val rawState: Any?,
    private val virtualInventoryProvider: (UUID, Int) -> VirtualInventory,
) {
    @Suppress("UNCHECKED_CAST")
    val state: S
        get() = rawState as S

    @Suppress("UNCHECKED_CAST")
    fun stateOrNull(): S? = rawState as? S

    fun virtualInventory(
        id: UUID,
        size: Int,
    ): VirtualInventory = virtualInventoryProvider(id, size)
}

class UiOpenContext<S> internal constructor(
    val player: Player,
    val menuId: String,
    private val rawState: Any?,
    private val virtualInventoryProvider: (UUID, Int) -> VirtualInventory,
    private val refreshAction: (IntArray?) -> Boolean,
    private val openAction: (String, Any?) -> Unit,
    private val navigateAction: (String, Any?, Boolean) -> Boolean,
    private val setStateAction: (Any?) -> Boolean,
    private val updateStateAction: ((Any?) -> Any?) -> Boolean,
    private val closeAction: () -> Boolean,
    private val animateAction: (IntArray, AnimationPreset, Long) -> Boolean,
) {
    @Suppress("UNCHECKED_CAST")
    val state: S
        get() = rawState as S

    @Suppress("UNCHECKED_CAST")
    fun stateOrNull(): S? = rawState as? S

    fun virtualInventory(
        id: UUID,
        size: Int,
    ): VirtualInventory = virtualInventoryProvider(id, size)

    fun refresh(): Boolean = refreshAction(null)

    fun refresh(vararg slots: Int): Boolean = refreshAction(slots)

    fun open(
        menuId: String,
        state: Any? = rawState,
    ) {
        openAction(menuId, state)
    }

    fun navigate(
        menuId: String,
        state: Any? = rawState,
        reopen: Boolean = false,
    ): Boolean = navigateAction(menuId, state, reopen)

    fun setState(state: S): Boolean = setStateAction(state)

    fun updateState(transform: (S) -> S): Boolean {
        return updateStateAction { current ->
            @Suppress("UNCHECKED_CAST")
            transform(current as S)
        }
    }

    fun updateStateAndRefresh(transform: (S) -> S): Boolean {
        val updated = updateState(transform)
        if (!updated) return false
        return refresh()
    }

    fun close(): Boolean = closeAction()

    fun animate(
        vararg slots: Int,
        preset: AnimationPreset = stagger(),
        intervalTicks: Long = 1L,
    ): Boolean = animateAction(slots, preset, intervalTicks)
}

class UiClickContext<S> internal constructor(
    val player: Player,
    val menuId: String,
    private val rawState: Any?,
    val slot: Int,
    val click: ClickType,
    val event: InventoryClickEvent,
    private val virtualInventoryProvider: (UUID, Int) -> VirtualInventory,
    private val refreshAction: (IntArray?) -> Boolean,
    private val openAction: (String, Any?) -> Unit,
    private val navigateAction: (String, Any?, Boolean) -> Boolean,
    private val setStateAction: (Any?) -> Boolean,
    private val updateStateAction: ((Any?) -> Any?) -> Boolean,
    private val closeAction: () -> Boolean,
    private val animateAction: (IntArray, AnimationPreset, Long) -> Boolean,
) {
    @Suppress("UNCHECKED_CAST")
    val state: S
        get() = rawState as S

    @Suppress("UNCHECKED_CAST")
    fun stateOrNull(): S? = rawState as? S

    fun virtualInventory(
        id: UUID,
        size: Int,
    ): VirtualInventory = virtualInventoryProvider(id, size)

    fun refresh(): Boolean = refreshAction(null)

    fun refresh(vararg slots: Int): Boolean = refreshAction(slots)

    fun open(
        menuId: String,
        state: Any? = rawState,
    ) {
        openAction(menuId, state)
    }

    fun navigate(
        menuId: String,
        state: Any? = rawState,
        reopen: Boolean = false,
    ): Boolean = navigateAction(menuId, state, reopen)

    fun setState(state: S): Boolean = setStateAction(state)

    fun updateState(transform: (S) -> S): Boolean {
        return updateStateAction { current ->
            @Suppress("UNCHECKED_CAST")
            transform(current as S)
        }
    }

    fun updateStateAndRefresh(transform: (S) -> S): Boolean {
        val updated = updateState(transform)
        if (!updated) return false
        return refresh()
    }

    fun close(): Boolean = closeAction()

    fun animate(
        vararg slots: Int,
        preset: AnimationPreset = stagger(),
        intervalTicks: Long = 1L,
    ): Boolean = animateAction(slots, preset, intervalTicks)
}

class UiOutsideClickContext<S> internal constructor(
    val player: Player,
    val menuId: String,
    private val rawState: Any?,
    val event: InventoryClickEvent,
    private val virtualInventoryProvider: (UUID, Int) -> VirtualInventory,
    private val refreshAction: (IntArray?) -> Boolean,
    private val closeAction: () -> Boolean,
) {
    @Suppress("UNCHECKED_CAST")
    val state: S
        get() = rawState as S

    @Suppress("UNCHECKED_CAST")
    fun stateOrNull(): S? = rawState as? S

    fun virtualInventory(
        id: UUID,
        size: Int,
    ): VirtualInventory = virtualInventoryProvider(id, size)

    fun refresh(): Boolean = refreshAction(null)

    fun refresh(vararg slots: Int): Boolean = refreshAction(slots)

    fun close(): Boolean = closeAction()
}

class UiCloseContext<S> internal constructor(
    val player: Player,
    val menuId: String,
    private val rawState: Any?,
    val reason: UiCloseReason,
    private val virtualInventoryProvider: (UUID, Int) -> VirtualInventory,
) {
    @Suppress("UNCHECKED_CAST")
    val state: S
        get() = rawState as S

    @Suppress("UNCHECKED_CAST")
    fun stateOrNull(): S? = rawState as? S

    fun virtualInventory(
        id: UUID,
        size: Int,
    ): VirtualInventory = virtualInventoryProvider(id, size)
}
