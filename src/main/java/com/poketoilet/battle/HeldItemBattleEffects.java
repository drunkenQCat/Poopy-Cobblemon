package com.poketoilet.battle;

import com.altnoir.poopsky.init.PoItems;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import com.poketoilet.util.SizeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宝可梦携带 PoopSky 物品时的战斗效果桥接。
 *
 * <ul>
 *   <li><strong>番泻叶</strong>（{@code poopsky:folium_sennae}）：携带后每次使用任意技能
 *       （消耗 PP，{@code MoveInstruction} 每条技能消息触发一次），敌方所有出战宝可梦
 *       速度阶级 -1（可叠加，下限 -6）。通过 Mixin 挂在 Cobblemon 战斗解释器的
 *       {@code MoveInstruction.invoke} 上——Cobblemon 没有公开“使用技能”事件。</li>
 *   <li><strong>帝王火龙果</strong>（{@code poopsky:king_of_dragon_fruit}）：每次进入战斗
 *       对自己造成固定 1% 最大生命的伤害，对敌方每只出战宝可梦造成
 *       {@code 线性体型 × 等级} 点伤害（线性体型 = 碰撞箱体积的等效立方边长，见
 *       {@link SizeUtil}；种族差异直接体现）。触发时播放一触即发同款爆炸动画，
 *       并写入战斗界面的战报文本流。</li>
 * </ul>
 *
 * <p><strong>时序</strong>：{@code BATTLE_STARTED_POST} 触发时参战位往往尚未分配
 * （出战是战斗开始后的指令），所以只把战斗登记进待处理表，由服务端 tick 每 5 tick
 * 轮询补判：参战位就绪且有携带者 → 触发；全部就绪但无携带者 → 提前放弃；
 * 超过 40 次轮询（约 20 秒）仍未就绪 → 放弃并告警。
 *
 * <p>扣血直接写 {@code Pokemon.setCurrentHealth} 并 {@code sendUpdate} 同步 UI；
 * 一律<strong>保底留 1 HP</strong>（不直接打倒）——战斗血量的权威在 Showdown 引擎，
 * 直接打到 0 会与引擎脱同步造成状态错乱。
 */
public final class HeldItemBattleEffects {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 待补判的战斗：id → 战斗对象 */
    private static final Map<UUID, PendingBattle> PENDING = new ConcurrentHashMap<>();

    private static long tickCounter;

    private HeldItemBattleEffects() {
    }

    /** 在模组构造时调用一次 */
    public static void register() {
        // 显式 Consumer 类型：subscribe 同时有 Function1 重载，方法引用会产生歧义
        java.util.function.Consumer<BattleStartedEvent.Post> handler =
                event -> PENDING.put(event.getBattle().getBattleId(), new PendingBattle(event.getBattle()));
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(handler);
        LOGGER.info("[Poketoilet] 携带物品战斗效果已注册（番泻叶 / 帝王火龙果）");
    }

    private static boolean isHolding(Pokemon pokemon, Item item) {
        ItemStack held = pokemon.heldItem();
        return !held.isEmpty() && held.is(item);
    }

    /** 由 BattleTickHooks 在服务端 tick 调用：轮询补判待处理的战斗 */
    public static void tickPending() {
        ShowdownPatchLoader.ensurePatched();
        if (PENDING.isEmpty()) {
            return;
        }
        if (++tickCounter % 5 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, PendingBattle>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingBattle pending = iterator.next().getValue();
            pending.attempts++;
            CheckResult result = attemptDragonFruit(pending.battle);
            if (result != CheckResult.NOT_READY) {
                iterator.remove();
            } else if (pending.attempts >= 40) {
                LOGGER.warn("[Poketoilet] 战斗 {} 轮询 40 次参战位仍未就绪，放弃帝王火龙果检查",
                        pending.battle.getBattleId());
                iterator.remove();
            }
        }
    }

    private enum CheckResult { APPLIED, NO_HOLDER, NOT_READY }

