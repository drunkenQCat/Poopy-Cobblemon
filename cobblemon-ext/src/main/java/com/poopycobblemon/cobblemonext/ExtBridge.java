package com.poopycobblemon.cobblemonext;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;

import java.util.UUID;

/**
 * 引擎级效果的桥接 API：通过 {@code ShowdownService.send}（公开接口）发送
 * 自定义协议行，由本库注入的 {@code cobblemon_ext_patch.js} 拦截并用引擎原生
 * API（boostBy/damage）结算——效果真实生效，战报与 UI 由引擎自己生成。
 *
 * <p>所有伤害一律保底留 1 HP（不直接打倒），避免开场打倒引发的回合结构问题。
 */
public final class ExtBridge {

    private ExtBridge() {
    }

    /** 确保补丁已注入引擎（幂等，内部带重试节流） */
    public static void ensurePatched() {
        ShowdownPatchLoader.ensurePatched();
    }

    public static boolean isPatched() {
        return ShowdownPatchLoader.isPatched();
    }

    /** 引擎原生能力值变化：stages 正=提升，负=下降 */
    public static void applyBoost(PokemonBattle battle, UUID targetUuid, String stat, int stages) {
        ShowdownPatchLoader.sendLine(battle, "cobblemonext_boost",
                "{\"target\":\"" + targetUuid + "\",\"stat\":\"" + stat + "\",\"stages\":" + stages + "}");
        ShowdownPatchLoader.logStatus();
    }

    /** 引擎原生伤害：保底留 1 HP（不直接打倒） */
    public static void applyDamage(PokemonBattle battle, UUID targetUuid, int amount) {
        ShowdownPatchLoader.sendLine(battle, "cobblemonext_damage",
                "{\"target\":\"" + targetUuid + "\",\"amount\":" + amount + "}");
        ShowdownPatchLoader.logStatus();
    }
}
