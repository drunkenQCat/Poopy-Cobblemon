package com.poopycobblemon.cobblemonext;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.api.moves.MoveTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * cobblemon-ext 提供的扩展事件。全部为纯 Java 消费者列表，
 * 订阅方直接 {@code list.add(消费函数)} 即可（线程安全，写少读多）。
 */
public final class ExtEvents {

    /** 技能使用事件（每次消耗 PP / 每条技能指令） */
    public record MoveUsedEvent(PokemonBattle battle, BattlePokemon user, BattlePokemon target, MoveTemplate move) {
    }

    /** 技能使用：参战位分配完毕的战斗里，每次有宝可梦使用技能都会触发 */
    public static final List<Consumer<MoveUsedEvent>> MOVE_USED = new CopyOnWriteArrayList<>();

    /** 参战位就绪：战斗的所有出战位都分配到宝可梦后触发（每场战斗一次） */
    public static final List<Consumer<PokemonBattle>> BATTLE_ACTIVE_READY = new CopyOnWriteArrayList<>();

    /** 出战位内容改变（包括换下变为 null）；相同对象重复赋值不触发。 */
    public static final List<Consumer<ActiveBattlePokemon>> ACTIVE_POKEMON_CHANGED = new CopyOnWriteArrayList<>();

    /** 回合事件：引擎宣布第 N 回合开始（即第 N-1 回合的招式、状态伤害与回合末效果已全部结算） */
    public record BattleTurnEvent(PokemonBattle battle, int turn) {
    }

    public static final List<Consumer<BattleTurnEvent>> BATTLE_TURN = new CopyOnWriteArrayList<>();

    /** 战斗结束后清理业务层的等待状态。 */
    public static final List<Consumer<PokemonBattle>> BATTLE_ENDED = new CopyOnWriteArrayList<>();

    /** 每场战斗已执行的最新回合数（第一回合尚未执行时为 0） */
    private static final Map<UUID, Integer> CURRENT_TURN = new ConcurrentHashMap<>();

    /** 查询战斗当前回合数（0 = 第一回合尚未开始） */
    public static int currentTurn(UUID battleId) {
        return CURRENT_TURN.getOrDefault(battleId, 0);
    }

    public static void emitTurn(PokemonBattle battle, int turn) {
        if (battle == null || battle.getEnded() || turn <= currentTurn(battle.getBattleId())) {
            return;
        }
        CURRENT_TURN.put(battle.getBattleId(), turn);
        for (Consumer<BattleTurnEvent> consumer : BATTLE_TURN) {
            try {
                consumer.accept(new BattleTurnEvent(battle, turn));
            } catch (Throwable t) {
                CobblemonExt.LOGGER.error("[cobblemon-ext] BATTLE_TURN 订阅者异常", t);
            }
        }
    }

    public static void emitBattleEnded(PokemonBattle battle) {
        CURRENT_TURN.remove(battle.getBattleId());
        READY_MARKED.remove(battle);
        for (Consumer<PokemonBattle> consumer : BATTLE_ENDED) {
            try {
                consumer.accept(battle);
            } catch (Throwable t) {
                CobblemonExt.LOGGER.error("[cobblemon-ext] BATTLE_ENDED 订阅者异常", t);
            }
        }
    }

    public static void emitActivePokemonChanged(ActiveBattlePokemon active) {
        for (Consumer<ActiveBattlePokemon> consumer : ACTIVE_POKEMON_CHANGED) {
            try {
                consumer.accept(active);
            } catch (Throwable t) {
                CobblemonExt.LOGGER.error("[cobblemon-ext] ACTIVE_POKEMON_CHANGED 订阅者异常", t);
            }
        }
    }

    /** 每场战斗只标记一次就绪事件；弱引用键，战斗结束后自动清理 */
    private static final Map<PokemonBattle, Object> READY_MARKED =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 标记战斗已就绪；返回 false 表示此前已标记过 */
    public static boolean markBattleActiveReady(PokemonBattle battle) {
        if (battle == null) {
            return false;
        }
        return READY_MARKED.put(battle, Boolean.TRUE) == null;
    }

    public static boolean isBattleActiveReady(PokemonBattle battle) {
        return READY_MARKED.containsKey(battle);
    }

    public static void emitMoveUsed(MoveUsedEvent event) {
        for (Consumer<MoveUsedEvent> consumer : MOVE_USED) {
            try {
                consumer.accept(event);
            } catch (Throwable t) {
                CobblemonExt.LOGGER.error("[cobblemon-ext] MOVE_USED 订阅者异常", t);
            }
        }
    }

    public static void emitBattleActiveReady(PokemonBattle battle) {
        for (Consumer<PokemonBattle> consumer : BATTLE_ACTIVE_READY) {
            try {
                consumer.accept(battle);
            } catch (Throwable t) {
                CobblemonExt.LOGGER.error("[cobblemon-ext] BATTLE_ACTIVE_READY 订阅者异常", t);
            }
        }
    }

    private ExtEvents() {
    }
}
