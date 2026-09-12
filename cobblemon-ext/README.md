# cobblemon-ext 1.2

[中文](#中文) · [English](#english) · [Poketoilet README](../README.md)

## 中文

独立的 NeoForge 扩展库，集中处理 Cobblemon 事件与 Showdown 伤害、能力值协议。1.2 验证基线为 Minecraft 1.21.1、NeoForge 21.1.240、Cobblemon 1.7.3、Java 21。它依赖 Cobblemon 内部实现，升级上游后必须复测。

| 接口 | 语义 |
| --- | --- |
| `ExtEvents.MOVE_USED` | `MoveInstruction.invoke` 返回后发射；提供使用者、可空目标和招式。该指令可包含延后分发，不能视为所有动画已完成。 |
| `BATTLE_ACTIVE_READY` | 初始参战位全部分配后，每场一次；不等于实体出球动画结束。 |
| `ACTIVE_POKEMON_CHANGED` | 出战位的 `BattlePokemon` 对象改变时发射；换下可能变成 null。 |
| `BATTLE_TURN` | `PokemonBattle.turn(int)` 实际执行后发射，过滤重复或倒退回合号。 |
| `BATTLE_ENDED` | 战斗结束，清理回合与就绪记录并通知订阅者。 |
| `currentTurn(battleId)` | 最新实际回合号，尚未开始或已清理时为 0。 |
| `ExtBridge.applyDamage(battle, uuid, amount)` | 向引擎请求伤害，限制至少剩余 1 HP。 |
| `ExtBridge.applyBoost(battle, uuid, stat, stages)` | 向引擎请求能力阶级变化。 |

订阅者列表在模组初始化时添加一次；处理器应自行检查存活、携带物、实体和动画就绪状态。通过 `ExtBridge.ensurePatched()` 尝试初始化，再用 `ExtBridge.isPatched()` 确认后发送请求。

补丁 `assets/cobblemon_ext/showdown/cobblemon_ext_patch.js` 在运行中的 GraalJS 上下文包装 `BattleStream._writeLine`，处理 `>cobblemonext_damage` 与 `>cobblemonext_boost`。不覆盖 Cobblemon 文件。HP 消息必须保持 Showdown 的 split 私有/公开协议；聊天提示不是 HP 同步成功的证据。

在仓库根目录运行 `python scripts/build.py`，统一下载锁定依赖、构建两个模组并测试。只构建库可先在根目录运行 `python scripts/prepare_dependencies.py`，再进入本目录运行 `./gradlew build`（Windows 使用 `./gradlew.bat build`）。版本统一读取根目录 `VERSION`，本目录 wrapper 可单独工作；根目录主模组读取本目录 `build/libs/` 的产物。

完整构建运行回合调度、Showdown 桥接和无界面对战检查，不替代 Minecraft 实机验证。安装与发布请按[主 README](../README.md)操作。

## English

A standalone NeoForge extension library for Cobblemon events and Showdown damage/stat protocols. The 1.2 baseline is Minecraft 1.21.1, NeoForge 21.1.240, Cobblemon 1.7.3 and Java 21. It depends on Cobblemon internals; upstream upgrades require retesting.

| API | Meaning |
| --- | --- |
| `ExtEvents.MOVE_USED` | Emitted after `MoveInstruction.invoke` returns, with user, nullable target and move. The instruction may enqueue later dispatches; this does not guarantee completed animations. |
| `BATTLE_ACTIVE_READY` | Once per battle after initial active slots are assigned; entry animations may still be running. |
| `ACTIVE_POKEMON_CHANGED` | The slot's `BattlePokemon` identity changes, including becoming null on switch-out. |
| `BATTLE_TURN` | After `PokemonBattle.turn(int)` executes; duplicate or backward turn numbers are ignored. |
| `BATTLE_ENDED` | Clears stored turn/readiness state and notifies subscribers when the battle ends. |
| `currentTurn(battleId)` | Latest executed turn, or 0 before the first turn/after cleanup. |
| `ExtBridge.applyDamage(battle, uuid, amount)` | Requests engine damage, leaving at least 1 HP. |
| `ExtBridge.applyBoost(battle, uuid, stat, stages)` | Requests an engine stat-stage change. |

Subscribe once during mod initialization. Handlers must check health, held items, entity availability and animation readiness as appropriate. Call `ExtBridge.ensurePatched()`, then check `ExtBridge.isPatched()` before sending requests.

`assets/cobblemon_ext/showdown/cobblemon_ext_patch.js` wraps `BattleStream._writeLine` in the running GraalJS context to handle `>cobblemonext_damage` and `>cobblemonext_boost`, without overwriting Cobblemon files. HP messages must retain Showdown's split private/public protocol; chat text alone does not prove HP synchronization.

Run `python scripts/build.py` at the repository root to download pinned inputs, build both mods and test. For the library alone, run `python scripts/prepare_dependencies.py` at the root, then `./gradlew build` in this directory (`./gradlew.bat build` on Windows). Both projects read the root `VERSION`; the addon compiles against this directory's `build/libs/` output.

The full build runs scheduling, Showdown bridge and headless battle checks, which do not replace in-game validation. See the [main README](../README.md) for installation and releases.
