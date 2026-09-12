package com.poopycobblemon.client;

import com.poopycobblemon.content.entity.SeatEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 隐形座椅的占位渲染器：不画任何东西（实体压根看不见），
 * 只为了满足 EntityRenderDispatcher 的渲染器查找。
 */
public class SeatEntityRenderer extends EntityRenderer<SeatEntity> {

    public SeatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SeatEntity entity) {
        // 不会被实际使用，返回合法路径即可
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}