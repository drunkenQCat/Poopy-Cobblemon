package com.poopycobblemon.client;

import com.poopycobblemon.PoopyCobblemon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 客户端初始化：为隐形座椅实体注册“不渲染”的占位渲染器。
 *
 * <p>不注册渲染器的话，客户端 EntityRenderDispatcher 查不到该实体类型
 * 会导致渲染线程 NPE 崩溃（TextField: shouldRender 中 entityrenderer 为 null）。
 */
@EventBusSubscriber(modid = PoopyCobblemon.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PoopyCobblemonClient {

    private PoopyCobblemonClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PoopyCobblemon.SEAT.get(), SeatEntityRenderer::new);
    }
}