package com.poketoilet.content.handler;

import com.altnoir.poopsky.content.recipe.AnalPressingRecipe;
import com.altnoir.poopsky.impl.util.PoopTntUtil;
import com.altnoir.poopsky.init.PoEffects;
import com.altnoir.poopsky.init.PoRecipes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * “一触即发”的宝可梦桥接。
 *
 * <p>PoopSky 的 {@code OnTheVergeEffect} 触发分支只认玩家：
 * {@code instanceof Player + isShiftKeyDown()}（下蹲）——宝可梦不会下蹲，
 * 所以永远触发不了。这里在宝可梦坐上厕所时按玩家的触发序列原样执行：
 * <ol>
 *   <li>ANAL_PRESSING 配方匹配（厕所方块 + 正下方方块）→ 拆厕所、转换下方块；</li>
 *   <li>爆心爆炸（ExplosionInteraction.NONE，与玩家路径同参数）；</li>
 *   <li>{@link PoopTntUtil#triggerExplosion} 侵染周围方块。</li>
 * </ol>
 *
 * <p>与玩家路径的唯一区别：侵染半径按宝可梦体型缩放——体型来源是
 * {@code Pokemon.getScaleModifier()}（体型差异模组 SizeVariation 的写入点，
 * 玩家路径固定为 {@code min(18, 强度+2)}）。
 */
public final class OnTheVergeBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private OnTheVergeBridge() {
    }

    /**
     * 若实体（非玩家）身上有“一触即发”，立即按玩家序列触发。
     *
     * @return 是否触发了（触发后“一触即发”效果会被移除，天然幂等）
     */
    public static boolean tryTrigger(ServerLevel level, LivingEntity entity, BlockPos toiletPos) {
        if (entity instanceof Player) {
            return false; // 玩家走 PoopSky 原生下蹲逻辑，不干预
        }
        MobEffectInstance verge = entity.getEffect(PoEffects.ON_THE_VERGE);
        if (verge == null) {
            return false;
        }

        // 与 OnTheVergeEffect 玩家分支完全一致：配方匹配（厕所方块 + 下方块）才拆厕
        BlockState toiletState = level.getBlockState(toiletPos);
        BlockState belowState = level.getBlockState(toiletPos.below());
        RecipeManager recipes = level.getRecipeManager();
        boolean matched = false;
        for (RecipeHolder<AnalPressingRecipe> holder : recipes.getAllRecipesFor(PoRecipes.ANAL_PRESSING.type().get())) {
            AnalPressingRecipe recipe = holder.value();
            if (recipe.input().test(new ItemStack(toiletState.getBlock().asItem()))
                    && recipe.replaceTarget() == belowState.getBlock()) {
                level.removeBlock(toiletPos, false);
                recipe.applyConversion(level, toiletPos.below());
                matched = true;
                break;
            }
        }

        // 体型系数：Pokemon.scaleModifier（体型差异模组写入），其他生物恒为 1
        float scale = 1.0F;
        if (entity instanceof PokemonEntity pokemon) {
            scale = Math.max(0.1F, pokemon.getPokemon().getScaleModifier());
        }
        // 玩家路径：min(18, 强度+2)；宝可梦按体型缩放，上限同样是 18
        int radius = Math.clamp(Math.round((verge.getAmplifier() + 2) * scale), 1, 18);

        level.explode(entity, entity.getX(), entity.getY(-0.0625), entity.getZ(),
                2.0F, Level.ExplosionInteraction.NONE);
        PoopTntUtil.triggerExplosion(entity, radius);

        entity.removeEffect(PoEffects.ON_THE_VERGE);
        if (matched) {
            entity.removeEffect(PoEffects.INTESTINAL_SPASM);
        }
        LOGGER.info("[Poketoilet] {} 触发一触即发 @ {}，侵染半径 {}（体型 x{}）",
                entity.getName().getString(), toiletPos, radius, scale);
        return true;
    }
}
