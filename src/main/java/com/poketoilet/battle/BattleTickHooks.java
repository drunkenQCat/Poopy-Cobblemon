package com.poketoilet.battle;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 服务端 tick 钩子：驱动帝王火龙果的“进战斗”轮询补判
 * （BATTLE_STARTED_POST 时参战位尚未分配，需要等出战指令执行完）。
 */
@EventBusSubscriber(modid = "poketoilet")
public final class BattleTickHooks {

    private BattleTickHooks() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        HeldItemBattleEffects.tickPending();
    }
}
