# cobblemon-ext

NeoForge 扩展库，提供 Cobblemon 战斗事件，以及修改伤害和能力等级的 Showdown 桥接。供 Poopy Cobblemon 使用，本身不添加物品或玩法。

[English](README.md) · [安装说明](../README.zh-CN.md#安装)

已测试 Minecraft 1.21.1、NeoForge 21.1.240、Cobblemon 1.7.3 和 Java 21。扩展库使用 Cobblemon 内部接口，升级前置后需要复测。

## API

Java 包名为 `com.poopycobblemon.cobblemonext`；使用旧包名的附属模组需要使用本库重新编译。

| 事件或方法 | 行为 |
| --- | --- |
| `ExtEvents.MOVE_USED` | 在 `MoveInstruction.invoke` 返回后发射，包含使用者、可空目标和招式；动画可能尚未完成。 |
| `BATTLE_ACTIVE_READY` | 初始出战位分配完成后，每场战斗发射一次。 |
| `ACTIVE_POKEMON_CHANGED` | 出战位的 `BattlePokemon` 变化时发射，包括变为 null。 |
| `BATTLE_TURN` | 在 `PokemonBattle.turn(int)` 执行后发射，忽略重复或倒退的回合号。 |
| `BATTLE_ENDED` | 清理战斗记录并通知订阅者。 |
| `currentTurn(battleId)` | 最新已执行回合号；首回合前和清理后为 0。 |
| `ExtBridge.applyDamage(battle, uuid, amount)` | 通过 Showdown 请求伤害，至少保留 1 HP。 |
| `ExtBridge.applyBoost(battle, uuid, stat, stages)` | 通过 Showdown 请求能力等级变化。 |

在模组初始化时订阅一次。处理器需检查携带者的存活、道具和动画就绪状态。发送请求前调用 `ExtBridge.ensurePatched()`，再用 `ExtBridge.isPatched()` 确认补丁已加载。

JavaScript 补丁在运行中的 GraalJS 上下文包装 `BattleStream._writeLine`，处理 `>cobblemonext_damage` 和 `>cobblemonext_boost`，保留 Showdown 的私有／公开 HP 消息，不覆盖 Cobblemon 文件。

## 构建

在仓库根目录运行 `python scripts/build.py`，构建并测试两个模组。仅构建本库时，先在根目录运行 `python scripts/prepare_dependencies.py`，再进入本目录运行 `./gradlew build`（Windows 使用 `./gradlew.bat build`）。

两个项目共用根目录的 [`VERSION`](../VERSION)。Java 和 JavaScript 代码使用 [MIT 许可](../LICENSE)；非代码资源见[资源许可](../LICENSE-ASSETS.md)。
