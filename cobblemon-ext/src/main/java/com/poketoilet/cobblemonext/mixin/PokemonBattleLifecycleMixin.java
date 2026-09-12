package com.poketoilet.cobblemonext.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.poketoilet.cobblemonext.ExtEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在派发队列真正执行回合切换后通知，而不是 TurnInstruction 将回调入队时。 */
@Mixin(value = PokemonBattle.class, remap = false)
public class PokemonBattleLifecycleMixin {
    @Inject(method = "turn(I)V", at = @At("TAIL"), remap = false)
    private void cobblemonExt$onTurn(int turn, CallbackInfo ci) {
        ExtEvents.emitTurn((PokemonBattle) (Object) this, turn);
    }

    @Inject(method = "end()V", at = @At("TAIL"), remap = false)
    private void cobblemonExt$onEnd(CallbackInfo ci) {
        ExtEvents.emitBattleEnded((PokemonBattle) (Object) this);
    }
}
