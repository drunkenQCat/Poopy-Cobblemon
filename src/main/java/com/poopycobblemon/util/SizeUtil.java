package com.poopycobblemon.util;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

/**
 * 宝可梦“体型”量纲工具。
 *
 * <p>体型 = 碰撞箱体积的等效立方边长（∛(宽² × 高)），直接读原版
 * {@code Entity.getBbWidth()/getBbHeight()}。Cobblemon 重写了
 * {@code PokemonEntity.getDimensions(Pose)}，种族基础体型与体型系数
 * （{@code scaleModifier}，体型差异模组写入值）都会反映在碰撞箱里——
 * 巨浪鼬和小碎钻因此有数量级的差异。
 *
 * <p><strong>读取前强制刷新</strong>：原版只在姿态/同步数据变化时重算碰撞箱，
 * 极巨化（Mega Showdown 只改 {@code scaleModifier}，其包围盒刷新在客户端）
 * 这类“只改缩放、不刷包围盒”的场景会留下陈旧尺寸。本工具每次读取前调用
 * {@code refreshDimensions()}，保证拿到的是当前缩放下的真实体型。
 * 调用方均为事件频率（结算/扫描/触发），无热路径顾虑。
 */
public final class SizeUtil {

    private SizeUtil() {
    }

    /** 碰撞箱体积（立方格）；读取前按当前 scaleModifier 重算包围盒 */
    public static float volume(PokemonEntity entity) {
        entity.refreshDimensions();
        return entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
    }

    /** 线性体型 = 等效立方边长 = ∛体积；读取前按当前 scaleModifier 重算包围盒 */
    public static float linearSize(PokemonEntity entity) {
        return (float) Math.cbrt(Math.max(0.0D, volume(entity)));
    }
}
