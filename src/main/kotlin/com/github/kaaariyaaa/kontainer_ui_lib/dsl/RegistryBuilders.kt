package com.github.kaaariyaaa.kontainer_ui_lib.dsl

import com.github.kaaariyaaa.kontainer_ui_lib.context.UiClickContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiCloseContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiOpenContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiOutsideClickContext
import com.github.kaaariyaaa.kontainer_ui_lib.context.UiRenderContext
import com.github.kaaariyaaa.kontainer_ui_lib.model.ClickPolicy
import com.github.kaaariyaaa.kontainer_ui_lib.model.MenuLifecycle
import com.github.kaaariyaaa.kontainer_ui_lib.model.MenuModel
import com.github.kaaariyaaa.kontainer_ui_lib.model.NestedSlotSpec
import com.github.kaaariyaaa.kontainer_ui_lib.model.SlotModel
import com.github.kaaariyaaa.kontainer_ui_lib.model.VirtualSlotSpec
import com.github.kaaariyaaa.kontainer_ui_lib.persistence.PersistenceOptions
import com.github.kaaariyaaa.kontainer_ui_lib.persistence.VirtualInventoryRepository
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualInventory
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.VirtualMapping
import com.github.kaaariyaaa.kontainer_ui_lib.virtual.sequential
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

@KontainerUiDsl
class KontainerRegistry internal constructor() {
    private val menus = linkedMapOf<String, MenuModel>()
    private var persistenceOptions = PersistenceOptions()

    fun persistence(block: PersistenceBuilder.() -> Unit) {
        val builder = PersistenceBuilder()
        builder.block()
        persistenceOptions = builder.build()
    }

    @JvmName("menuTyped")
    inline fun <reified S> menu(
        id: String,
        noinline block: MenuBuilder<S>.() -> Unit,
    ) {
        registerMenu(
            id = id,
            stateType = S::class,
            isUnitState = S::class == Unit::class,
            block = block,
        )
    }

    fun menu(
        id: String,
        block: MenuBuilder<Unit>.() -> Unit,
    ) {
        registerMenu(
            id = id,
            stateType = Unit::class,
            isUnitState = true,
            block = block,
        )
    }

    internal fun build(): RegistryBuildResult =
        RegistryBuildResult(
            menus = menus.toMap(),
            persistenceOptions = persistenceOptions,
        )

    @PublishedApi
    internal fun <S> registerMenu(
        id: String,
        stateType: KClass<*>,
        isUnitState: Boolean,
        block: MenuBuilder<S>.() -> Unit,
    ) {
        val normalizedId = normalizeId(id)
        require(normalizedId.isNotEmpty()) { "Menu id must not be blank." }
        check(!menus.containsKey(normalizedId)) { "Menu '$normalizedId' is already registered." }

        val builder = MenuBuilder<S>(normalizedId, stateType, isUnitState)
        builder.block()
        menus[normalizedId] = builder.build()
    }

    @PublishedApi
    internal fun normalizeId(id: String): String = id.trim().lowercase()
}

internal data class RegistryBuildResult(
    val menus: Map<String, MenuModel>,
    val persistenceOptions: PersistenceOptions,
)

@KontainerUiDsl
class PersistenceBuilder internal constructor() {
    private var repository: VirtualInventoryRepository? = null
    var autoSaveIntervalTicks: Long = 100L

    fun repository(repository: VirtualInventoryRepository) {
        this.repository = repository
    }

    internal fun build(): PersistenceOptions =
        PersistenceOptions(
            repository = repository,
            autoSaveIntervalTicks = autoSaveIntervalTicks,
        )
}

