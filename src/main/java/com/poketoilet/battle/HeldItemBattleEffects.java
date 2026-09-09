package com.poketoilet.battle;

import com.altnoir.poopsky.init.PoItems;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.logging.LogUtils;
import com.poketoilet.util.SizeUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * 宝可梦携带 PoopSky 物品时的战斗效果桥接。
 *
 * <ul>
 *   <li><strong>番泻叶</strong>（{@code poopsky:folium_sennae}）：携带后每次使用任意技能
 *       （消耗 PP，{@code MoveInstruction} 每条技能消息触发一次），敌方所有出战宝可梦
 *       速度阶级 -1（可叠加，下限 -6）。通过 Mixin 挂在 Cobblemon 战斗解释器的
 *       {@code MoveInstruction.invoke} 上——Cobblemon 没有公开“使用技能”事件。</li>
 *   <li><strong>帝王火龙果</strong>（{@code poopsky:king_of_dragon_fruit}）：每次进入战斗
 *       （{@code BATTLE_STARTED_POST}），对自己造成固定 1% 最大生命的伤害，对敌方每只
 *       出战宝可梦造成 {@code 线性体型 × 等级} 点伤害（线性体型 = 碰撞箱体积的等效
 *       立方边长，见 {@link SizeUtil}；种族差异直接体现）。</li>
 * </ul>
 *
 * <p>扣血直接写 {@code Pokemon.setCurrentHealth} 并 {@code sendUpdate} 同步 UI；
 * 一律<strong>保底留 1 HP</strong>（不直接打倒）——战斗血量的权威在 Showdown 引擎，
 * 直接打到 0 会与引擎脱同步造成状态错乱。
 */
public final class HeldItemBattleEffects {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HeldItemBattleEffects() {
    }

    /** 在模组构造时调用一次 */
    public static void register() {
        // 显式 Consumer 类型：subscribe 同时有 Function1 重载，方法引用会产生歧义
        java.util.function.Consumer<BattleStartedEvent.Post> handler = HeldItemBattleEffects::onBattleStarted;
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(handler);
        LOGGER.info("[Poketoilet] 携带物品战斗效果已注册（番泻叶 / 帝王火龙果）");
    }

    private static boolean isHolding(Pokemon pokemon, Item item) {
        ItemStack held = pokemon.heldItem();
        return !held.isEmpty() && held.is(item);
    }

    /** 番泻叶：每次技能使用 → 敌方速度阶级 -1 */
    public static void onMoveUsed(MoveInstruction instruction, PokemonBattle battle) {
        try {
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
                int next = Math.max(-6, target.getStatChanges().getOrDefault(Stats.SPEED, 0) - 1);
                target.getStatChanges().put(Stats.SPEED, next);
                target.sendUpdate();
            }
            LOGGER.info("[Poketoilet] {} 携带番泻叶使用技能，敌方速度下降（技能 {}）",
                    pokemon.getDisplayName(false).getString(), instruction.getMove().getName());
        } catch (Exception e) {
            LOGGER.error("[Poketoilet] 番泻叶效果处理失败", e);
        }
    }

    /** 帝王火龙果：进入战斗 → 自伤 1% 最大生命，敌方按 体型 × 等级 扣血 */
    private static void onBattleStarted(BattleStartedEvent.Post event) {
        try {
            PokemonBattle battle = event.getBattle();
            for (ActiveBattlePokemon active : battle.getActivePokemon()) {
                BattlePokemon self = active.getBattlePokemon();
                if (self == null) {
                    continue;
                }
                Pokemon pokemon = self.getEffectedPokemon();
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

                applyDamage(self, selfDamage);
                for (ActiveBattlePokemon other : battle.getActivePokemon()) {
                    if (other == active) {
                        continue;
                    }
                    BattlePokemon target = other.getBattlePokemon();
                    if (target == null || target.getActor() == self.getActor()) {
                        continue;
                    }
                    applyDamage(target, enemyDamage);
                }
                LOGGER.info("[Poketoilet] {} 携带帝王火龙果进入战斗：自损 {} HP，敌方各损 {} HP（体型边长 x{}，等级 {}）",
                        pokemon.getDisplayName(false).getString(), selfDamage, enemyDamage, size, pokemon.getLevel());
            }
        } catch (Exception e) {
            LOGGER.error("[Poketoilet] 帝王火龙果效果处理失败", e);
        }
    }

    private static void applyDamage(BattlePokemon target, int amount) {
        Pokemon pokemon = target.getEffectedPokemon();
        int health = Math.max(1, pokemon.getCurrentHealth() - Math.max(1, amount));
        pokemon.setCurrentHealth(health);
        target.sendUpdate();
    }
}
