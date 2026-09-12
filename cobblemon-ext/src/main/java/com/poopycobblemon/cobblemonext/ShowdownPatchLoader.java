package com.poopycobblemon.cobblemonext;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 把 {@code assets/cobblemon_ext/showdown/cobblemon_ext_patch.js} 注入运行中的
 * Showdown 引擎：通过 {@link GraalShowdownService#getContext()} 拿到 GraalJS
 * 上下文后直接 eval（MonsterTrainer 模式）。补丁自身幂等，此处对注入动作也做
 * 一次性保护，并按频率限制重试。
 */
public final class ShowdownPatchLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PATCH_RESOURCE = "/assets/cobblemon_ext/showdown/cobblemon_ext_patch.js";

    private static boolean applied;
    private static long attemptCounter;

    private ShowdownPatchLoader() {
    }

    public static boolean isPatched() {
        return applied;
    }

    public static void ensurePatched() {
        if (applied) {
            return;
        }
        if (attemptCounter++ % 100 != 0) {
            return;
        }
        try {
            var service = com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService();
            if (!(service instanceof GraalShowdownService graal)) {
                CobblemonExt.LOGGER.warn("[cobblemon-ext] Showdown 服务不是 Graal 实现({})，补丁不可用",
                        service == null ? "null" : service.getClass().getName());
                return;
            }
            String js;
            try (InputStream in = ShowdownPatchLoader.class.getResourceAsStream(PATCH_RESOURCE)) {
                if (in == null) {
                    LOGGER.error("[cobblemon-ext] 找不到补丁资源 {}", PATCH_RESOURCE);
                    return;
                }
                js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            graal.getContext().eval("js", js);
            applied = true;
            LOGGER.info("[cobblemon-ext] Showdown 补丁已注入");
        } catch (Throwable t) {
            LOGGER.debug("[cobblemon-ext] Showdown 补丁注入暂未成功：{}", t.toString());
        }
    }

    static void sendLine(PokemonBattle battle, String type, String json) {
        com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService()
                .send(battle.getBattleId(), new String[]{">" + type + " " + json});
    }

    /** 回读引擎内的补丁自诊断状态（写进日志用于排查） */
    public static void logStatus() {
        try {
            var service = com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService();
            if (!(service instanceof GraalShowdownService graal)) {
                return;
            }
            var status = graal.getContext().getBindings("js").getMember("__cobblemonExtStatus");
            LOGGER.info("[cobblemon-ext] 引擎侧状态: {}", status == null ? "null" : status.toString());
        } catch (Throwable t) {
            LOGGER.debug("[cobblemon-ext] 回读状态失败：{}", t.toString());
        }
    }
}
