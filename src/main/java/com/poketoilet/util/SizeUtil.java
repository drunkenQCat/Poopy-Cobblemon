package com.poketoilet.util;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

/**
 * 宝可梦“体型”量纲工具。
 *
 * <p>体型 = 碰撞箱体积的等效立方边长（∛(宽² × 高)），直接读原版
 * {@code Entity.getBbWidth()/getBbHeight()}。Cobblemon 重写了
 * {@code PokemonEntity.getDimensions(Pose)}，种族基础体型与体型系数
 * （{@code scaleModifier}，体型差异模组写入值）都会反映在碰撞箱里——
 * 巨浪鼬和小碎钻因此有数量级的差异，不依赖倍率是否被修改过。
 */
public final class SizeUtil {

    private SizeUtil() {
    }

    /** 碰撞箱体积（立方格） */
    public static float volume(PokemonEntity entity) {
        return entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
    }

    /** 线性体型 = 等效立方边长 = ∛体积 */
    public static float linearSize(PokemonEntity entity) {
        return (float) Math.cbrt(Math.max(0.0D, volume(entity)));
    }
}
