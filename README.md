# kontainer-ui-lib

Paper 1.21.11 向けの Kotlin DSL Container UI ライブラリです。

- `structure + bind` でレイアウトを文字ベース定義
- `paged` / `scroll` / `tabContent` / `tabSelectors` を DSL で定義
- `nested` で別メニューを埋め込み表示
- `navigate` / `updateStateAndRefresh` で再オープンを減らした遷移
- `VirtualInventory` を画面の一部にバインド可能
- animation preset (`stagger`, `snake`, `random`)
- 永続化は `VirtualInventoryRepository` で利用側が自由に実装
- Paged/Scroll/Tab/Nested を載せるための v2 基盤

## 基本例

```kotlin
data class UiState(val page: Int, val bagId: UUID)

ui =
    kontainerUi(this) {
        menu<UiState>("pager") {
            rows = 3
            structure(
                "#########",
                "#<..P..>#",
                "#...S...#",
            )

            bind('#') { item { pane() }; cancelClick = true }
            bind('<') { onClick { updateStateAndRefresh { current -> current.copy(page = (current.page - 1).coerceAtLeast(1)) } } }
            bind('>') { onClick { updateStateAndRefresh { current -> current.copy(page = current.page + 1) } } }
            bind('S') { onClick { navigate("storage", state) } }
        }

        menu<UiState>("storage") {
            rows = 6
            structure(
                "B........",
                ".PPPPPPP.",
                ".PPPPPPP.",
                ".PPPPPPP.",
                ".........",
                "#########",
            )

            bind('B') { onClick { navigate("pager", state) } }
            bind('P') {
                virtual(
                    inventory = { virtualInventory(state.bagId, 27) },
                    mapping = sequential(),
                )
            }
            bind('#') { item { pane() }; cancelClick = true }
        }
    }
```

## VirtualInventory 永続化

永続化方式はライブラリ固定ではありません。

```kotlin
class MyRepository : VirtualInventoryRepository {
    override fun load(id: UUID): PersistedVirtualInventory? = TODO()
    override fun save(data: PersistedVirtualInventory) {}
    override fun delete(id: UUID) {}
}
```

```kotlin
kontainerUi(this) {
    persistence {
        repository(MyRepository())
        autoSaveIntervalTicks = 100L
    }
}
```

## ビルド

```bash
./gradlew build
```

## 要件

- Java 21+
