package com.poopycobblemon;

import com.mojang.logging.LogUtils;
import com.poopycobblemon.battle.HeldItemBattleEffects;
import com.poopycobblemon.content.entity.SeatEntity;
import com.poopycobblemon.content.item.ScaleScannerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Poopy Cobblemon —— PoopSkyMod + Cobblemon 的附属模组（addon）。
 *
 * <p>让 Cobblemon 宝可梦（或其他可拴绳的生物）坐上 PoopSkyMod 的厕所方块：
 * <ul>
 *   <li>拴住宝可梦后右键厕所（手上拿什么都行，判定依据是拴绳状态）→ 宝可梦被牵过来
 *       坐上隐形座椅，并持续调用 PoopSky 自身的排便逻辑产出它的“大便”；</li>
 *   <li>空手右键 PoopSky 的厕所 → 玩家自己坐下，同样持续排便；</li>
 *   <li>玩家蹲在 PoopSky 厕所上按 Shift 是 PoopSky 原版机制，本模组不干预。</li>
 * </ul>
 */
@Mod(PoopyCobblemon.MODID)
public class PoopyCobblemon {

    public static final String MODID = "poopy_cobblemon";

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    /** 隐形“马桶座椅”实体：让宝可梦/玩家骑乘在 PoopSky 厕所方块上 */
    public static final Supplier<EntityType<SeatEntity>> SEAT =
            ENTITY_TYPES.register("seat", () ->
                    EntityType.Builder.of(SeatEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(2)
                            .build("seat"));

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MODID);

    /** 体型扫描仪：对宝可梦右键读取体型值（scaleModifier） */
    public static final Supplier<Item> SCALE_SCANNER =
            ITEMS.register("scale_scanner", () ->
                    new ScaleScannerItem(new Item.Properties().stacksTo(1)));

    public PoopyCobblemon(IEventBus modEventBus) {
        // Resolve saved item/entity IDs from builds made before the project rename.
        ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath("poketoilet", "scale_scanner"),
                ResourceLocation.fromNamespaceAndPath(MODID, "scale_scanner"));
        ENTITY_TYPES.addAlias(ResourceLocation.fromNamespaceAndPath("poketoilet", "seat"),
                ResourceLocation.fromNamespaceAndPath(MODID, "seat"));
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        HeldItemBattleEffects.register();
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(SCALE_SCANNER.get());
            }
        });

        LOGGER.info("Poopy Cobblemon 已加载：PoopSkyMod + Cobblemon 附属模组（宝可梦坐马桶产屎）");
    }
}