@KontainerUiDsl
class MenuBuilder<S> internal constructor(
    private val id: String,
    private val stateType: KClass<*>,
    private val isUnitState: Boolean,
) {
    var rows: Int = 3
    var closeable: Boolean = true
    var cancelTopClicks: Boolean = true
    var cancelBottomClicks: Boolean = false
    var cancelDrag: Boolean = true

    private var titleProvider: (UiRenderContext<S>.() -> Component)? = null
    private var structureRows: List<String>? = null

    private val bindTemplates = linkedMapOf<Char, SlotTemplate<S>>()
    private val slotTemplates = linkedMapOf<Int, SlotTemplate<S>>()
    private val pagedTemplates = mutableListOf<PagedTemplate<S, *>>()
    private val scrollTemplates = mutableListOf<ScrollTemplate<S, *>>()
    private val tabContentTemplates = mutableListOf<TabContentTemplate<S, *>>()
    private val tabSelectorTemplates = mutableListOf<TabSelectorTemplate<S>>()

    private val onOpenHandlers = mutableListOf<UiOpenContext<S>.() -> Unit>()
    private val onCloseHandlers = mutableListOf<UiCloseContext<S>.() -> Unit>()
    private val onOutsideClickHandlers = mutableListOf<UiOutsideClickContext<S>.() -> Unit>()

    fun title(text: String) {
        titleProvider = { Component.text(text) }
    }

    fun title(component: Component) {
        titleProvider = { component }
    }

    fun title(block: UiRenderContext<S>.() -> Component) {
        titleProvider = block
    }

    fun structure(vararg rows: String) {
        require(rows.isNotEmpty()) { "structure must contain at least one row" }
        structureRows = rows.toList()
    }

    fun bind(
        symbol: Char,
        block: SlotBuilder<S>.() -> Unit,
    ) {
        check(!bindTemplates.containsKey(symbol)) {
            "bind('$symbol') is already defined in menu '$id'."
        }

        val builder = SlotBuilder<S>()
        builder.block()
        bindTemplates[symbol] = builder.buildTemplate()
    }

    fun slot(
        index: Int,
        block: SlotBuilder<S>.() -> Unit,
    ) {
        require(index >= 0) { "slot index must be >= 0" }
        val builder = SlotBuilder<S>()
        builder.block()
        slotTemplates[index] = builder.buildTemplate()
    }

    fun <T> paged(
        symbol: Char,
        entries: UiRenderContext<S>.() -> List<T>,
        page: UiRenderContext<S>.() -> Int,
        item: UiRenderContext<S>.(entry: T, absoluteIndex: Int) -> ItemStack?,
        onClick: (UiClickContext<S>.(entry: T, absoluteIndex: Int) -> Unit)? = null,
        cancelClick: Boolean = false,
    ) {
        pagedTemplates +=
            PagedTemplate(
                symbol = symbol,
                entries = entries,
                page = page,
                item = item,
                onClick = onClick,
                cancelClick = cancelClick,
            )
    }

    fun <T> scroll(
        symbol: Char,
        entries: UiRenderContext<S>.() -> List<T>,
        offset: UiRenderContext<S>.() -> Int,
        item: UiRenderContext<S>.(entry: T, absoluteIndex: Int) -> ItemStack?,
        onClick: (UiClickContext<S>.(entry: T, absoluteIndex: Int) -> Unit)? = null,
        cancelClick: Boolean = false,
    ) {
        scrollTemplates +=
            ScrollTemplate(
                symbol = symbol,
                entries = entries,
                offset = offset,
                item = item,
                onClick = onClick,
                cancelClick = cancelClick,
            )
    }

    fun <T> tabContent(
        symbol: Char,
        tabs: UiRenderContext<S>.() -> List<List<T>>,
        selectedTab: UiRenderContext<S>.() -> Int,
        item: UiRenderContext<S>.(entry: T, tabIndex: Int, indexInTab: Int) -> ItemStack?,
        onClick: (UiClickContext<S>.(entry: T, tabIndex: Int, indexInTab: Int) -> Unit)? = null,
        cancelClick: Boolean = false,
    ) {
        tabContentTemplates +=
            TabContentTemplate(
                symbol = symbol,
                tabs = tabs,
                selectedTab = selectedTab,
                item = item,
                onClick = onClick,
                cancelClick = cancelClick,
            )
    }

    fun tabSelectors(
        symbol: Char,
        tabCount: UiRenderContext<S>.() -> Int,
        selectedTab: UiRenderContext<S>.() -> Int,
        item: UiRenderContext<S>.(tabIndex: Int, selected: Boolean) -> ItemStack?,
        onSelect: UiClickContext<S>.(tabIndex: Int) -> Unit,
        cancelClick: Boolean = true,
    ) {
        tabSelectorTemplates +=
            TabSelectorTemplate(
                symbol = symbol,
                tabCount = tabCount,
                selectedTab = selectedTab,
                item = item,
                onSelect = onSelect,
                cancelClick = cancelClick,
            )
    }

    fun onOpen(block: UiOpenContext<S>.() -> Unit) {
        onOpenHandlers += block
    }

    fun onClose(block: UiCloseContext<S>.() -> Unit) {
        onCloseHandlers += block
    }

    fun onOutsideClick(block: UiOutsideClickContext<S>.() -> Unit) {
        onOutsideClickHandlers += block
    }

    internal fun build(): MenuModel {
        require(rows in 1..6) { "rows must be in 1..6 (menu '$id')" }

        val resolvedSlots = linkedMapOf<Int, SlotModel>()
        val structureLookup = resolveStructure()

        bindTemplates.forEach { (symbol, template) ->
            val indices = structureLookup[symbol]
                ?: error("bind('$symbol') is defined but structure does not contain '$symbol' in menu '$id'.")

            indices.forEachIndexed { position, slotIndex ->
                resolvedSlots[slotIndex] = template.materialize(position, slotIndex)
            }
        }

        applyCompositions(structureLookup, resolvedSlots)

        val maxSlots = rows * 9
        slotTemplates.forEach { (slotIndex, template) ->
            require(slotIndex in 0 until maxSlots) {
                "slot index $slotIndex is out of range for menu '$id' (rows=$rows)."
            }
            resolvedSlots[slotIndex] = template.materialize(position = 0, menuSlot = slotIndex)
        }

        val titleResolver = titleProvider

        return MenuModel(
            id = id,
            rows = rows,
            defaultState = if (isUnitState) Unit else null,
            titleProvider = { ctx ->
                if (titleResolver == null) {
                    Component.text(id)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    titleResolver.invoke(ctx as UiRenderContext<S>)
                }
            },
            clickPolicy =
                ClickPolicy(
                    cancelTopClicks = cancelTopClicks,
                    cancelBottomClicks = cancelBottomClicks,
                    cancelDrag = cancelDrag,
                ),
            closeable = closeable,
            slots = resolvedSlots.toMap(),
            lifecycle =
                MenuLifecycle(
                    onOpenHandlers = onOpenHandlers.map { handler ->
                        { @Suppress("UNCHECKED_CAST") (this as UiOpenContext<S>).handler() }
                    },
                    onCloseHandlers = onCloseHandlers.map { handler ->
                        { @Suppress("UNCHECKED_CAST") (this as UiCloseContext<S>).handler() }
                    },
                    onOutsideClickHandlers = onOutsideClickHandlers.map { handler ->
                        { @Suppress("UNCHECKED_CAST") (this as UiOutsideClickContext<S>).handler() }
                    },
                ),
        )
    }

    private fun applyCompositions(
        structureLookup: Map<Char, List<Int>>,
        resolvedSlots: MutableMap<Int, SlotModel>,
    ) {
        pagedTemplates.forEach { template ->
            val slots = structureLookup[template.symbol]
                ?: error("paged('${template.symbol}') is defined but structure does not contain '${template.symbol}' in menu '$id'.")
            slots.forEachIndexed { position, slotIndex ->
                resolvedSlots[slotIndex] = template.materialize(position, slotIndex, slots.size)
            }
        }

        scrollTemplates.forEach { template ->
            val slots = structureLookup[template.symbol]
                ?: error("scroll('${template.symbol}') is defined but structure does not contain '${template.symbol}' in menu '$id'.")
            slots.forEachIndexed { position, slotIndex ->
                resolvedSlots[slotIndex] = template.materialize(position, slotIndex)
            }
        }

        tabContentTemplates.forEach { template ->
            val slots = structureLookup[template.symbol]
                ?: error("tabContent('${template.symbol}') is defined but structure does not contain '${template.symbol}' in menu '$id'.")
            slots.forEachIndexed { position, slotIndex ->
                resolvedSlots[slotIndex] = template.materialize(position, slotIndex)
            }
        }

        tabSelectorTemplates.forEach { template ->
            val slots = structureLookup[template.symbol]
                ?: error("tabSelectors('${template.symbol}') is defined but structure does not contain '${template.symbol}' in menu '$id'.")
            slots.forEachIndexed { position, slotIndex ->
                resolvedSlots[slotIndex] = template.materialize(position, slotIndex)
            }
        }
    }

    private fun resolveStructure(): Map<Char, List<Int>> {
        val structure = structureRows ?: return emptyMap()
        require(structure.size == rows) {
            "structure row count (${structure.size}) must match rows ($rows) in menu '$id'."
        }

        val mapping = linkedMapOf<Char, MutableList<Int>>()
        structure.forEachIndexed { rowIndex, row ->
            require(row.length == 9) {
                "structure row $rowIndex length must be 9 in menu '$id'."
            }
            row.forEachIndexed { columnIndex, symbol ->
                if (symbol == '.') return@forEachIndexed
                val slot = (rowIndex * 9) + columnIndex
                mapping.getOrPut(symbol) { mutableListOf() }.add(slot)
            }
        }

        return mapping.mapValues { (_, value) -> value.toList() }
    }
}

