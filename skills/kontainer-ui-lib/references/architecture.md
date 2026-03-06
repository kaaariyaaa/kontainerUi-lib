# Architecture Reference

## Core Layers

- Public API: `KontainerUi.kt`
- DSL compilation: `dsl/RegistryBuilders.kt`
- Model types: `model/MenuModel.kt`
- Runtime and event handling: `runtime/UiRuntime.kt`
- Rendering pipeline: `runtime/MenuRenderer.kt`
- Virtual inventory and persistence SPI:
  - `virtual/VirtualInventory.kt`
  - `virtual/VirtualInventoryManager.kt`
  - `persistence/VirtualInventoryRepository.kt`

## High-level Data Flow

1. User defines menus via DSL (`menu`, `structure`, `bind`, `paged`, `scroll`, `tabContent`, `tabSelectors`, `nested`, `virtual`).
2. DSL compiles to immutable `MenuModel` instances.
3. Runtime creates and tracks per-player `WindowSession`.
4. Renderer resolves slots (including nested and virtual bindings) and writes inventory items.
5. Runtime routes click/drag/close events through resolved slot runtime data.
6. Virtual inventory updates propagate to UI via slot refresh.

## Behavioral Priorities

- Main-thread safety for UI operations.
- Correct session ownership checks.
- Stable navigation behavior (`navigate` vs reopen).
- Persistence remains implementation-agnostic through repository SPI.
