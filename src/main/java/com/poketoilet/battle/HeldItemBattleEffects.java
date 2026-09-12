package com.poketoilet.battle;

import com.altnoir.poopsky.init.PoItems;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import com.poketoilet.cobblemonext.ExtBridge;
import com.poketoilet.cobblemonext.ExtEvents;
import com.poketoilet.util.SizeUtil;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 宝可梦携带 PoopSky 物品时的战斗效果（业务层）。
 *
 * <p>机制全部委托给 cobblemon_ext 库：本类只做“判断携带物 + 计算数值 + 调用桥接”，
 * 对 Showdown 内部的所有访问（事件缺失、引擎注入、协议行）都在库里。
 *
 * <p><strong>当前处于探针实验模式</strong>（{@link #PROBE_MODE}）：在五个候选时点各
 * 对携带者造成 1 点引擎伤害（A 战斗开始 / B 参战位就绪 / C 出球 / D 出球+2秒 /
 * E 首次出招），用于定位引擎可以判定伤害的最早时点。实验结束时把
 * {@code PROBE_MODE} 改回 false 并恢复 {@code onPokemonSent} 里的真实伤害逻辑。
 */
public final class HeldItemBattleEffects {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 临时实验开关：true 时真实伤害关闭，只跑探针 */
    private static final boolean PROBE_MODE = true;

    private static final Map<PokemonBattle, Object> FIRST_MOVE_PROBED =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void register() {
        if (!ModList.get().isLoaded("cobblemon_ext")) {
            LOGGER.warn("[Poketoilet] 未安装 cobblemon_ext 库，携带物品战斗效果不可用");
            return;
        }
        ExtEvents.MOVE_USED.add(HeldItemBattleEffects::onMoveUsed);
        ExtEvents.BATTLE_ACTIVE_READY.add(HeldItemBattleEffects::onBattleActiveReady);

        Consumer<com.cobblemon.mod.common.api.events.battles.BattleStartedEvent.Post> startedHandler =
                event -> probe("A:战斗开始", event.getBattle());
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(startedHandler);

        Consumer<com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent.Post> sentHandler =
                event -> onPokemonSent(event.getPokemon(), event.getPokemonEntity());
        CobblemonEvents.POKEMON_SENT_POST.subscribe(sentHandler);

        LOGGER.info("[Poketoilet] 携带物品战斗效果已注册（探针实验模式）");
    }

    private static boolean isHolding(Pokemon pokemon, Item item) {
        ItemStack held = pokemon.heldItem();
        return !held.isEmpty() && held.is(item);
    }

    /** 在战斗的出战位里找“携带火龙果”的宝可梦（探针实验假定出战的就是携带者） */
    private static BattlePokemon findHolderSelf(PokemonBattle battle) {
        for (ActiveBattlePokemon active : battle.getActivePokemon()) {
            BattlePokemon self = active.getBattlePokemon();
            if (self == null) {
                continue;
            }
            Pokemon pokemon = self.getEffectedPokemon();
            if (pokemon != null && isHolding(pokemon, PoItems.KING_OF_DRAGON_FRUIT.get())) {
                return self;
            }
        }
        return null;
    }

    private static void probe(String label, PokemonBattle battle) {
        try {
            ExtBridge.ensurePatched();
            BattlePokemon self = findHolderSelf(battle);
            if (self == null) {
                LOGGER.info("[Poketoilet] 探针 {}：场上未找到携带火龙果的出战位", label);
                return;
            }
            if (!ExtBridge.isPatched()) {
                LOGGER.info("[Poketoilet] 探针 {}：引擎补丁未注入，跳过", label);
                return;
            }
            ExtBridge.applyDamage(battle, self.getUuid(), 1);
            tellBattle(battle, Component.literal("[探针] " + label + "：-1 HP"));
            LOGGER.info("[Poketoilet] 探针 {}：已发送 1 点伤害", label);
        } catch (Exception e) {
            LOGGER.error("[Poketoilet] 探针 {} 异常", label, e);
        }
    }

    /** 探针 A：战斗开始事件（参战位此时通常尚未分配） */
    private static void onBattleStartedPost(com.cobblemon.mod.common.api.events.battles.BattleStartedEvent.Post event) {
        probe("A:战斗开始", event.getBattle());
    }

    /** 探针 B：参战位就绪事件 */
    private static void onBattleActiveReady(PokemonBattle battle) {
        probe("B:参战位就绪", battle);
    }

    /** 探针 C/D：出球事件 + 出球 2 秒后 */
    private static void onPokemonSent(Pokemon pokemon, PokemonEntity entity) {
        try {
            ServerPlayer owner = pokemon.getOwnerPlayer();
            if (owner == null) {
                return;
            }
            var battle = com.cobblemon.mod.common.battles.BattleRegistry
                    .getBattleByParticipatingPlayer(owner);
            if (battle == null || battle.getEnded()) {
                return;
            }
            if (!isHolding(pokemon, PoItems.KING_OF_DRAGON_FRUIT.get())) {
                return;
            }
            probe("C:出球", battle);
            if (entity != null) {
                entity.after(2.0F, () -> {
                    probe("D:出球+2秒", battle);
                    return Unit.INSTANCE;
                });
            }
        } catch (Exception e) {
            LOGGER.error("[Poketoilet] 出球处理异常", e);
        }
    }

    /** 番泻叶（真实效果保持不变）：每次技能使用 → 敌方速度阶级 -1；探针 E 挂在首次出招 */
    private static void onMoveUsed(ExtEvents.MoveUsedEvent event) {
        try {
            if (PROBE_MODE && FIRST_MOVE_PROBED.put(event.battle(), Boolean.TRUE) == null) {
                probe("E:首次出招", event.battle());
            }
            ExtBridge.ensurePatched();
            if (!ExtBridge.isPatched()) {
                return;
            }
            Pokemon pokemon = event.user().getEffectedPokemon();
            if (pokemon == null || !isHolding(pokemon, PoItems.FOLIUM_SENNAE.get())) {
                return;
            }
            BattlePokemon target = event.target();
            if (target == null) {
                return;
            }
            ExtBridge.applyBoost(event.battle(), target.getUuid(), "spe", -1);
            tellBattle(event.battle(), Component.translatable("message.poketoilet.senna_trigger",
                    target.getName(), event.move().getName()));
            LOGGER.info("[Poketoilet] {} 携带番泻叶使用技能，敌方速度下降（技能 {}）",
                    pokemon.getDisplayName(false).getString(), event.move().getName());
        } catch (Exception e) {
            LOGGER.error("[Poketoilet] 番泻叶效果处理失败", e);
        }
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
