package com.poketoilet.cobblemonext.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.poketoilet.cobblemonext.ExtEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 补齐“参战位就绪”事件：{@code BATTLE_STARTED_POST} 触发时参战位尚未分配
 * （出战是战斗开始后的指令），本 Mixin 在每个出战位分配完宝可梦后检查整场战斗，
 * 全部就绪即发射 {@code BATTLE_ACTIVE_READY}（每场战斗恰好一次，按战斗对象弱引用去重）。
 */
@Mixin(value = ActiveBattlePokemon.class, remap = false)
public class ActiveBattlePokemonMixin {

    @Unique
    private BattlePokemon cobblemonExt$previousPokemon;

    @Inject(method = "setBattlePokemon", at = @At("HEAD"), remap = false)
    private void cobblemonExt$beforePokemonSet(BattlePokemon battlePokemon, CallbackInfo ci) {
        cobblemonExt$previousPokemon = ((ActiveBattlePokemon) (Object) this).getBattlePokemon();
    }

    @Inject(method = "setBattlePokemon", at = @At("TAIL"), remap = false)
    private void cobblemonExt$onBattlePokemonSet(BattlePokemon battlePokemon, CallbackInfo ci) {
        ActiveBattlePokemon self = (ActiveBattlePokemon) (Object) this;
        PokemonBattle battle = self.getBattle();
        if (battle == null || battle.getEnded()) {
            return;
        }
        if (cobblemonExt$previousPokemon != battlePokemon) {
            ExtEvents.emitActivePokemonChanged(self);
        }
        for (ActiveBattlePokemon active : battle.getActivePokemon()) {
            if (active.getBattlePokemon() == null) {
                return; // 还有未分配的出战位
            }
        }
        if (ExtEvents.markBattleActiveReady(battle)) {
            ExtEvents.emitBattleActiveReady(battle);
        }
    }
}