@KontainerUiDsl
class SlotBuilder<S> internal constructor() {
    var cancelClick: Boolean? = null

    private var itemProvider: UiRenderContext<S>.() -> ItemStack? = { null }
    private var visibleWhen: UiRenderContext<S>.() -> Boolean = { true }
    private var onClick: (UiClickContext<S>.() -> Unit)? = null
    private var virtualTemplate: VirtualTemplate<S>? = null
    private var nestedTemplate: NestedTemplate<S>? = null

    fun item(item: ItemStack?) {
        val snapshot = item?.clone()
        itemProvider = { snapshot?.clone() }
    }

    fun item(block: UiRenderContext<S>.() -> ItemStack?) {
        itemProvider = { block()?.clone() }
    }

    fun visibleWhen(block: UiRenderContext<S>.() -> Boolean) {
        visibleWhen = block
    }

    fun onClick(block: UiClickContext<S>.() -> Unit) {
        onClick = block
    }

    fun virtual(
        inventory: VirtualInventory,
        index: Int? = null,
        mapping: VirtualMapping = sequential(),
        allowTake: Boolean = true,
        allowInsert: Boolean = true,
    ) {
        virtual(
            inventory = { inventory },
            index = index,
            mapping = mapping,
            allowTake = allowTake,
            allowInsert = allowInsert,
        )
    }

