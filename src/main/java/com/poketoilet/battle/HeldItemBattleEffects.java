package com.poketoilet.battle;

import com.altnoir.poopsky.init.PoItems;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import com.poketoilet.cobblemonext.ExtBridge;
import com.poketoilet.cobblemonext.ExtEvents;
import com.poketoilet.util.SizeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * 宝可梦携带 PoopSky 物品时的战斗效果（业务层）。
 *
 * <p>机制全部委托给 cobblemon_ext 库：本类只做“判断携带物 + 计算数值 + 调用桥接”，
 * 对 Showdown 内部的所有访问（事件缺失、引擎注入、协议行）都在库里。
 *
 * <ul>
 *   <li><strong>番泻叶</strong>（{@code poopsky:folium_sennae}）：每次使用任意技能，
 *       敌方所有出战宝可梦速度阶级 -1（引擎原生 boost，可叠加至 -6）。</li>
 *   <li><strong>帝王火龙果</strong>（{@code poopsky:king_of_dragon_fruit}）：每次进入战斗，
 *       自身固定损失 1% 最大生命，敌方每只出战宝可梦受到
 *       {@code 线性体型 × 等级} 点引擎真实伤害（保底留 1 HP），并播放一触即发同款爆炸动画。</li>
 * </ul>
 */
public final class HeldItemBattleEffects {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HeldItemBattleEffects() {
    }

    /** 在模组构造时调用一次 */
    public static void register() {
        if (!ModList.get().isLoaded("cobblemon_ext")) {
            LOGGER.warn("[Poketoilet] 未安装 cobblemon_ext 库，携带物品战斗效果不可用");
            return;
        }
        ExtEvents.MOVE_USED.add(HeldItemBattleEffects::onMoveUsed);
        java.util.function.Consumer<com.cobblemon.mod.common.api.events.pokemon.PokemonSentEvent.Post> sentHandler =
                event -> onPokemonSent(event.getPokemon());
        com.cobblemon.mod.common.api.events.CobblemonEvents.POKEMON_SENT_POST.subscribe(sentHandler);
        LOGGER.info("[Poketoilet] 携带物品战斗效果已注册（番泻叶 / 帝王火龙果）");
    }

    private static boolean isHolding(Pokemon pokemon, Item item) {
        ItemStack held = pokemon.heldItem();
        return !held.isEmpty() && held.is(item);
    }

    /** 番泻叶：每次技能使用 → 敌方速度阶级 -1（引擎原生 boost） */
    private static void onMoveUsed(ExtEvents.MoveUsedEvent event) {
        try {
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

    /**
     * 帝王火龙果：宝可梦被派上场（“去吧！”之后，{@code POKEMON_SENT_POST}）时触发。
     * 自身固定损失 1% 最大生命，敌方每只出战宝可梦受到
     * {@code 线性体型 × 等级} 点引擎真实伤害（保底留 1 HP），并播放爆炸动画。
     * 中途换上场也会触发（每次上场都算一次进入战斗）。
     */
    private static void onPokemonSent(Pokemon pokemon) {
        try {
            ExtBridge.ensurePatched();
            net.minecraft.server.level.ServerPlayer owner = pokemon.getOwnerPlayer();
            var battle = owner == null ? null
                    : com.cobblemon.mod.common.battles.BattleRegistry
                            .getBattleByParticipatingPlayer(owner);
            boolean inBattle = battle != null && !battle.getEnded();
            boolean holding = isHolding(pokemon, PoItems.KING_OF_DRAGON_FRUIT.get());
            LOGGER.info("[Poketoilet] 出球事件：{} owner={} 在战斗中={} 携带火龙果={} 补丁={}",
                    pokemon.getDisplayName(false).getString(), owner != null, inBattle, holding,
                    ExtBridge.isPatched());
            if (!inBattle || !holding) {
                return;
            }
            if (!ExtBridge.isPatched()) {
                LOGGER.info("[Poketoilet] 引擎补丁未注入，跳过帝王火龙果触发");
                return;
            }

            BattlePokemon self = null;
            for (ActiveBattlePokemon active : battle.getActivePokemon()) {
                BattlePokemon bp = active.getBattlePokemon();
                if (bp != null && bp.getEffectedPokemon() == pokemon) {
                    self = bp;
                    break;
                }
            }
            if (self == null) {
                LOGGER.info("[Poketoilet] {} 在战斗中但未找到对应参战位", pokemon.getDisplayName(false).getString());
                return;
            }

            float size = 1.0F;
            if (self.getEntity() != null) {
                size = Math.max(0.1F, SizeUtil.linearSize(self.getEntity()));
            }
            int selfDamage = Math.max(1, Math.round(self.getMaxHealth() * 0.01F));
            int enemyDamage = Math.max(1, Math.round(size * pokemon.getLevel()));

            // 伤害交由引擎原生结算（保底 1 HP 的钳制在库补丁 JS 里）
            ExtBridge.applyDamage(battle, self.getUuid(), selfDamage);
            if (self.getEntity() != null) {
                playVergeExplosion(self.getEntity());
            }
            for (ActiveBattlePokemon other : battle.getActivePokemon()) {
                BattlePokemon target = other.getBattlePokemon();
                if (target == null || target.getActor() == self.getActor()) {
                    continue;
                }
                ExtBridge.applyDamage(battle, target.getUuid(), enemyDamage);
                if (target.getEntity() != null) {
                    playVergeExplosion(target.getEntity());
                }
                tellBattle(battle, Component.translatable("message.poketoilet.dragonfruit_trigger",
                        pokemon.getDisplayName(false), selfDamage, target.getName(), enemyDamage));
            }
            LOGGER.info("[Poketoilet] {} 携带帝王火龙果上场：自损 {} HP，敌方各损 {} HP（体型边长 x{}，等级 {}）",
                    pokemon.getDisplayName(false).getString(), selfDamage, enemyDamage, size, pokemon.getLevel());
        } catch (Exception e) {
            LOGGER.error("[Poketoilet] 帝王火龙果效果处理失败", e);
        }
    }

    private static void tellBattle(PokemonBattle battle, Component message) {
        // 写入战斗界面的战报文本流：broadcastChatMessage 会通过 BattleMessagePacket
        // 广播给双方玩家与观战者，并记入战斗的 chatLog
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

        // 体型越大炸得越猛：半径按线性体型（普通宝可梦≈1）
        float radius = 1.0F;
        if (at instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pokemon) {
            radius = Math.max(1.0F, SizeUtil.linearSize(pokemon));
        }

        // 与 PoopSky PoopTntUtil.spawnPoopParticle 相同的粒子配方
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
