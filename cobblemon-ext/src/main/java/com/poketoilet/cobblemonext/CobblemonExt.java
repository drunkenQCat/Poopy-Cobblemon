package com.poketoilet.cobblemonext;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * cobblemon-ext —— Cobblemon 扩展 API 补齐库。
 *
 * <p>把对 Showdown/Cobblemon 内部的“越界访问”收敛到这一个库：
 * <ul>
 *   <li>{@code ExtEvents.MOVE_USED}：每次战斗指令使用技能时触发
 *       （Mixin 进 {@code MoveInstruction.invoke}，Cobblemon 无公开事件）；</li>
 *   <li>{@code ExtEvents.BATTLE_ACTIVE_READY}：战斗的参战位全部分配完毕时触发
 *       （{@code BATTLE_STARTED_POST} 时参战位尚未就绪，Mixin 进
 *       {@code ActiveBattlePokemon.setBattlePokemon} 补齐该时点）；</li>
 *   <li>{@link ExtBridge}：引擎级伤害/能力值变化的桥接（{@code ShowdownService.send}
 *       自定义协议行 + {@code cobblemon_ext_patch.js} 运行时补丁）。</li>
 * </ul>
 *
 * <p>若上游 Cobblemon 未来接受了对应的功能 PR，删除本库中对应的 Mixin 即可，
 * 依赖本库的扩展无需改动。
 */
@Mod(CobblemonExt.MOD_ID)
public class CobblemonExt {

    public static final String MOD_ID = "cobblemon_ext";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CobblemonExt() {
        LOGGER.info("[cobblemon-ext] 扩展 API 补齐库已加载");
    }
}
