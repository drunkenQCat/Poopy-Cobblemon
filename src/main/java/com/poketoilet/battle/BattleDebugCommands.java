package com.poketoilet.battle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.logging.LogUtils;
import kotlin.Unit;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;

/** 显式执行、管理员限定的复现与只读诊断；不注入伤害，不改携带物或队伍。 */
public final class BattleDebugCommands {
    private BattleDebugCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("poketoiletdebug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("battle").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
                        context.getSource().sendFailure(Component.literal("已经处于战斗中"));
                        return 0;
                    }
                    var target = player.serverLevel().getEntitiesOfClass(PokemonEntity.class,
                                    player.getBoundingBox().inflate(8),
                                    entity -> entity.getPokemon().getOwnerUUID() == null && entity.getBattleId() == null)
                            .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                    if (target == null) {
                        context.getSource().sendFailure(Component.literal("8 格内没有可战斗的野生宝可梦"));
                        return 0;
                    }
                    BattleBuilder.INSTANCE.pve(player, target)
                            .ifSuccessful(battle -> {
                                LogUtils.getLogger().info("[Poketoilet debug] battle={} target={} hp={}/{}",
                                        battle.getBattleId(), target.getPokemon().getUuid(),
                                        target.getPokemon().getCurrentHealth(), target.getPokemon().getHp());
                                return Unit.INSTANCE;
                            }).ifErrored(error -> {
                                context.getSource().sendFailure(Component.literal(error.toString()));
                                return Unit.INSTANCE;
                            });
                    return 1;
                }))
                .then(Commands.literal("health").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    for (var pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                        String line = "[HP party] " + pokemon.getDisplayName(false).getString() + " "
                                + pokemon.getUuid() + " " + pokemon.getCurrentHealth() + "/" + pokemon.getHp();
                        context.getSource().sendSuccess(() -> Component.literal(line), false);
                        LogUtils.getLogger().info(line);
                    }
                    var battle = BattleRegistry.getBattleByParticipatingPlayer(player);
                    if (battle != null) {
                        for (var active : battle.getActivePokemon()) {
                            var pokemon = active.getBattlePokemon();
                            if (pokemon == null) continue;
                            String line = "[HP battle] " + pokemon.getUuid() + " " + pokemon.getHealth()
                                    + "/" + pokemon.getMaxHealth() + " original="
                                    + pokemon.getOriginalPokemon().getCurrentHealth();
                            context.getSource().sendSuccess(() -> Component.literal(line), false);
                            LogUtils.getLogger().info(line);
                        }
                    }
                    return 1;
                })));
    }
}
