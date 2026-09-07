package com.poketoilet.content.handler;

import com.altnoir.poopsky.content.block.abs.AbstractToiletBlock;
import com.altnoir.poopsky.content.block.p.FlushToiletBlock;
import com.altnoir.poopsky.content.entity.p.FlushToiletEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.logging.LogUtils;
import com.poketoilet.Poketoilet;
import com.poketoilet.content.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;

/**
 * PoopSky 厕所/马桶方块上的附属交互。
 *
 * <p>只处理主手事件（副手直接忽略，避免一次点击触发两次逻辑），文案对
 * “厕所”（{@link AbstractToiletBlock} 系列，蹲坑）和“马桶”
 * （{@link FlushToiletBlock}，即 {@code poopsky:flush_toilet}，坐式抽水马桶）做区分：
 * <ul>
 *   <li><strong>附近有该玩家拴着的生物</strong>（无论手上拿什么）→ 生物上座位，
 *       玩家自己不上。厕所用本模组的 {@link SeatEntity}（每 2 秒调用
 *       PoopSky 的 {@code ToiletUtil.onPoop}）；马桶直接复用 PoopSky 自带的
 *       {@link FlushToiletEntity} 座椅（排便/金马桶/冲水都是 PoopSky 原生逻辑）。
 *       宝可梦用 {@code startRiding(seat, true)} 强制乘骑，绕过
 *       {@code PokemonEntity.canRide} 的平台类型限制。判断依据是拴绳状态
 *       （拴绳末端是这位玩家），不要求手里拿着拴绳。</li>
 *   <li><strong>没有拴着的生物</strong>：空手右键厕所 → 玩家自己坐下；
 *       空手右键马桶则不干预，PoopSky 原生就会让玩家坐上马桶；
 *       手持拴绳 → 提示先拴住宝可梦；其他物品 → 不处理，交给 PoopSky / 原版。</li>
 * </ul>
 */
