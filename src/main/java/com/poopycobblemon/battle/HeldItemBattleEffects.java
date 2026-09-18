package com.poopycobblemon.battle;

import com.altnoir.poopsky.init.PoItems;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import com.poopycobblemon.cobblemonext.ExtBridge;
import com.poopycobblemon.cobblemonext.ExtEvents;
import com.poopycobblemon.cobblemonext.handler.ExtHandlers;
import com.poopycobblemon.util.SizeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宝可梦携带 PoopSky 物品时的战斗效果（业务层）。
 *
 * <p>机制全部委托给 cobblemon_ext 库：本类只做“判断携带物 + 计算数值 + 调用桥接”，
 * 对 Showdown 内部的所有访问（事件缺失、引擎注入、协议行）都在库里。
 *
 * <ul>
 *   <li><strong>番泻叶</strong>（{@code poopsky:folium_sennae}）：每次使用任意技能，
 *       敌方所有出战宝可梦速度阶级 -1（引擎原生 boost，可叠加至 -6），
 *       伴随便便抛物线动画与抽水马桶音效。</li>
 *   <li><strong>帝王火龙果</strong>（{@code poopsky:king_of_dragon_fruit}，
 *       每场战斗限发动一次，道具不消耗）：
 *       触发时点按宝可梦是否为“满级火属性”分两条路径——
 *       <ul>
 *         <li>满级火属性：入场动画结束后立即触发；</li>
 *         <li>其他：完整在场经历一个回合后，在引擎宣布下一回合开始时触发；
 *             等待期间被换下、击倒或战斗结束则取消，重新上场重新计算。</li>
 *       </ul>
 *       结算：自身固定损失 1% 最大生命，敌方每只出战宝可梦受到
 *       基于对数体积比与等级差的引擎真实伤害（保底留 1 HP），并播放爆炸动画。
 *       发动过后道具保留在身上，但同一场战斗内不得再发动。</li>
 * </ul>
 */
public final class HeldItemBattleEffects {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 即时触发路径（满级火属性）：出战位 → 待结算 */
    private static final Map<ActiveBattlePokemon, PendingDragonFruit> IMMEDIATE = new IdentityHashMap<>();

    /** 回合触发路径（其他宝可梦）：战斗 id → 等待结算列表 */
    private static final Map<UUID, List<PendingDragonFruit>> TURN_WAITING = new ConcurrentHashMap<>();

    /** 本场战斗已发动过火龙果的宝可梦（战斗 id → 宝可梦 UUID）：道具不消耗，但每场限发动一次 */
    private static final Map<UUID, Set<UUID>> TRIGGERED = new ConcurrentHashMap<>();

    private static boolean hasTriggeredThisBattle(UUID battleId, UUID pokemonUuid) {
        return TRIGGERED.getOrDefault(battleId, java.util.Set.of()).contains(pokemonUuid);
    }

    private static final class PendingDragonFruit {
        final ActiveBattlePokemon active;
        final BattlePokemon self;
        final Pokemon pokemon;
        final int triggerTurn; // 0 = 即时（出球动画结束即触发）
        int ticks;

        PendingDragonFruit(ActiveBattlePokemon active, BattlePokemon self, Pokemon pokemon, int triggerTurn) {
            this.active = active;
            this.self = self;
            this.pokemon = pokemon;
            this.triggerTurn = triggerTurn;
        }
    }

