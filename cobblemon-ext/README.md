# cobblemon-ext

A NeoForge library exposing Cobblemon battle events and a Showdown bridge for damage and stat changes. Used by Poopy Cobblemon; it adds no items or gameplay on its own.

[简体中文](README.zh-CN.md) · [Installation](../README.md#install)

Tested with Minecraft 1.21.1, NeoForge 21.1.240, Cobblemon 1.7.3 and Java 21. The library hooks into Cobblemon internals, so dependency upgrades require retesting.

## API

Java package: `com.poopycobblemon.cobblemonext`. Addons compiled against the previous package must be rebuilt with this library.

| Event or method | Behavior |
| --- | --- |
| `ExtEvents.MOVE_USED` | After `MoveInstruction.invoke` returns; includes the user, nullable target and move. Animations may still be pending. |
| `BATTLE_ACTIVE_READY` | Once per battle after initial active slots are assigned. |
| `ACTIVE_POKEMON_CHANGED` | When a slot's `BattlePokemon` changes, including becoming null. |
| `BATTLE_TURN` | After `PokemonBattle.turn(int)` executes; ignores repeated or backward turn numbers. |
| `BATTLE_ENDED` | Clears tracked battle state and notifies subscribers. |
| `currentTurn(battleId)` | Latest executed turn, or 0 before the first turn and after cleanup. |
| `ExtBridge.applyDamage(battle, uuid, amount)` | Requests damage through Showdown, leaving at least 1 HP. |
| `ExtBridge.applyBoost(battle, uuid, stat, stages)` | Requests a stat-stage change through Showdown. |

Subscribe once during mod initialization. Handlers should check the holder's health, item and animation readiness. Call `ExtBridge.ensurePatched()` and confirm `ExtBridge.isPatched()` before sending requests.

The JavaScript patch wraps `BattleStream._writeLine` inside the running GraalJS context. It handles `>cobblemonext_damage` and `>cobblemonext_boost` without overwriting Cobblemon files, and preserves Showdown's private/public HP messages.

## Build

Run `python scripts/build.py` from the repository root to build and test both mods. To build only this library, first run `python scripts/prepare_dependencies.py` at the root, then `./gradlew build` here (`./gradlew.bat build` on Windows).

Both projects read the root [`VERSION`](../VERSION). Java and JavaScript code use the [MIT license](../LICENSE); see the [asset license](../LICENSE-ASSETS.md) for non-code resources.
