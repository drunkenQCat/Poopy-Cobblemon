package com.poketoilet.mixin;

import com.altnoir.poopsky.content.entity.p.FlushToiletEntity;
import com.poketoilet.content.handler.OnTheVergeBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 马桶座椅（PoopSky 的实体）的“一触即发”兜底：与 SeatEntity.tick 的桥接对齐，
 * 宝可梦先坐上去、之后才被施加效果（如被泼药水）时也能触发。
 * 玩家不处理——PoopSky 原生的下蹲触发对马桶同样有效。
 * 触发会拆掉马桶，FlushToiletEntity 自己的 tick 检测到方块消失会自杀并弹出乘客。
 */
@Mixin(value = FlushToiletEntity.class, remap = false)
public class FlushToiletEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void poketoilet$onTick(CallbackInfo ci) {
        FlushToiletEntity self = (FlushToiletEntity) (Object) this;
        if (self.isRemoved() || !(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (var passenger : self.getPassengers()) {
            if (passenger instanceof LivingEntity living
                    && OnTheVergeBridge.tryTrigger(serverLevel, living, self.blockPosition())) {
                return;
            }
        }
    }
}
