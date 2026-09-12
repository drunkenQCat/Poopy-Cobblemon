package com.poopycobblemon.cobblemonext.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.poopycobblemon.cobblemonext.ExtEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 补齐“技能使用”事件：Cobblemon 未公开对应 observable，
 * 在技能指令执行完毕处发射 {@link ExtEvents.MoveUsedEvent}。
 */
@Mixin(value = MoveInstruction.class, remap = false)
public class MoveInstructionMixin {

    @Inject(method = "invoke(Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;)V",
            at = @At("TAIL"), remap = false)
    private void cobblemonExt$onMoveUsed(PokemonBattle battle, CallbackInfo ci) {
        MoveInstruction self = (MoveInstruction) (Object) this;
        BattlePokemon user = self.getUserPokemon();
        if (user == null) {
            return;
        }
        ExtEvents.emitMoveUsed(new ExtEvents.MoveUsedEvent(
                battle, user, self.getTargetPokemon(), self.getMove()));
    }
}