    fun virtual(
        inventory: UiRenderContext<S>.() -> VirtualInventory,
        index: Int? = null,
        mapping: VirtualMapping = sequential(),
        allowTake: Boolean = true,
        allowInsert: Boolean = true,
    ) {
        if (index != null) {
            require(index >= 0) { "virtual index must be >= 0" }
        }

        virtualTemplate =
            VirtualTemplate(
                inventoryProvider = inventory,
                fixedIndex = index,
                mapping = mapping,
                allowTake = allowTake,
                allowInsert = allowInsert,
            )
    }

    fun nested(
        menuId: String,
        index: Int? = null,
        mapping: VirtualMapping = sequential(),
        state: UiRenderContext<S>.() -> Any? = { this.stateOrNull() },
    ) {
        nested(
            menuId = { menuId },
            index = index,
            mapping = mapping,
            state = state,
        )
    }

    fun nested(
        menuId: UiRenderContext<S>.() -> String,
        index: Int? = null,
        mapping: VirtualMapping = sequential(),
        state: UiRenderContext<S>.() -> Any? = { this.stateOrNull() },
    ) {
        if (index != null) {
            require(index >= 0) { "nested index must be >= 0" }
        }

        nestedTemplate =
            NestedTemplate(
                menuIdProvider = menuId,
                fixedIndex = index,
                mapping = mapping,
                stateProvider = state,
            )
    }

    internal fun buildTemplate(): SlotTemplate<S> =
        SlotTemplate(
            itemProvider = itemProvider,
            visibleWhen = visibleWhen,
            onClick = onClick,
            cancelClick = cancelClick,
            virtualTemplate = virtualTemplate,
            nestedTemplate = nestedTemplate,
        )
}