    /**
     * 帝王火龙果检查：参战位就绪且有携带者 → 触发（动画 + 扣血 + 战报）。
     */
    private static CheckResult attemptDragonFruit(PokemonBattle battle) {
        boolean allAssigned = true;
        boolean applied = false;
        StringBuilder actives = new StringBuilder();
        try {
            for (ActiveBattlePokemon active : battle.getActivePokemon()) {
                BattlePokemon self = active.getBattlePokemon();
                if (self == null) {
                    allAssigned = false;
                    continue;
                }
                Pokemon pokemon = self.getEffectedPokemon();
                if (actives.length() > 0) {
                    actives.append(", ");
                }
                actives.append(pokemon == null ? "?" : pokemon.getDisplayName(false).getString())
                        .append("[携带=").append(pokemon == null || pokemon.heldItem().isEmpty()
                                ? "无" : pokemon.heldItem().getItem()).append(']');
                if (pokemon == null || !isHolding(pokemon, PoItems.KING_OF_DRAGON_FRUIT.get())) {
                    continue;
                }
                // 线性体型：碰撞箱体积的等效立方边长（种族差异 + 倍率都体现在碰撞箱里）
                float size = 1.0F;
                if (self.getEntity() != null) {
                    size = Math.max(0.1F, SizeUtil.linearSize(self.getEntity()));
                }
                int selfDamage = Math.max(1, Math.round(self.getMaxHealth() * 0.01F));
                int enemyDamage = Math.max(1, Math.round(size * pokemon.getLevel()));

                // 伤害交由引擎原生结算（保底 1 HP 的钳制在补丁 JS 里）
                sendLine(battle, "poketoilet_dragonfruit", self.getUuid(), selfDamage);
                if (self.getEntity() != null) {
                    playVergeExplosion(self.getEntity());
                }
                for (ActiveBattlePokemon other : battle.getActivePokemon()) {
                    if (other == active) {
                        continue;
                    }
                    BattlePokemon target = other.getBattlePokemon();
                    if (target == null || target.getActor() == self.getActor()) {
                        continue;
                    }
                    sendLine(battle, "poketoilet_dragonfruit", target.getUuid(), enemyDamage);
                    if (target.getEntity() != null) {
                        playVergeExplosion(target.getEntity());
                    }
                    tellBattle(battle, Component.translatable("message.poketoilet.dragonfruit_trigger",
                            pokemon.getDisplayName(false), selfDamage, target.getName(), enemyDamage));
                }
                applied = true;
                LOGGER.info("[Poketoilet] {} 携带帝王火龙果进入战斗：自损 {} HP，敌方各损 {} HP（体型边长 x{}，等级 {}）",
                        pokemon.getDisplayName(false).getString(), selfDamage, enemyDamage, size, pokemon.getLevel());
            }
            if (applied) {
                LOGGER.info("[Poketoilet] 战斗 {} 参战位：{}", battle.getBattleId(), actives);
                return CheckResult.APPLIED;
            }
            return allAssigned ? CheckResult.NO_HOLDER : CheckResult.NOT_READY;
        } catch (Exception e) {
            LOGGER.error("[Poketoilet] 帝王火龙果效果处理失败", e);
            return CheckResult.NO_HOLDER; // 出错不再重试，避免刷屏
        }
    }

    /** 番泻叶：每次技能使用 → 敌方速度阶级 -1（引擎原生 boost） */
    public static void onMoveUsed(MoveInstruction instruction, PokemonBattle battle) {
        try {
            ShowdownPatchLoader.ensurePatched();
            if (!ShowdownPatchLoader.isPatched()) {
                return; // 补丁未注入时引擎侧效果不可用
            }
            BattlePokemon user = instruction.getUserPokemon();
            if (user == null) {
                return;
            }
            Pokemon pokemon = user.getEffectedPokemon();
            if (pokemon == null || !isHolding(pokemon, PoItems.FOLIUM_SENNAE.get())) {
                return;
            }
            for (ActiveBattlePokemon active : battle.getActivePokemon()) {
                BattlePokemon target = active.getBattlePokemon();
                if (target == null || target.getActor() == user.getActor()) {
                    continue;
                }
                sendLine(battle, "poketoilet_senna", target.getUuid(), 1);
                tellBattle(battle, Component.translatable("message.poketoilet.senna_trigger",
                        target.getName(), instruction.getMove().getName()));
            }
            LOGGER.info("[Poketoilet] {} 携带番泻叶使用技能，敌方速度下降（技能 {}）",
                    pokemon.getDisplayName(false).getString(), instruction.getMove().getName());
        } catch (Exception e) {
            LOGGER.error("[Poketoilet] 番泻叶效果处理失败", e);
        }
    }

    /**
     * MonsterTrainer 模式的触发通道：通过 {@code ShowdownService.send} 写入自定义
     * 协议行（{@code >行名 JSON}），由注入的补丁 JS 拦截后用引擎原生 API 结算。
     */
    private static void sendLine(PokemonBattle battle, String type, UUID targetUuid, int amount) {
        com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService()
                .send(battle.getBattleId(),
                        new String[]{">" + type + " {\"target\":\"" + targetUuid + "\",\"amount\":" + amount + "}"});
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
        if (at instanceof PokemonEntity pokemon) {
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

    private static void tellBattle(PokemonBattle battle, Component message) {
        // 写入战斗界面的战报文本流：broadcastChatMessage 会通过 BattleMessagePacket
        // 广播给双方玩家与观战者，并记入战斗的 chatLog
        battle.broadcastChatMessage(message.copy().withStyle(ChatFormatting.GRAY));
    }

    private static final class PendingBattle {
        final PokemonBattle battle;
        int attempts;

        PendingBattle(PokemonBattle battle) {
            this.battle = battle;
        }
    }
}