@EventBusSubscriber(modid = Poketoilet.MODID)
public final class ToiletAddonEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 搜索“该玩家拴着的生物”的半径（格） */
    private static final double LEASH_SEARCH_RANGE = 16.0;

    /** 与 PoopSky FlushToiletBlock 生成 FlushToiletEntity 时的偏移一致 */
    private static final float FLUSH_SEAT_OFFSET = 0.0625F;

    /** PoopSky 马桶座椅实体的注册 id（注意不是 flush_toilet，那是方块的 id） */
    private static final ResourceLocation FLUSH_SEAT_ID =
            ResourceLocation.fromNamespaceAndPath("poopsky", "flush_toilet_entity");

    private ToiletAddonEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean flush = state.getBlock() instanceof FlushToiletBlock;
        if (!flush && !(state.getBlock() instanceof AbstractToiletBlock)) {
            return;
        }

        Player player = event.getEntity();
        if (player == null || !player.isAlive()) {
            return;
        }

        ItemStack held = event.getItemStack();
        Mob target = findLeashedBy(level, pos, player);
        if (target != null) {
            // 拴着的生物优先：无论手上拿什么都让它上厕所
            handleLeashClick(level, state, pos, player, event, flush, target);
        } else if (held.isEmpty() && !flush) {
            // 马桶空手右键由 PoopSky 原生处理（坐下/开盖/开容器），不干预
            handleEmptyHandClick(level, pos, player, event);
        } else if (held.getItem() instanceof LeadItem) {
            // 手持拴绳但附近没有自己拴着的生物：给个提示，功能已不依赖手持拴绳
            player.displayClientMessage(Component.translatable("message.poketoilet.empty_lead"), true);
            event.setCanceled(true);
        }
    }

    /** 拴着的生物上座位，玩家不上（判断依据是拴绳状态，与手持物品无关） */
    private static void handleLeashClick(Level level, BlockState state, BlockPos pos, Player player,
                                         PlayerInteractEvent.RightClickBlock event, boolean flush, Mob target) {
        if (flush && state.getValue(FlushToiletBlock.CLOSED)) {
            player.displayClientMessage(Component.translatable("message.poketoilet.flush_closed"), true);
            event.setCanceled(true);
            return;
        }

        Entity seat = flush ? findOrCreateFlushSeat((ServerLevel) level, state, pos)
                : findOrCreateSeat(level, pos);
        if (seat == null) {
            // 座椅创建失败（如 PoopSky 实体 id 变动），如实提示而不是误报“被占用”
            player.displayClientMessage(Component.translatable(
                    key("ride_failed", flush), target.getDisplayName()), true);
            event.setCanceled(true);
            return;
        }
        if (seat.isVehicle()) {
            player.displayClientMessage(Component.translatable(key("toilet_occupied", flush)), true);
            event.setCanceled(true);
            return;
        }

        // force=true：绕过 PokemonEntity.canRide（部分宝可梦带平台类型会拒绝乘骑）
        boolean ridden = target.startRiding(seat, true);
        if (ridden) {
            target.dropLeash(true, false); // 乘骑成功才解拴绳
        }
        player.displayClientMessage(Component.translatable(
                key(ridden ? "sit_pokemon" : "ride_failed", flush), target.getDisplayName()), true);
        LOGGER.info("[Poketoilet] {} 手持拴绳右键{} {}，目标 {}，startRiding(force)={}",
                player.getName().getString(), flush ? "马桶" : "厕所", pos,
                target.getName().getString(), ridden);
        event.setCanceled(true);
    }

    /** 空手右键厕所：玩家自己坐下 */
    private static void handleEmptyHandClick(Level level, BlockPos pos, Player player,
                                             PlayerInteractEvent.RightClickBlock event) {
        SeatEntity seat = findOrCreateSeat(level, pos);
        if (seat.isVehicle()) {
            player.displayClientMessage(Component.translatable(key("toilet_occupied", false)), true);
            event.setCanceled(true);
            return;
        }
        player.startRiding(seat);
        player.displayClientMessage(Component.translatable(key("sit_self", false)), true);
        LOGGER.info("[Poketoilet] {} 空手坐上厕所 {}", player.getName().getString(), pos);
        event.setCanceled(true);
    }

    /** 文案键：按厕所/马桶取不同后缀 */
    private static String key(String base, boolean flush) {
        return "message.poketoilet." + base + (flush ? "_flush" : "_toilet");
    }

    /** 该玩家附近拴着的生物：宝可梦优先，其余按距离取最近 */
    private static Mob findLeashedBy(Level level, BlockPos pos, Player player) {
        List<Mob> leashed = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(LEASH_SEARCH_RANGE),
                mob -> mob.isAlive() && mob.getLeashHolder() == (Entity) player);
        if (leashed.isEmpty()) {
            return null;
        }
        return leashed.stream()
                .max(Comparator
                        .comparing((Mob mob) -> mob instanceof PokemonEntity)
                        .thenComparing(mob -> -mob.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .orElseThrow();
    }

    private static SeatEntity findOrCreateSeat(Level level, BlockPos pos) {
        // 只在本方块内找、且校验座椅记录的归属坐标：
        // 之前搜索框 inflate(1.0) 会命中相邻坑位的座椅，导致一只宝可梦锁死两个坑
        List<SeatEntity> seats = level.getEntitiesOfClass(SeatEntity.class, new AABB(pos),
                s -> s.isAlive() && s.getToiletPos().equals(pos));
        if (!seats.isEmpty()) {
            return seats.getFirst();
        }
        SeatEntity seat = new SeatEntity(Poketoilet.SEAT.get(), level);
        seat.setToiletPos(pos);
        seat.setPos(pos.getX() + 0.5, pos.getY() + 0.55, pos.getZ() + 0.5);
        level.addFreshEntity(seat);
        return seat;
    }

    /**
     * 复用 PoopSky 自己的马桶座椅：优先找马桶方块位置上已有的
     * {@link FlushToiletEntity}，没有就按 PoopSky FlushToiletBlock 的
     * 生成方式（TRIGGERED + FACING*0.0625 偏移）补一个。
     */
    private static FlushToiletEntity findOrCreateFlushSeat(ServerLevel level, BlockState state, BlockPos pos) {
        // 同 findOrCreateSeat：严格限定本方块，防止相邻马桶互锁
        List<FlushToiletEntity> seats = level.getEntitiesOfClass(FlushToiletEntity.class,
                new AABB(pos), e -> e.isAlive() && e.blockPosition().equals(pos));
        if (!seats.isEmpty()) {
            return seats.getFirst();
        }
        Entity spawned = BuiltInRegistries.ENTITY_TYPE.get(FLUSH_SEAT_ID)
                .spawn(level, pos, MobSpawnType.TRIGGERED);
        // NeoForge 的 Registry.get 未命中时不返回 null 而是注册表默认值（猪），
        // 所以必须 instanceof 校验，取错了立即丢弃，绝不把错误实体留在场上
        if (!(spawned instanceof FlushToiletEntity seat)) {
            if (spawned != null) {
                spawned.discard();
                LOGGER.error("[Poketoilet] {} 生成的不是 FlushToiletEntity（实际为 {}），已丢弃",
                        pos, BuiltInRegistries.ENTITY_TYPE.getKey(spawned.getType()));
            }
            return null;
        }
        if (seat != null) {
            Direction facing = state.getValue(FlushToiletBlock.FACING);
            seat.setPos(seat.getX() + facing.getStepX() * FLUSH_SEAT_OFFSET,
                    seat.getY(), seat.getZ() + facing.getStepZ() * FLUSH_SEAT_OFFSET);
        }
        return seat;
    }
}
