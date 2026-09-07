package com.poketoilet.content.entity;

import com.altnoir.poopsky.content.block.abs.AbstractToiletBlock;
import com.altnoir.poopsky.impl.util.ToiletUtil;
import com.poketoilet.content.handler.OnTheVergeBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 隐形“马桶座椅”：让宝可梦/玩家骑乘在 PoopSky 的厕所方块上方。
 *
 * <p>座椅自己不会移动；每隔 {@link #POOP_COOLDOWN} tick 检查一次：
 * 下方还是不是 PoopSky 的厕所方块、有没有乘客——两者都满足就调用
 * PoopSky 自身的 {@link ToiletUtil#onPoop} 产出“大便”（含金马桶判断）。
 * 如果底下的厕所被拆了，座椅会自毁，把乘客弹出来。
 */
public class SeatEntity extends Entity {

    /** 排便周期（tick）：2 秒一次 */
    private static final int POOP_COOLDOWN = 40;

    private BlockPos toiletPos = BlockPos.ZERO;

    public SeatEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public BlockPos getToiletPos() {
        return toiletPos;
    }

    public void setToiletPos(BlockPos toiletPos) {
        this.toiletPos = toiletPos.immutable();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("ToiletPos")) {
            this.toiletPos = BlockPos.of(tag.getLong("ToiletPos"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("ToiletPos", this.toiletPos.asLong());
    }

    @Override
    protected void removePassenger(Entity passenger) {
        passenger.setPos(this.getX(), this.getY() + 1.2, this.getZ());
        super.removePassenger(passenger);
        if (!this.isRemoved()) {
            this.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.tickCount % POOP_COOLDOWN != 0) {
            return;
        }

        BlockState state = this.level().getBlockState(this.toiletPos);
        if (!(state.getBlock() instanceof AbstractToiletBlock)) {
            // 底下不再是 PoopSky 的厕所 -> 自毁（乘客会脱离）
            this.discard();
            return;
        }

        if (this.isVehicle() && this.getFirstPassenger() instanceof LivingEntity living) {
            // 坐着期间被施加“一触即发”（如被泼药水）也能触发；触发会拆掉厕所，下个 tick 自毁
            if (OnTheVergeBridge.tryTrigger(serverLevel, living, this.toiletPos)) {
                return;
            }
            boolean golden = ToiletUtil.isGoldenToilet(this.level(), this.toiletPos);
            ToiletUtil.onPoop(serverLevel, living, false, golden, 0.1F, 0.5F);
        }
    }
}