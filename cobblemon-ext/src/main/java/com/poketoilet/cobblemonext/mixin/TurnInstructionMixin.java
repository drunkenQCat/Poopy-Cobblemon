package com.poketoilet.cobblemonext.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.TurnInstruction;
import com.poketoilet.cobblemonext.CobblemonExt;
import com.poketoilet.cobblemonext.ExtEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 补齐“回合”事件：Cobblemon 把 |turn|N 协议行解析为 TurnInstruction，
 * 在其执行完毕（第 N-1 回合完全结算、第 N 回合开始）时发射 BATTLE_TURN。
 */
@Mixin(value = TurnInstruction.class, remap = false)
public class TurnInstructionMixin {

    @Shadow @Final private com.cobblemon.mod.common.api.battles.interpreter.BattleMessage message;

    @Inject(method = "invoke", at = @At("TAIL"), remap = false)
    private void cobblemonExt$onTurn(PokemonBattle battle, CallbackInfo ci) {
        try {
            int turn = Integer.parseInt(message.argumentAt(0));
            ExtEvents.emitTurn(battle, turn);
        } catch (Exception e) {
            CobblemonExt.LOGGER.debug("[cobblemon-ext] 回合数解析失败：{}", e.toString());
        }
    }
}
