package com.poopycobblemon.content.item;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.poopycobblemon.util.SizeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 体型扫描仪：对宝可梦（{@link PokemonEntity}）右键，读取其碰撞箱体积与
 * 等效边长（见 {@link SizeUtil}），倍率（scaleModifier）作参考值一并列出。
 * 非宝可梦实体直接 PASS，不干扰原版交互。
 */
public class ScaleScannerItem extends Item {

    public ScaleScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof PokemonEntity pokemonEntity)) {
            return InteractionResult.PASS; // 不是宝可梦：交给原版/其他模组处理
        }
        if (!player.level().isClientSide) {
            float volume = SizeUtil.volume(pokemonEntity);
            float edge = (float) Math.cbrt(volume);
            player.displayClientMessage(Component.translatable("message.poopy_cobblemon.scan_result",
                    pokemonEntity.getPokemon().getDisplayName(false),
                    String.format("%.2f", volume), String.format("%.2f", edge),
                    String.format("%.3f", pokemonEntity.getPokemon().getScaleModifier())), false);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.poopy_cobblemon.scale_scanner").withStyle(ChatFormatting.GRAY));
    }
}
