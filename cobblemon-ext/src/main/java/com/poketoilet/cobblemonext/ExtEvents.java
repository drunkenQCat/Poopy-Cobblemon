package com.poketoilet.cobblemonext;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.api.moves.MoveTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
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
