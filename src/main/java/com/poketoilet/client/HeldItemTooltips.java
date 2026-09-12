package com.poketoilet.client;

import com.altnoir.poopsky.init.PoItems;
import com.poketoilet.Poketoilet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 携带物品的悬停说明（类似树果的效果说明）。
 * PoopSky 的物品类我们无法修改，用 ItemTooltipEvent 追加灰色说明行。
 */
@EventBusSubscriber(modid = Poketoilet.MODID, value = Dist.CLIENT)
public final class HeldItemTooltips {

    private HeldItemTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(PoItems.FOLIUM_SENNAE.get())) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.poketoilet.folium_sennae").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable(
                    "tooltip.poketoilet.folium_sennae.2").withStyle(ChatFormatting.DARK_GRAY));
        } else if (stack.is(PoItems.KING_OF_DRAGON_FRUIT.get())) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.poketoilet.king_of_dragon_fruit").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable(
                    "tooltip.poketoilet.king_of_dragon_fruit.2").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
