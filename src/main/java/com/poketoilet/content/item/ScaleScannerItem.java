package com.poketoilet.content.item;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
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
 * 体型扫描仪：对宝可梦（{@link PokemonEntity}）右键，读取其体型值并展示。
 *
 * <p>体型即 {@code Pokemon.getScaleModifier()}——体型差异模组
 * （CobblemonSizeVariation）写入的缩放系数，也是一触即发侵染半径、
 * 帝王火龙果伤害所使用的同一数值。非宝可梦实体直接 PASS，不干扰原版交互。
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
            float scale = pokemonEntity.getPokemon().getScaleModifier();
            player.displayClientMessage(Component.translatable("message.poketoilet.scan_result",
                    pokemonEntity.getPokemon().getDisplayName(false),
                    String.format("%.3f", scale)), false);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.poketoilet.scale_scanner").withStyle(ChatFormatting.GRAY));
    }
}
