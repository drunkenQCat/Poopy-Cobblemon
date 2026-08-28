package com.poketoilet;

import com.mojang.logging.LogUtils;
import com.poketoilet.content.entity.SeatEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * Poketoilet —— PoopSkyMod + Cobblemon 的附属模组（addon）。
 *
 * <p>让 Cobblemon 宝可梦（或其他可拴绳的生物）坐上 PoopSkyMod 的厕所方块：
 * <ul>
 *   <li>手持拴绳右键 PoopSky 的厕所 → 附近该玩家拴着的宝可梦被牵过来坐上隐形座椅，
 *       并持续调用 PoopSky 自身的排便逻辑产出它的“大便”；</li>
 *   <li>空手右键 PoopSky 的厕所 → 玩家自己坐下，同样持续排便；</li>
 *   <li>玩家蹲在 PoopSky 厕所上按 Shift 是 PoopSky 原版机制，本模组不干预。</li>
 * </ul>
 */
@Mod(Poketoilet.MODID)
public class Poketoilet {

    public static final String MODID = "poketoilet";

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

    public Poketoilet(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);

        LOGGER.info("Poketoilet 已加载：PoopSkyMod + Cobblemon 附属模组（宝可梦坐马桶产屎）");
    }
}