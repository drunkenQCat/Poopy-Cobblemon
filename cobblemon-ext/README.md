# cobblemon-ext —— Cobblemon 扩展 API 补齐库

把对 Cobblemon / Showdown 内部的“越界访问”收敛到一个独立库模组，
让上层附属（如 poketoilet）只依赖干净的 Java API。

## 提供的能力

| API | 说明 | 实现手段 |
|---|---|---|
| `ExtEvents.MOVE_USED` | 每次战斗指令使用技能（消耗 PP）时触发 | Mixin 进 `MoveInstruction.invoke`（Cobblemon 无公开事件） |
| `ExtEvents.BATTLE_ACTIVE_READY` | 战斗的参战位全部分配完毕时触发（每场一次） | Mixin 进 `ActiveBattlePokemon.setBattlePokemon`（`BATTLE_STARTED_POST` 时参战位尚未分配） |
| `ExtBridge.applyDamage(battle, uuid, amount)` | 引擎原生真实伤害（保底留 1 HP） | `ShowdownService.send` 发 `>cobblemonext_damage` 协议行，由注入的 JS 补丁拦截并用引擎 `damage()` 结算 |
| `ExtBridge.applyBoost(battle, uuid, stat, stages)` | 引擎原生能力值变化（真实影响出手顺序） | 同上，`>cobblemonext_boost` 行 + 引擎 `boostBy()` |

## 用法

```java
// 模组构造时订阅（cobblemon_ext 在 mods 目录即可）
ExtEvents.MOVE_USED.add(event -> { ... });          // MoveUsedEvent(battle, user, target, move)
ExtEvents.BATTLE_ACTIVE_READY.add(battle -> { ... });

// 触发引擎级效果
ExtBridge.ensurePatched();
ExtBridge.applyBoost(battle, targetUuid, "spe", -1);
ExtBridge.applyDamage(battle, targetUuid, 50);
```

## 原理

`ShowdownPatchLoader` 通过 `GraalShowdownService.getContext()` 拿到 GraalJS 上下文，
把 `assets/cobblemon_ext/showdown/cobblemon_ext_patch.js` 直接 eval 进运行中的
Showdown 引擎（MonsterTrainer 模式，不覆盖任何文件）。补丁包装
`BattleStream._writeLine`，拦截本库自定义协议行 `>cobblemonext_*`，
用引擎原生 API（`boostBy` / `damage`）结算——战报、UI、回合顺序全部由引擎自己处理。

上游 Cobblemon 若未来接受对应功能（公开事件 / 伤害桥接），删除库中对应的
Mixin / 补丁即可，订阅方代码无需改动。

## 构建

```powershell
powershell -ExecutionPolicy Bypass -File ..\build.ps1   # 一键构建两个模组
# 或单独构建：
.\gradlew.bat build installToInstance                    # 构建并安装进整合包
```

依赖：`libs/cobblemon-1.7.3.jar`（compileOnly，从整合包 mods 复制）、
Kotlin stdlib（Maven，走 gradle.properties 代理）。