internal data class SlotTemplate<S>(
    val itemProvider: UiRenderContext<S>.() -> ItemStack?,
    val visibleWhen: UiRenderContext<S>.() -> Boolean,
    val onClick: (UiClickContext<S>.() -> Unit)?,
    val cancelClick: Boolean?,
    val virtualTemplate: VirtualTemplate<S>?,
    val nestedTemplate: NestedTemplate<S>?,
) {
    fun materialize(
        position: Int,
        menuSlot: Int,
    ): SlotModel {
        val virtual = virtualTemplate
        val nested = nestedTemplate

        val cancel = cancelClick ?: if (virtual != null || nested != null) false else null

        return SlotModel(
            itemProvider = { ctx ->
                @Suppress("UNCHECKED_CAST")
                itemProvider.invoke(ctx as UiRenderContext<S>)?.clone()
            },
            visibleWhen = { ctx ->
                @Suppress("UNCHECKED_CAST")
                visibleWhen.invoke(ctx as UiRenderContext<S>)
            },
            onClick = onClick?.let { handler ->
                {
                    @Suppress("UNCHECKED_CAST")
                    handler.invoke(this as UiClickContext<S>)
                }
            },
            cancelClick = cancel,
            virtualSpec =
                virtual?.let { spec ->
                    VirtualSlotSpec(
                        inventoryProvider = { ctx ->
                            @Suppress("UNCHECKED_CAST")
                            spec.inventoryProvider.invoke(ctx as UiRenderContext<S>)
                        },
                        indexProvider = { _, _ ->
                            spec.fixedIndex ?: spec.mapping.resolve(position, menuSlot)
                        },
                        allowTake = spec.allowTake,
                        allowInsert = spec.allowInsert,
                    )
                },
            nestedSpec =
                nested?.let { spec ->
                    NestedSlotSpec(
                        menuIdProvider = { ctx ->
                            @Suppress("UNCHECKED_CAST")
                            spec.menuIdProvider.invoke(ctx as UiRenderContext<S>)
                        },
                        stateProvider = { ctx ->
                            @Suppress("UNCHECKED_CAST")
                            spec.stateProvider.invoke(ctx as UiRenderContext<S>)
                        },
                        slotProvider = { _, _ ->
                            spec.fixedIndex ?: spec.mapping.resolve(position, menuSlot)
                        },
                    )
                },
        )
    }
}

internal data class VirtualTemplate<S>(
    val inventoryProvider: UiRenderContext<S>.() -> VirtualInventory,
    val fixedIndex: Int?,
    val mapping: VirtualMapping,
    val allowTake: Boolean,
    val allowInsert: Boolean,
)

internal data class NestedTemplate<S>(
    val menuIdProvider: UiRenderContext<S>.() -> String,
    val fixedIndex: Int?,
    val mapping: VirtualMapping,
    val stateProvider: UiRenderContext<S>.() -> Any?,
)

private data class PagedTemplate<S, T>(
    val symbol: Char,
    val entries: UiRenderContext<S>.() -> List<T>,
    val page: UiRenderContext<S>.() -> Int,
    val item: UiRenderContext<S>.(entry: T, absoluteIndex: Int) -> ItemStack?,
    val onClick: (UiClickContext<S>.(entry: T, absoluteIndex: Int) -> Unit)?,
    val cancelClick: Boolean,
) {
    fun materialize(
        position: Int,
        menuSlot: Int,
        pageSize: Int,
    ): SlotModel {
        val template =
            SlotTemplate(
                itemProvider = {
                    val list = entries(this)
                    val pageIndex = page(this).coerceAtLeast(0)
                    val absoluteIndex = (pageIndex * pageSize) + position
                    val entry = list.getOrNull(absoluteIndex)
                    if (entry == null) null else item(entry, absoluteIndex)
                },
                visibleWhen = { true },
                onClick =
                    onClick?.let { click ->
                        {
                            val renderContext =
                                UiRenderContext<S>(
                                    player = player,
                                    menuId = menuId,
                                    rawState = stateOrNull(),
                                    virtualInventoryProvider = { id, size -> virtualInventory(id, size) },
                                )

                            val list = entries(renderContext)
                            val pageIndex = page(renderContext).coerceAtLeast(0)
                            val absoluteIndex = (pageIndex * pageSize) + position
                            val entry = list.getOrNull(absoluteIndex)
                            if (entry != null) {
                                click(entry, absoluteIndex)
                            }
                        }
                    },
                cancelClick = cancelClick,
                virtualTemplate = null,
                nestedTemplate = null,
            )

        return template.materialize(position, menuSlot)
    }
}

