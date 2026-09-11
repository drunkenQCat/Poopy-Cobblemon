# Poketoilet —— PoopSkyMod × Cobblemon 附属模组

“天空宝可梦厕所 v1.0”整合包专用 **addon**：在 [PoopSkyMod](https://github.com/Altnoir/PoopSkyMod)
和 [Cobblemon](https://github.com/Cobblemon-Global/Cobblemon) 之间搭桥——让宝可梦能坐上 PoopSky 的厕所。

## 玩法

文案上区分**厕所**（`AbstractToiletBlock` 系列，蹲坑式）和**马桶**
（`poopsky:flush_toilet` / `poopsky:golden_flush_toilet`，坐式抽水马桶）。

1. **宝可梦上厕所/马桶**：先用**拴绳**拴住一只宝可梦（或其他可拴生物），然后右键
   PoopSky 的厕所或马桶——**手上拿什么都行**，判断依据是拴绳状态而不是手持物品，
   玩家自己不上，宝可梦被牵过去坐下；相邻坑位互不影响（座椅严格归属单个方块坐标）。
   - **厕所**：坐上本模组的隐形座椅（`poketoilet:seat`），每 2 秒调用 PoopSky 的
     `ToiletUtil.onPoop` 产出大便（金厕所产金大便）。
   - **马桶**：直接坐上 PoopSky 自带的 `FlushToiletEntity` 座椅，排便、金马桶判断、
     冲水收纳全部走 PoopSky 原生逻辑；马桶盖关着时会提示先掀盖。
   - 宝可梦用 `startRiding(seat, true)` 强制乘骑，绕过 Cobblemon `PokemonEntity.canRide`
     的平台类型限制。
2. **玩家坐下**：空手右键 PoopSky 厕所 → 玩家自己坐下持续排便；空手右键马桶
   → 不干预，PoopSky 原生就会让玩家坐上去。
3. **一触即发桥接**：宝可梦带着 PoopSky 的“一触即发”效果（如被泼对应药水）坐上
   厕所/马桶时，立即触发（坐着期间被施加也一样：厕所走 `SeatEntity.tick`、
   马桶走 `FlushToiletEntity.tick` 的 Mixin 兜底，均为每 tick）：
   - ANAL_PRESSING 配方命中（矿石压制类特殊方块）→ 与玩家下蹲完全一致：拆厕 + 下方块转换；
     普通厕所（无配方）→ 厕所直接轰碎（掉落本体）；
   - 爆炸走 PoopSky 原生 `PoopTntUtil.triggerExplosion`（不自写爆炸逻辑），半径与
     **宝可梦体型**挂钩：`min(18, max(1, round(2 × 线性体型)))`。线性体型 =
     ∛(碰撞箱体积)（`SizeUtil`），种族基础体型与倍率都体现在碰撞箱里，
     巨浪鼬和小碎钻有数量级差异，与效果等级无关。
4. **携带物品战斗效果**（PoopSky 物品 + Cobblemon 战斗）：
   - 两件物品已通过 datapack 标签加入 Cobblemon 的 `held/is_held_item` 与
     `held/whitelisted_items_to_hold` 白名单（`"replace": false` 合并）。
     装备方式：**手持物品 → 潜行+右键自己的宝可梦 → 交互轮盘选"携带物品"**
     （服务端取主手物品装备；注意别选成旁边的"装饰物品"，也别空手开轮盘）。
     机制：官方 `whitelisted_items_to_hold` 默认为空 = 全部放行（仅黑名单挡
     容器类）；显式加白名单是防其他数据包启用名单后这两件被排除。
   - **番泻叶**（`poopsky:folium_sennae`）：携带后每次使用任意技能（消耗 PP），
     敌方所有出战宝可梦速度阶级 -1（真实 boost，可叠加至 -6，影响出手顺序）。
   - **帝王火龙果**（`poopsky:king_of_dragon_fruit`）：每次进入战斗对敌方每只
     出战宝可梦造成 `线性体型 × 等级` 点**引擎真实伤害**（保底 1 HP）。
   - **帝王火龙果**（`poopsky:king_of_dragon_fruit`）：每次进入战斗，对自己造成
     固定 1% 最大生命的伤害，对敌方每只出战宝可梦造成 `线性体型 × 等级` 点伤害
     （线性体型 = ∛(碰撞箱体积)，见 `SizeUtil`）。
   - 触发时通过 `PokemonBattle.broadcastChatMessage` 写入**战斗界面的战报文本流**
     （灰色文本，双方与观战者可见），触发与否一眼可辨。
   - 两种扣血一律**保底留 1 HP**：战斗血量权威在 Showdown 引擎，直接打至 0 会
     脱同步；"恶系"为风味设定，实际按上述固定公式结算。
   - 实现采用 **MonsterTrainer 模式**：`ShowdownPatchLoader` 把
     `assets/poketoilet/showdown/poketoilet_patch.js` eval 进 GraalJS 引擎上下文
     （包装 `BattleStream._writeLine`），Java 侧通过 `ShowdownService.send` 发送
     自定义协议行 `>poketoilet_senna/-dragonfruit {uuid,amount}`，由补丁用引擎
     原生 API（boostBy/damage）结算——速度箭头、战报、出手顺序全部真实。
   - 无头验证：`node dev/showdowntest/headless_test.js <整合包>/minecraft/showdown
     dev/src/main/resources/assets/poketoilet/showdown/poketoilet_patch.js`
5. **体型扫描仪**（`poketoilet:scale_scanner`，本模组物品）：对宝可梦右键读取其
   碰撞箱体积、等效边长（即上面两处公式所用的"线性体型"）与倍率参考值。
   合成：玻璃/铁锭/木棍竖排一列；也出现在创造物品栏"工具与实用物品"页。
6. **原版机制不受影响**：玩家站在 PoopSky 厕所上按 Shift 蹲坑，本来就是 PoopSky 自带功能，本模组不干预。

## 依赖（`mods.toml` 均为 required）

| 模组 | 版本要求 | 说明 |
|---|---|---|
| neoforge | [21.1.240,) | 必装 |
| minecraft | [1.21.1,1.22) | 必装 |
| **poopsky** | [2.2,) | 整合包内已有 `poopsky-2.2+NeoForge1.21.1-Hotfix2.jar` |
| **cobblemon** | [1.7.0,) | 整合包内已有 `Cobblemon-neoforge-1.7.3+1.21.1.jar` |

## 工作原理（源码导读）

```
src/main/java/com/poketoilet/
├── Poketoilet.java                  # @Mod 入口：注册 poketoilet:seat 实体
└── content/
    ├── entity/SeatEntity.java       # 隐形座椅：每 40 tick 检查下方是否为 PoopSky
    │                                #   厕所(AbstractToiletBlock)且有乘客 → 调
    │                                #   ToiletUtil.onPoop() 产出 PoopSky 大便
    └── handler/ToiletAddonEvents.java # PlayerInteractEvent.RightClickBlock：
                                       #   拴绳右键 → 牵附近拴着的宝可梦
                                       #   (PokemonEntity 优先) 坐下；空手 → 玩家坐下
```

关键 API 直接来自两个前置模组：

- `com.altnoir.poopsky.content.block.abs.AbstractToiletBlock`（PoopSky 所有厕所的父类）
- `com.altnoir.poopsky.impl.util.ToiletUtil`（`onPoop` / `isGoldenToilet` 等，全部 public static）
- `com.cobblemon.mod.common.entity.pokemon.PokemonEntity`（Cobblemon 的宝可梦实体）

> 注意：Cobblemon 的 `com.cobblemon.mod.common.pokemon.Pokemon` 是**数据类**不是实体，
> 场上宝可梦的实例类型是 `PokemonEntity`，判断宝可梦要用后者。

## 构建

**依赖来源**：`dev/libs/` 里是从整合包 `minecraft/mods/` 复制进来的 jar
（`poopsky-2.2+NeoForge1.21.1-Hotfix2.jar`、`cobblemon-1.7.3.jar`、`kotlinforforge-5.12.0-all.jar`），
以 **compileOnly** 引入（只编译不打包；运行时由整合包里的本体提供）。
Cobblemon 本体是 Kotlin 写的，编译期需要 Kotlin runtime（kotlinforforge 提供）。
如果以后整合包升级了这两个模组，把新 jar 覆盖到 `dev/libs/` 同名即可。

```bat
set JAVA_HOME=C:\Users\user\.jdks\ms-21.0.9
set GRADLE_OPTS=-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890
cd /d "C:\Users\user\AppData\Roaming\PrismLauncher\instances\天空宝可梦厕所 v1.0\dev"
gradlew.bat build          # 编译 + 打包 build/libs/poketoilet-1.0.0.jar
gradlew.bat installToInstance   # 覆盖安装进 minecraft\mods（自动清旧版）
```

或在 PowerShell 里执行 `powershell -ExecutionPolicy Bypass -File dev\build.ps1`
（.ps1 无法直接双击运行，这是 Windows 默认策略；脚本自带 jar 存在性检查）。

## 无头验证（已完成）

`gradlew runServer` 曾在纯服务器模式下同场加载并启动：
`Poketoilet + PoopSky 2.1.3 + Cobblemon 1.7.3 + KotlinForForge`，
模组列表识别、`Poketoilet 已加载：PoopSkyMod + Cobblemon 附属模组` 日志、
Cobblemon 数据初始化（1025 个物种）、PoopSky 130 种厕所类型载入、
服务器 `Done` 完成世界生成——全部无异常。
要复现：先把 `dev/libs/*.jar`（及需要时更多依赖）复制到 `dev/run/mods/`。

## ⚠️ 网络与代理（本机必备）

本机命令行 HTTPS 是坏的，Gradle 下载一律要走 `127.0.0.1:7890` 代理——
参数已写进 `dev/gradle.properties` 和 `dev/build.ps1`，Clash 开着就能编译；
（注：`build.bat` 因中文注释的编码问题已废弃，改为 `build.ps1`）；
修好网络或换端口后请同步改这两处。

## 踩过的坑（给续作者）

1. **addon 对本体模组要用 `compileOnly` + `run/mods` 放 jar**：若用 `implementation`，
   本体依赖会二次进 classpath，ModLauncher 模块层报
   `Modules x and y export package ... to module minecraft`（Registrate 双副本真实踩过）。
2. **NeoForge 21.1 的 `useItemOn` 返回 `ItemInteractionResult`**（不是旧 `InteractionResult`）。
3. **拴绳方法在 `Mob` 上**：`getLeashHolder()` / `dropLeash(boolean, boolean)` 不在 `LivingEntity` 上。

## 声明

玩法设计桥接自主流模组 PoopSkyMod（MIT，作者 Altnoir 等）与 Cobblemon（MPL-2.0，
The Cobblemon Team）。本工程不含两者的代码与资源，仅按公开 API 调用；代码许可：All rights reserved。