package com.poketoilet.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.poketoilet.battle.HeldItemBattleEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 挂在 Cobblemon 战斗解释器的技能指令上，实现番泻叶的“每次使用技能
 * （消耗 PP）触发”。Cobblemon 没有公开对应的 observable 事件。
 * Cobblemon 自带类名（非混淆），NeoForge 21.1 运行时即 Mojang 映射，无需 refmap。
 */
@Mixin(value = MoveInstruction.class, remap = false)
public class MoveInstructionMixin {

    @Inject(method = "invoke(Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;)V",
            at = @At("TAIL"), remap = false)
    private void poketoilet$onMoveUsed(PokemonBattle battle, CallbackInfo ci) {
        HeldItemBattleEffects.onMoveUsed((MoveInstruction) (Object) this, battle);
    }
}