private data class ScrollTemplate<S, T>(
    val symbol: Char,
    val entries: UiRenderContext<S>.() -> List<T>,
    val offset: UiRenderContext<S>.() -> Int,
    val item: UiRenderContext<S>.(entry: T, absoluteIndex: Int) -> ItemStack?,
    val onClick: (UiClickContext<S>.(entry: T, absoluteIndex: Int) -> Unit)?,
    val cancelClick: Boolean,
) {
    fun materialize(
        position: Int,
        menuSlot: Int,
    ): SlotModel {
        val template =
            SlotTemplate(
                itemProvider = {
                    val list = entries(this)
                    val offsetIndex = offset(this).coerceAtLeast(0)
                    val absoluteIndex = offsetIndex + position
                    val entry = list.getOrNull(absoluteIndex)
                    if (entry == null) null else item(entry, absoluteIndex)
                },
                visibleWhen = { true },
                onClick =
                    onClick?.let { click ->
                        {
                            val renderContext =
                                UiRenderContext<S>(
                                    player = player,
                                    menuId = menuId,
                                    rawState = stateOrNull(),
                                    virtualInventoryProvider = { id, size -> virtualInventory(id, size) },
                                )

                            val list = entries(renderContext)
                            val offsetIndex = offset(renderContext).coerceAtLeast(0)
                            val absoluteIndex = offsetIndex + position
                            val entry = list.getOrNull(absoluteIndex)
                            if (entry != null) {
                                click(entry, absoluteIndex)
                            }
                        }
                    },
                cancelClick = cancelClick,
                virtualTemplate = null,
                nestedTemplate = null,
            )

        return template.materialize(position, menuSlot)
    }
}

private data class TabContentTemplate<S, T>(
    val symbol: Char,
    val tabs: UiRenderContext<S>.() -> List<List<T>>,
    val selectedTab: UiRenderContext<S>.() -> Int,
    val item: UiRenderContext<S>.(entry: T, tabIndex: Int, indexInTab: Int) -> ItemStack?,
    val onClick: (UiClickContext<S>.(entry: T, tabIndex: Int, indexInTab: Int) -> Unit)?,
    val cancelClick: Boolean,
) {
    fun materialize(
        position: Int,
        menuSlot: Int,
    ): SlotModel {
        val template =
            SlotTemplate(
                itemProvider = {
                    val tabList = tabs(this)
                    if (tabList.isEmpty()) {
                        null
                    } else {
                    val tabIndex = selectedTab(this).coerceIn(0, tabList.lastIndex)
                    val entries = tabList[tabIndex]
                    val entry = entries.getOrNull(position)
                    if (entry == null) null else item(entry, tabIndex, position)
                    }
                },
                visibleWhen = { true },
                onClick =
                    onClick?.let { click ->
                        {
                            val renderContext =
                                UiRenderContext<S>(
                                    player = player,
                                    menuId = menuId,
                                    rawState = stateOrNull(),
                                    virtualInventoryProvider = { id, size -> virtualInventory(id, size) },
                                )

                            val tabList = tabs(renderContext)
                            if (tabList.isNotEmpty()) {
                                val tabIndex = selectedTab(renderContext).coerceIn(0, tabList.lastIndex)
                                val entries = tabList[tabIndex]
                                val entry = entries.getOrNull(position)
                                if (entry != null) {
                                    click(entry, tabIndex, position)
                                }
                            }
                        }
                    },
                cancelClick = cancelClick,
                virtualTemplate = null,
                nestedTemplate = null,
            )

        return template.materialize(position, menuSlot)
    }
}

private data class TabSelectorTemplate<S>(
    val symbol: Char,
    val tabCount: UiRenderContext<S>.() -> Int,
    val selectedTab: UiRenderContext<S>.() -> Int,
    val item: UiRenderContext<S>.(tabIndex: Int, selected: Boolean) -> ItemStack?,
    val onSelect: UiClickContext<S>.(tabIndex: Int) -> Unit,
    val cancelClick: Boolean,
) {
    fun materialize(
        position: Int,
        menuSlot: Int,
    ): SlotModel {
        val template =
            SlotTemplate(
                itemProvider = {
                    val count = tabCount(this).coerceAtLeast(0)
                    if (position >= count) {
                        null
                    } else {
                        val selectedIndex = selectedTab(this).coerceAtLeast(0)
                        item(position, selectedIndex == position)
                    }
                },
                visibleWhen = {
                    val count = tabCount(this).coerceAtLeast(0)
                    position < count
                },
                onClick = {
                    onSelect(position)
                },
                cancelClick = cancelClick,
                virtualTemplate = null,
                nestedTemplate = null,
            )

        return template.materialize(position, menuSlot)
    }
}
