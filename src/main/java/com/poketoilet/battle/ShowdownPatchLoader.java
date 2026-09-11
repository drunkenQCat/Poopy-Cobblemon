package com.poketoilet.battle;

import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * MonsterTrainer 模式的 Showdown 补丁器：拿 GraalShowdownService 的 GraalJS
 * {@link com.cobblemon.mod.relocations.graalvm.polyglot.Context}，把
 * {@code assets/poketoilet/showdown/poketoilet_patch.js} 直接 eval 进运行中的引擎。
 *
 * <p>补丁内容（见 JS 文件）：包装 {@code BattleStream._writeLine}，拦截自定义协议行
 * {@code poketoilet_senna} / {@code poketoilet_dragonfruit}（由 Java 侧通过
 * {@code ShowdownService.send(battleId, [">行名 JSON"])} 公开 API 发出），
 * 用引擎原生 API（boostBy/damage）修改真实战斗状态。
 */
public final class ShowdownPatchLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PATCH_RESOURCE = "/assets/poketoilet/showdown/poketoilet_patch.js";

    private static boolean applied;
    private static long attemptCounter;

    private ShowdownPatchLoader() {
    }

    public static boolean isPatched() {
        return applied;
    }

    /**
     * 幂等尝试：引擎服务就绪后把补丁 eval 进去。失败（服务未启动/非 Graal 实现）
     * 会按频率限制重试。
     */
    public static void ensurePatched() {
        if (applied) {
            return;
        }
        // 每 100 次调用尝试一次，避免未就绪阶段刷爆日志
        if (attemptCounter++ % 100 != 0) {
            return;
        }
        try {
            var service = com.cobblemon.mod.common.battles.runner.ShowdownService.Companion.getService();
            if (!(service instanceof GraalShowdownService graal)) {
                LOGGER.warn("[Poketoilet] Showdown 服务不是 Graal 实现({})，补丁不可用",
                        service == null ? "null" : service.getClass().getName());
                return;
            }
            String js;
            try (InputStream in = ShowdownPatchLoader.class.getResourceAsStream(PATCH_RESOURCE)) {
                if (in == null) {
                    LOGGER.error("[Poketoilet] 找不到补丁资源 {}", PATCH_RESOURCE);
                    return;
                }
                js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            graal.getContext().eval("js", js);
            applied = true;
            LOGGER.info("[Poketoilet] Showdown 补丁已注入（番泻叶/帝王火龙果引擎效果可用）");
        } catch (Throwable t) {
            // 引擎尚未启动时会走这里，等下一轮重试
            LOGGER.debug("[Poketoilet] Showdown 补丁注入暂未成功：{}", t.toString());
        }
    }
}