    /** 在模组构造时调用一次 */
    public static void register() {
        if (!ModList.get().isLoaded("cobblemon_ext")) {
            LOGGER.warn("[Poopy Cobblemon] 未安装 cobblemon_ext 库，携带物品战斗效果不可用");
            return;
        }
        // 番泻叶：携带者每次出招 → 敌方全体速度 -1；持有物判定收进过滤器
        ExtHandlers.subscribe(ExtEvents.MOVE_USED,
                event -> {
                    Pokemon user = event.user() == null ? null : event.user().getEffectedPokemon();
                    return user != null && event.target() != null
                            && isHolding(user, PoItems.FOLIUM_SENNAE.get());
                },
                HeldItemBattleEffects::onMoveUsed);
        // 帝王火龙果：参战位变化时刷新两条触发路径的登记
        ExtHandlers.subscribe(ExtEvents.ACTIVE_POKEMON_CHANGED,
                HeldItemBattleEffects::onActivePokemonChanged);
        ExtHandlers.subscribe(ExtEvents.BATTLE_ACTIVE_READY,
                battle -> battle.getActivePokemon().forEach(HeldItemBattleEffects::onActivePokemonChanged));
        ExtHandlers.subscribe(ExtEvents.BATTLE_TURN,
                event -> !event.battle().getEnded(),
                event -> onBattleTurn(event.battle(), event.turn()));
        ExtHandlers.subscribe(ExtEvents.BATTLE_ENDED, HeldItemBattleEffects::onBattleEnded);
        NeoForge.EVENT_BUS.addListener(HeldItemBattleEffects::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.poopycobblemon.battle.BattleDebugCommands::register);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            IMMEDIATE.clear();
            TURN_WAITING.clear();
            TRIGGERED.clear();
        });
        LOGGER.info("[Poopy Cobblemon] 携带物品战斗效果已注册（番泻叶 / 帝王火龙果）");
    }

    private static boolean isHolding(Pokemon pokemon, Item item) {
        if (pokemon == null) {
            return false;
        }
        ItemStack held = pokemon.heldItem();
        return !held.isEmpty() && held.is(item);
    }

    /** 达到配置中的满级，且当前形态含火属性（不包括太晶化）。 */
    private static boolean isFireTypeMaxLevel(Pokemon pokemon) {
        if (pokemon.getLevel() < Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel()) {
            return false;
        }
        for (var type : pokemon.getTypes()) {
            if (type.getName().equalsIgnoreCase("fire")) {
                return true;
            }
        }
        return false;
    }

    /** 出战位内容变化：刷新两条触发路径的登记（换下/击倒即取消等待，果子不消耗） */
    private static void onActivePokemonChanged(ActiveBattlePokemon active) {
        IMMEDIATE.remove(active);
        TURN_WAITING.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(w -> w.active == active);
            return entry.getValue().isEmpty();
        });
        BattlePokemon self = active.getBattlePokemon();
        if (self == null || !active.isAlive()) {
            return;
        }
        Pokemon pokemon = self.getEffectedPokemon();
        if (pokemon == null || !isHolding(pokemon, PoItems.KING_OF_DRAGON_FRUIT.get())) {
            return;
        }
        var battle = active.getBattle();
        if (battle == null || battle.getEnded()) {
            return;
        }
        if (hasTriggeredThisBattle(battle.getBattleId(), pokemon.getUuid())) {
            return; // 本场战斗已发动过：果子保留，但不得再发动
        }
        PendingDragonFruit pending = new PendingDragonFruit(active, self, pokemon,
                isFireTypeMaxLevel(pokemon) ? 0 : ExtEvents.currentTurn(battle.getBattleId()) + 2);
        if (pending.triggerTurn == 0) {
            IMMEDIATE.put(active, pending);
        } else {
            TURN_WAITING.computeIfAbsent(battle.getBattleId(), k -> new ArrayList<>()).add(pending);
            LOGGER.debug("[Poopy Cobblemon] 火龙果等待：battle={} pokemon={} triggerTurn={}",
                    battle.getBattleId(), pokemon.getUuid(), pending.triggerTurn);
        }
    }

    private static void onBattleEnded(PokemonBattle battle) {
        TURN_WAITING.remove(battle.getBattleId());
        TRIGGERED.remove(battle.getBattleId()); // 新战斗恢复可发动
        IMMEDIATE.keySet().removeIf(active -> active.getBattle() == battle);
    }

    /** 即时路径的 tick 门控：等出球光束动画结束、对手就绪、战斗就绪 */
    private static void onServerTick(ServerTickEvent.Post event) {
        var iterator = IMMEDIATE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ActiveBattlePokemon active = entry.getKey();
            PendingDragonFruit pending = entry.getValue();
            PokemonBattle battle = active.getBattle();
            if (battle.getEnded() || active.getBattlePokemon() != pending.self || !active.isAlive()
                    || !isHolding(pending.pokemon, PoItems.KING_OF_DRAGON_FRUIT.get())) {
                iterator.remove();
                continue;
            }
            if (++pending.ticks > 200) {
                LOGGER.warn("[Poopy Cobblemon] 火龙果上场等待超时：battle={} pokemon={}",
                        battle.getBattleId(), pending.pokemon.getUuid());
                iterator.remove();
                continue;
            }
            if (!ExtEvents.isBattleActiveReady(battle)) {
                continue; // 首次入场必须等全部出战位分配，避免多人战过早结算后又被 ready 重复入队
            }
            if (pending.self.getEntity() == null || pending.self.getEntity().getBeamMode() != 0) {
                continue; // 出球光束动画未结束
            }
            boolean opponentsReady = false;
            for (ActiveBattlePokemon other : battle.getActivePokemon()) {
                if (other.getSide() != active.getSide() && other.isAlive()) {
                    opponentsReady = true;
                }
            }
            if (!opponentsReady) {
                continue;
            }
            ExtBridge.ensurePatched();
            if (!ExtBridge.isPatched()) {
                continue;
            }
            iterator.remove();
            triggerDragonFruit(battle, pending.self, pending.pokemon);
        }
    }

    /** 回合路径：引擎宣布的回合数到达触发回合时结算（等待期间被换下/击倒则放弃） */
    private static void onBattleTurn(PokemonBattle battle, int turn) {
        if (battle.getEnded()) {
            onBattleEnded(battle);
            return;
        }
        var list = TURN_WAITING.get(battle.getBattleId());
        if (list == null) {
            return;
        }
        var iterator = list.iterator();
        while (iterator.hasNext()) {
            PendingDragonFruit wait = iterator.next();
            if (wait.active.getBattlePokemon() != wait.self || !wait.active.isAlive()
                    || !isHolding(wait.pokemon, PoItems.KING_OF_DRAGON_FRUIT.get())) {
                iterator.remove();
                continue;
            }
            if (turn < wait.triggerTurn) {
                continue; // 保留登记，下一个回合事件还要继续检查
            }
            ExtBridge.ensurePatched();
            if (!ExtBridge.isPatched()) {
                continue;
            }
            iterator.remove();
            triggerDragonFruit(battle, wait.self, wait.pokemon);
        }
        if (list.isEmpty()) {
            TURN_WAITING.remove(battle.getBattleId(), list);
        }
    }

    /** 帝王火龙果结算：自损 1% 最大生命，敌方按体积比公式扣血（保底留 1 HP）；发动后本场战斗锁定（道具不消耗） */
    private static void triggerDragonFruit(PokemonBattle battle, BattlePokemon self, Pokemon pokemon) {
        Pokemon holder = self.getEffectedPokemon() != null ? self.getEffectedPokemon() : pokemon;
        // 发动即锁定：本场战斗内不再登记、不再结算
        TRIGGERED.computeIfAbsent(battle.getBattleId(), k -> ConcurrentHashMap.newKeySet())
                .add(holder.getUuid());
        float size = 1.0F;
        float atkVolume = 1.0F;
        if (self.getEntity() != null) {
            size = Math.max(0.1F, SizeUtil.linearSize(self.getEntity()));
            atkVolume = Math.max(0.1F, SizeUtil.volume(self.getEntity()));
        }
        int selfDamage = Math.max(1, Math.round(self.getMaxHealth() * 0.01F));

        ExtBridge.applyDamage(battle, self.getUuid(), selfDamage);
        if (self.getEntity() != null) {
            playVergeExplosion(self.getEntity());
        }
        int dealt = 0;
        for (ActiveBattlePokemon other : battle.getActivePokemon()) {
            BattlePokemon target = other.getBattlePokemon();
            if (target == null || !other.isAlive() || other.getSide() == self.getActor().getSide()) {
                continue;
            }
            float tgtVol = 1.0F;
            if (target.getEntity() != null) {
                tgtVol = Math.max(0.1F, SizeUtil.volume(target.getEntity()));
            }
            double r = atkVolume / tgtVol;
            double f = r <= 1 ? r : 1 + Math.log(r) / Math.log(500.0D);
            Pokemon targetPokemon = target.getEffectedPokemon();
            int targetLevel = targetPokemon != null ? targetPokemon.getLevel() : 50;
            int enemyDamage = Math.max(1, (int) Math.round(
                    target.getMaxHealth() * 0.25 * Math.max(0, f + (pokemon.getLevel() - targetLevel) / 100.0D)));
            ExtBridge.applyDamage(battle, target.getUuid(), enemyDamage);
            if (target.getEntity() != null) {
                playVergeExplosion(target.getEntity());
            }
            tellBattle(battle, Component.translatable("message.poopy_cobblemon.dragonfruit_trigger",
                    holder.getDisplayName(false), selfDamage, target.getName(), enemyDamage));
            dealt++;
        }
        LOGGER.info("[Poopy Cobblemon] {} 携带帝王火龙果结算：自损 {} HP，命中 {} 个目标，道具保留（本场战斗不得再发动；体型边长 x{}，等级 {}）",
                holder.getDisplayName(false).getString(), selfDamage, dealt, size, holder.getLevel());
    }

    /** 番泻叶结算：敌方速度阶级 -1（引擎原生 boost）+ 便意倾泻表现（过滤已在注册处完成） */
    private static void onMoveUsed(ExtEvents.MoveUsedEvent event) {
        try {
            ExtBridge.ensurePatched();
            if (!ExtBridge.isPatched()) {
                return;
            }
            Pokemon pokemon = event.user().getEffectedPokemon();
            BattlePokemon target = event.target();
            ExtBridge.applyBoost(event.battle(), target.getUuid(), "spe", -1);
            playSennaAnimation(event.user(), target);
            tellBattle(event.battle(), Component.translatable("message.poopy_cobblemon.senna_trigger",
                    pokemon.getDisplayName(false), target.getName()));
            LOGGER.info("[Poopy Cobblemon] {} 携带番泻叶使用技能，敌方速度下降（技能 {}）",
                    pokemon.getDisplayName(false).getString(), event.move().getName());
        } catch (Exception e) {
            LOGGER.error("[Poopy Cobblemon] 番泻叶效果处理失败", e);
        }
    }

    /**
     * 番泻叶的表现：一条便便粒子抛物线从携带者飞向对手，落点爆开一坨便便，
     * 并播放抽水马桶音效。纯表现，不参与结算。
     */
    private static void playSennaAnimation(BattlePokemon user, BattlePokemon target) {
        PokemonEntity holderEntity = user.getEntity();
        PokemonEntity targetEntity = target.getEntity();
        if (targetEntity == null || targetEntity.level().isClientSide) {
            return;
        }
        var level = (net.minecraft.server.level.ServerLevel) targetEntity.level();
        var poop = com.altnoir.poopsky.init.PoParticles.POOP_PARTICLE.get();

        level.playSound(null, targetEntity.getX(), targetEntity.getY(), targetEntity.getZ(),
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "poopsky", "block.flush_toilet.open")),
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.2F, 1.0F);

        if (holderEntity == null) {
            return;
        }
        var start = holderEntity.position();
        var end = targetEntity.position();
        for (int i = 0; i <= 12; i++) {
            double t = i / 12.0;
            double px = start.x + (end.x - start.x) * t;
            double py = start.y + 0.9 + (end.y - start.y) * t + Math.sin(t * Math.PI) * 0.7;
            double pz = start.z + (end.z - start.z) * t;
            level.sendParticles(poop, px, py, pz, 2, 0.04, 0.04, 0.04, 0);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                end.x, end.y + 0.4, end.z, 4, 0.2, 0.2, 0.2, 0.02);
        level.sendParticles(poop, end.x, end.y + 0.5, end.z, 12, 0.25, 0.25, 0.25, 0.03);
    }

    private static void tellBattle(PokemonBattle battle, Component message) {
        battle.broadcastChatMessage(message.copy().withStyle(ChatFormatting.GRAY));
    }

    /**
     * 一触即发触发时的同款爆炸表现：便便粒子爆发（PoopSky 原生 POOP_PARTICLE，
     * 数量/扩散随体型）+ 爆闪 + 爆炸音效。只做纯表现——不造成伤害、不击退、
     * 不破坏方块，避免干扰战斗结算。
     */
    private static void playVergeExplosion(net.minecraft.world.entity.Entity at) {
        if (!(at.level() instanceof net.minecraft.server.level.ServerLevel level) || at.level().isClientSide) {
            return;
        }
        double x = at.getX();
        double y = at.getY(-0.0625);
        double z = at.getZ();

        float radius = 1.0F;
        if (at instanceof PokemonEntity pokemon) {
            radius = Math.max(1.0F, SizeUtil.linearSize(pokemon));
        }

        int count = Math.max(1, Math.round(radius * 30));
        double spread = radius * 0.5;
        double speed = 0.4 + level.random.nextDouble() * 0.4;
        level.sendParticles(com.altnoir.poopsky.init.PoParticles.POOP_PARTICLE.get(),
                x, y, z, count, spread, spread, spread, speed);
        level.sendParticles(radius <= 2
                        ? net.minecraft.core.particles.ParticleTypes.EXPLOSION
                        : net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                x, y, z, Math.max(1, Math.round(radius)), spread, spread, spread, speed);
        level.playSound(null, x, y, z,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                net.minecraft.sounds.SoundSource.NEUTRAL, 2.0F, 1.0F);
    }
}
