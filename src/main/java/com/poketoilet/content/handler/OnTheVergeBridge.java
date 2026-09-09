package com.poketoilet.content.handler;

import com.altnoir.poopsky.content.block.abs.AbstractToiletBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * “一触即发”的宝可梦桥接。
 *
 * <p>PoopSky 的 {@code OnTheVergeEffect} 触发分支只认玩家：
 * {@code instanceof Player + isShiftKeyDown()}（下蹲）——宝可梦不会下蹲，
 * 所以永远触发不了。这里在宝可梦坐上厕所/马桶时补上触发：
 * <ol>
 *   <li>ANAL_PRESSING 配方匹配（矿石压制类特殊方块）→ 拆厕 + 下方块转换（与玩家一致）；
 *       普通厕所没有配方 → 直接轰碎（掉落本体）；</li>
 *   <li>爆炸走 PoopSky 自己的 {@link PoopTntUtil#triggerExplosion}（本模组不自写爆炸逻辑），
 *       半径与<strong>宝可梦体型</strong>挂钩：{@code min(18, max(1, round(2 × 体型)))}——
 *       体型取 {@code Pokemon.scaleModifier}（体型差异模组写入值），与效果等级无关。</li>
 * </ol>
 * 触发后移除“一触即发”（配方命中时连“肠痉挛”一起移除），天然幂等：
 * 效果被药水反复施加时每次坐厕各触发一次。
 */
public final class OnTheVergeBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private OnTheVergeBridge() {
    }

    /**
     * 若实体（非玩家）身上有“一触即发”，立即按玩家序列触发。
     *
     * @return 是否触发了（触发后“一触即发”效果会被移除）
     */
    public static boolean tryTrigger(ServerLevel level, LivingEntity entity, BlockPos toiletPos) {
        if (entity instanceof Player) {
            return false; // 玩家走 PoopSky 原生下蹲逻辑，不干预
        }
        MobEffectInstance verge = entity.getEffect(PoEffects.ON_THE_VERGE);
        if (verge == null) {
            return false;
        }

        // ANAL_PRESSING 配方命中（矿石压制类）才走 removeBlock + 下方块转换（玩家同款）；
        // 普通厕所没有配方 → 直接轰碎（掉落本体），保证触发肉眼可见
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
        if (!matched) {
            level.destroyBlock(toiletPos, true, entity);
        }

        // 爆炸交给 PoopSky 自己的 triggerExplosion，半径随宝可梦体型缩放
        float scale = 1.0F;
        if (entity instanceof PokemonEntity pokemon) {
            scale = Math.max(0.1F, Math.abs(pokemon.getPokemon().getScaleModifier()));
        }
        int radius = Math.min(18, Math.max(1, Math.round(2 * scale)));
        PoopTntUtil.triggerExplosion(entity, radius);

        entity.removeEffect(PoEffects.ON_THE_VERGE);
        if (matched) {
            entity.removeEffect(PoEffects.INTESTINAL_SPASM);
        }
        LOGGER.info("[Poketoilet] {} 触发一触即发 @ {}，体型 x{}，侵染半径 {}",
                entity.getName().getString(), toiletPos, scale, radius);
        return true;
    }
}
