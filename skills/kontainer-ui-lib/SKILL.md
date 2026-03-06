---
name: kontainer-ui-lib-dev
description: Use this skill whenever the user asks to implement, refactor, debug, or document anything in the kontainer-ui-lib repository (Kotlin DSL, runtime, VirtualInventory, persistence SPI, animation presets, Nix/Gradle build flow). Always use this skill for repository-local changes, even if the user only asks for a small fix.
---

# kontainer-ui-lib Developer Skill

This skill provides the standard workflow for working inside `kontainer-ui-lib`.

## Goals

- Keep changes aligned with the current v2 DSL direction (`structure + bind`).
- Preserve runtime behavior around session safety, virtual inventory sync, and main-thread operations.
- Build and verify changes in the Nix environment.

## Project Context

Read these files first when you need architecture context:

- `README.md`
- `src/main/kotlin/com/github/kaaariyaaa/kontainer_ui_lib/KontainerUi.kt`
- `src/main/kotlin/com/github/kaaariyaaa/kontainer_ui_lib/dsl/RegistryBuilders.kt`
- `src/main/kotlin/com/github/kaaariyaaa/kontainer_ui_lib/runtime/UiRuntime.kt`
- `src/main/kotlin/com/github/kaaariyaaa/kontainer_ui_lib/virtual/VirtualInventory.kt`

If you need quick references, use:

- `references/architecture.md`
- `references/build-and-run.md`

## Workflow

1. Identify the target layer before editing:
   - DSL/compiler surface: `dsl/`
   - Runtime/session/event behavior: `runtime/`
   - Data model: `model/`
   - Virtual inventory and persistence SPI: `virtual/`, `persistence/`
   - Public API: `KontainerUi.kt`, `context/`
2. Make minimal, coherent edits across layers (avoid one-off hacks in runtime).
3. Ensure API changes are reflected in:
   - context classes
   - runtime call sites
   - docs/examples if behavior changed
4. Build in Nix:
   - `nix develop -c ./gradlew clean build`
5. Summarize:
   - what changed
   - why it changed
   - what was validated

## DSL Conventions

- Keep the primary layout style as `structure(...) + bind('X')`.
- `slot(index)` is for explicit overrides and edge cases.
- Prefer typed state menus (`menu<State>(...)`) for non-trivial workflows.
- Keep mapping defaults intuitive (`sequential()`).
- In examples and docs, prefer explicit lambda parameter names (for example `current`) over implicit `it` for state transforms such as `updateStateAndRefresh`.

## Runtime Safety Rules

- UI operations must run on the main thread.
- Keep player/session ownership checks strict.
- Preserve close reason semantics (`PLAYER`, `API`, `REPLACED`, etc.).
- For mutable GUI data, rely on runtime refresh paths instead of forced reopen where possible.

## VirtualInventory and Persistence Rules

- Treat `VirtualInventory` as an independent data model, not as a GUI-owned object.
- Keep persistence backend-agnostic:
  - do not hardcode YAML/DB into core runtime
  - keep through `VirtualInventoryRepository` SPI
- Avoid data loss on shutdown; preserve coordinator flushing behavior.

## Animation Rules

- Presets currently exposed: `stagger`, `snake`, `random`.
- If adding new presets, ensure deterministic ordering logic where expected and keep cancellation behavior safe when sessions close/navigate.

## Output Format

When reporting completion, include:

- Changed files
- Behavioral impact
- Verification command(s) and result
