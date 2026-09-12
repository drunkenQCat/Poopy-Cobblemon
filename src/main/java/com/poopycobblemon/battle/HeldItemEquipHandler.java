package com.poopycobblemon.battle;

import com.altnoir.poopsky.init.PoItems;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import com.poopycobblemon.PoopyCobblemon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

/**
 * 番泻叶 / 帝王火龙果的携带装备交互修复。
 *
 * <p>两件物品都是 PoopSky 的食物类物品：潜行+右键自己的宝可梦本意是“装为携带物”，
 * 但物品的食物属性会抢在装备前触发吃取/喂食，导致果子被玩家吃掉。
 * 这里在交互事件的高优先级处直接走 Cobblemon 官方的 {@code offerHeldItem} 装备并
 * 取消后续处理（喂食/吃取/轮盘重复处理都不会发生）；同时潜行状态下禁止吃取
 * 这两件物品，防止装备流程被误触发的进食破坏。
 */
@EventBusSubscriber(modid = PoopyCobblemon.MODID)
public final class HeldItemEquipHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HeldItemEquipHandler() {
    }

    private static boolean isOurs(ItemStack stack) {
        return stack.is(PoItems.FOLIUM_SENNAE.get()) || stack.is(PoItems.KING_OF_DRAGON_FRUIT.get());
    }

    /**
     * 潜行 + 右键自己的宝可梦 + 手持番泻叶/帝王火龙果 → 直接装备为携带物。
     * 只处理主手（与轮盘的给携带物一致）；取高优先级，抢在 PoopSky 的
     * 喂食/吃取与轮盘逻辑之前，装备后取消事件防止二次消耗。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getTarget() instanceof PokemonEntity entity)) {
            return;
        }
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!isOurs(stack)) {
            return;
        }
        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null || pokemon.getOwnerPlayer() != player) {
            return; // 只给自己的宝可梦装备
        }

        // Cobblemon 官方装备路径：自动归还原携带物、消耗手上物品
        if (entity.offerHeldItem(player, stack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            player.displayClientMessage(Component.translatable(
                    "message.poopy_cobblemon.equip_success", pokemon.getDisplayName(false)), true);
            LOGGER.info("[Poopy Cobblemon] {} 将 {} 装备给 {}",
                    player.getName().getString(), stack.getItem(), pokemon.getDisplayName(false).getString());
        }
    }

    /** 潜行状态下禁止吃取这两件物品（防止右键落空时误食，装备流程被破坏） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player
                && player.isShiftKeyDown()
                && isOurs(event.getItem())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable(
                    "message.poopy_cobblemon.equip_hint"), true);
        }
    }
}
