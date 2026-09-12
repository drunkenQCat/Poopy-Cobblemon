# Poketoilet 1.2

**PoopSky × Cobblemon addon · Minecraft 1.21.1 · NeoForge · Java 21**

[中文](#中文) · [English](#english) · [Releases](https://github.com/drunkenQCat/poketoilet/releases) · [Issues](https://github.com/drunkenQCat/poketoilet/issues)

## 中文

让宝可梦坐上 PoopSky 的厕所和抽水马桶，并为 PoopSky 携带物增加 Cobblemon 战斗效果。最初为“天空宝可梦厕所”整合包开发；现在提供独立构建与发布流程。发布包不包含整合包或前置模组。

### 安装与兼容性

从 [Releases](https://github.com/drunkenQCat/poketoilet/releases) 下载同版的 **poketoilet-1.2.jar** 和 **cobblemon-ext-1.2.jar**，放入客户端与服务端的 `mods`。退出游戏后替换旧 JAR，每个模组只保留一个版本。

| 组件 | 声明要求 | 1.2 验证基线 |
| --- | --- | --- |
| Java | Minecraft / Cobblemon 所需 Java 21 | Java 21 |
| Minecraft | `[1.21.1,1.22)` | **1.21.1** |
| NeoForge | `21.1.240+` | **21.1.240** |
| [PoopSky](https://github.com/Altnoir/PoopSkyMod/releases) | `2.1.3+` | **2.2+NeoForge1.21.1-Hotfix2** |
| [Cobblemon](https://modrinth.com/mod/cobblemon) | `1.7.0+` | **1.7.3 NeoForge** |
| [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) | Cobblemon 1.7.3 运行时要求 `5.3+` | **5.12.0** |
| cobblemon-ext | 安装时须为 `1.2+` | **1.2** |

**允许加载的版本范围不代表全部经过验证。** 本项目使用 Cobblemon 内部 Mixin 和 Showdown 桥接，升级前置后需要重新测试。Fabric、Forge、其他 Minecraft 版本未验证。扩展库在主模组元数据中是可选依赖，但携带物战斗效果依赖它；本发布按两个 JAR 配套安装验证。

### 玩法

- **宝可梦坐厕：**拴住宝可梦，再右键 PoopSky 厕所或马桶。依据拴绳状态判断，手上可以拿其他物品。抽水马桶需先开盖。普通厕所每 40 tick（20 TPS 下约 2 秒）调用 PoopSky 排便逻辑；抽水马桶使用 PoopSky 原生座椅、排便和冲水逻辑。
- **玩家坐厕：**空手右键普通厕所即可坐下。PoopSky 原有的下蹲排便和马桶交互仍按原机制运行。
- **一触即发：**宝可梦带着该效果坐厕，或坐着时获得效果，会触发 PoopSky 爆炸及配方转换；无配方时破坏厕所并掉落本体。爆炸半径随碰撞箱等效边长变化，上限 18，会改变世界方块。
- **体型扫描仪：**用 `poketoilet:scale_scanner` 右键宝可梦查看体积、等效边长与倍率参考。合成：玻璃、铁锭、木棍从上到下竖排一列；也在创造物品栏“工具与实用物品”中。

**装备携带物：**主手拿物品，潜行并右键自己的宝可梦，在交互轮盘选择“携带物品”，不要选“装饰物品”。本模组追加携带物标签，不替换已有标签。

| 物品 | 当前行为 |
| --- | --- |
| 番泻叶 `poopsky:folium_sennae` | 招式指令有可解析目标时，该目标速度阶级降低 1，最低 -6；不消耗。当前代码处理招式目标，并非无条件影响敌方全场；没有目标的指令不会降速。 |
| 帝王火龙果 `poopsky:king_of_dragon_fruit` | 达到配置等级上限的火属性宝可梦在入场动画结束且战斗就绪后立即触发；其他宝可梦需完整在场经历一回合。首发通常在第二回合开始触发；回合中换入要再经历下一个完整回合。结算后消耗。 |

等待期间换下、倒下、失去果实或战斗结束会取消等待；重新上场重新计时。换下和战斗结束本身不消耗果实。效果写入战报，并由 Showdown 引擎结算。

火龙果自身伤害请求为 `max(1, round(最大 HP × 1%))`。每个存活敌方出战目标的伤害请求为：

```text
r = 攻击方碰撞箱体积 / 目标碰撞箱体积
f(r) = r                     当 r ≤ 1
       1 + ln(r) / ln(500)    当 r > 1
伤害 = max(1, round(目标最大 HP × 25% × max(0, f(r) + (攻击方等级 - 目标等级) / 100)))
```

双方扣血均**至少剩余 1 HP**，实际伤害可能低于请求值。同体积同等级约扣敌方最大 HP 的 25%，500 倍体积同等级约扣 50%，有整数取整。体积有 0.1 下限和实体缺失时的回退值；应结合扫描仪观察，不应仅凭画面估算。

### 构建与验证

准备 **JDK 21**（设置 `JAVA_HOME`）、**Node.js 22**、**Python 3.11+**（CI 使用 3.12），确保 `java`、`node`、`python` 可用。在仓库根目录运行：

```sh
python scripts/build.py
```

Windows PowerShell 也可运行 `./build.ps1`。默认只构建、测试、打包，不安装游戏或发布到 GitHub。

脚本按 [`scripts/dependencies.json`](scripts/dependencies.json) 的固定 URL 下载并校验 Cobblemon、PoopSky，提取其中的 Registrate 与 Showdown 引擎到忽略提交的 `.deps/`，依次构建扩展库和主模组。不需要 PrismLauncher、私人 `libs/`、存档或额外安装 Gradle。第三方输入不打入发布 JAR。

完整构建包括：发布脚本失败分支测试、19 项 Java 调度检查、11 项真实 Showdown 桥接回归和无界面对战检查；打包时检查版本、元数据、Mixin 类、补丁及未打入依赖。产物是 `dist/` 下的两个 JAR 和 `SHA256SUMS.txt`。

```sh
# Linux 校验
cd dist && sha256sum -c SHA256SUMS.txt
```

```powershell
# Windows：与 SHA256SUMS.txt 中对应条目比较
Get-FileHash dist/*.jar -Algorithm SHA256
```

网络需访问 Gradle、Maven Central、NeoForge Maven、GitHub、Modrinth CDN。仓库没有固定代理；如需代理，在个人 `~/.gradle/gradle.properties` 设置 `systemProp.http.proxyHost/Port` 与 `systemProp.https.proxyHost/Port`；Python 下载使用 `HTTPS_PROXY`。不要提交个人代理配置。

增量开发：先运行 `python scripts/prepare_dependencies.py`，再依次在扩展目录和根目录运行各自 wrapper 的 `build`。安装至原 Prism 布局是单独操作：退出游戏后运行 `./build.ps1 -Install`，要求仓库相邻存在 `minecraft/mods`；也可手动复制两个 JAR。

**验证边界：**自动测试不启动 Minecraft。1.2 普通首发伊布已实机验证完整一回合后触发、扣血、消耗和不重复触发。双打、换入取消等有调度层覆盖，不代表所有多人场景均已实机验收。在命令开启的测试世界，权限 2 的 `/poketoiletdebug battle` 与 8 格内最近的空闲野生宝可梦开战；`/poketoiletdebug health` 输出队伍与战斗血量。战报的计划伤害不是结算回执，应结合 HP 和日志检查。

### CI 与发布

[Build and Release](.github/workflows/ci.yml) 对分支推送、PR、手动触发分别运行 Ubuntu 和 Windows 全量构建，保存各自的 Actions artifacts。PR 只读、不发布。

1. 修改唯一版本源 [`VERSION`](VERSION)，更新 README 示例，添加 `releases/<版本>.md` 中英双语发行说明。若本地 `dist/` 留有其他版本产物，先移走该目录；打包会拒绝混入旧文件。
2. 运行 `python scripts/build.py --tag v1.2`（替换为目标版本），将工作流变更提交并合并到默认分支 `main`，确认 CI 通过。
3. 在目标提交上创建标签，例如 `git tag -a v1.2 -m "Poketoilet 1.2"`，再执行 `git push origin v1.2`。

只有推送 `v*` 标签才会发布，标签必须等于 `v` 加 `VERSION`。两平台通过后使用本次 Ubuntu 构建产物，校验远端标签提交，上传两个 JAR 和校验文件到草稿 Release，下载复验后公开。版本含 `-` 后缀时标记为预发布。

失败可在 Actions 重跑：草稿续传；已公开且文件相同则成功退出，不同则停止供维护者检查。不要移动已发布标签，修正应使用新版本。仓库需启用 Actions 并允许发布任务 `contents: write`，无需额外 PAT，但组织策略可能限制权限。手动触发只验证，不发布。

首次发布前应先合并工作流：若目标提交相对默认分支新增或修改工作流，GitHub 的 Release API 可能要求内置 `GITHUB_TOKEN` 无法获得的工作流写权限。见 [GitHub 创建 Release 的权限说明](https://docs.github.com/en/rest/releases/releases#create-a-release)。

### 源码与许可

玩法在 `src/main/java/com/poketoilet/`；Mixin、扩展事件和 Showdown 桥接见 [`cobblemon-ext/`](cobblemon-ext/README.md)。`docs/` 是历史诊断笔记，不代表当前版本验收结论。

项目元数据声明 **All rights reserved**，本次不更改许可。PoopSky、Cobblemon 及资源属于各自作者，遵循各自许可，请从上游获取依赖。本项目不隶属于 Mojang、The Pokémon Company 或 Cobblemon 团队。

## English

Poketoilet lets Pokémon use PoopSky toilets and flush toilets, and adds Cobblemon battle effects for PoopSky held items. Originally developed for the “Sky Pokémon Toilet” modpack, it now provides a standalone build and release process. Releases do not include the modpack or dependencies.

### Installation and compatibility

Download matching **poketoilet-1.2.jar** and **cobblemon-ext-1.2.jar** from [Releases](https://github.com/drunkenQCat/poketoilet/releases). Put both in `mods` on client and server. Stop the game before replacing old JARs, and retain only one version of each mod.

| Component | Declared requirement | Validation baseline for 1.2 |
| --- | --- | --- |
| Java | Java 21 required by Minecraft / Cobblemon | Java 21 |
| Minecraft | `[1.21.1,1.22)` | **1.21.1** |
| NeoForge | `21.1.240+` | **21.1.240** |
| [PoopSky](https://github.com/Altnoir/PoopSkyMod/releases) | `2.1.3+` | **2.2+NeoForge1.21.1-Hotfix2** |
| [Cobblemon](https://modrinth.com/mod/cobblemon) | `1.7.0+` | **1.7.3 NeoForge** |
| [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) | Cobblemon 1.7.3 runtime requirement: `5.3+` | **5.12.0** |
| cobblemon-ext | `1.2+` when installed | **1.2** |

**Accepted version ranges are not a tested compatibility matrix.** Cobblemon internal Mixins and the Showdown bridge require retesting after dependency upgrades. Fabric, Forge and other Minecraft versions are unverified. The extension is optional in addon metadata, but required for held-item battle effects; this release is validated as a two-JAR installation.

### Gameplay

- **Seat Pokémon:** leash a Pokémon and right-click a PoopSky toilet. The interaction checks the leash, not your current item. Open flush-toilet lids first. Ordinary toilets invoke PoopSky production every 40 ticks (about two seconds at 20 TPS); flush toilets use native PoopSky seats, production and flushing.
- **Seat players:** right-click an ordinary toilet with an empty hand. Existing PoopSky crouching and flush-toilet interactions retain their original behavior.
- **On the Verge:** a Pokémon with this effect triggers PoopSky's explosion and recipe conversion when seated, including when the effect is applied after seating. Without a matching recipe, the toilet breaks and drops itself. Radius scales with collision-box equivalent side length, capped at 18. This changes world blocks.
- **Scale Scanner:** right-click a Pokémon with `poketoilet:scale_scanner` to inspect volume, equivalent side length and scale references. Craft with glass, an iron ingot and a stick in a vertical column, top to bottom. Also available under Tools & Utilities in creative mode.

**Equip held items:** hold the item in your main hand, sneak-right-click your Pokémon and choose “Held Item,” not “Cosmetic Item.” The addon appends held-item tags without replacing existing entries.

| Item | Current behavior |
| --- | --- |
| Folium Sennae `poopsky:folium_sennae` | Lowers the move instruction's resolved target's Speed by one stage, down to -6. Not consumed. It operates on the move target, not unconditionally on every opponent; instructions without a resolved target do nothing. |
| King of Dragon Fruit `poopsky:king_of_dragon_fruit` | A Fire-type at the configured level cap activates after its entry animation and battle readiness. Other holders must remain on the field for a complete turn. Leads normally activate at turn two; mid-turn switch-ins wait through the following complete turn. Consumed on activation. |

Switching out, fainting, losing the fruit or ending the battle cancels a pending activation. Re-entry starts a new wait. Switching out and ending the battle do not themselves consume the fruit. Effects appear in the battle log and are processed through Showdown.

Requested self-damage is `max(1, round(max HP × 1%))`. Requested damage to each living active opponent is:

```text
r = attacker's collision-box volume / target's collision-box volume
f(r) = r                     if r ≤ 1
       1 + ln(r) / ln(500)    if r > 1
Damage = max(1, round(target max HP × 25% × max(0, f(r) + (attacker level - target level) / 100)))
```

Both sides **retain at least 1 HP**, so actual damage can be lower. Equal volume and level request roughly 25% of target max HP; 500 times the volume at equal level requests roughly 50%, subject to integer rounding. Volumes have a 0.1 floor and missing-entity fallbacks. Use the scanner rather than visual size alone.

### Building and validation

Install **JDK 21** (`JAVA_HOME`), **Node.js 22** and **Python 3.11+** (CI uses 3.12), with `java`, `node` and `python` available. From the repository root:

```sh
python scripts/build.py
```

Windows PowerShell users can also run `./build.ps1`. By default, this only builds, tests and packages; it does not install into Minecraft or publish to GitHub.

The script downloads fixed URLs and verifies hashes from [`scripts/dependencies.json`](scripts/dependencies.json), extracts bundled Registrate and Showdown into ignored `.deps/`, then builds the extension before the addon. No PrismLauncher, private `libs/`, save files or separately installed Gradle is needed. Dependencies are not bundled into release JARs.

The full build runs release-script failure-path tests, 19 Java scheduling checks, 11 real Showdown bridge checks and a headless battle test. Packaging checks versions, metadata, Mixin classes, the extension patch and absence of bundled dependencies. `dist/` contains two JARs and `SHA256SUMS.txt`.

On Linux, verify with `cd dist && sha256sum -c SHA256SUMS.txt`. On Windows, run `Get-FileHash dist/*.jar -Algorithm SHA256` and compare the corresponding entries.

Network access is needed for Gradle, Maven Central, NeoForge Maven, GitHub and the Modrinth CDN. No proxy is hardcoded. If necessary, configure `systemProp.http.proxyHost/Port` and `systemProp.https.proxyHost/Port` in your own `~/.gradle/gradle.properties`; Python downloads honor `HTTPS_PROXY`. Do not commit personal proxy settings.

For incremental builds, first run `python scripts/prepare_dependencies.py`, then each Gradle wrapper's `build`, extension first. Installation into the original Prism layout is separate: stop the game and run `./build.ps1 -Install`, which requires a sibling `minecraft/mods` directory, or copy both JARs manually.

**Validation limits:** automated tests do not launch Minecraft. In-game testing of a lead Eevee confirmed activation after a complete turn, HP changes, consumption and no repeat activation. Doubles and switch cancellation have scheduling-level coverage, not exhaustive multiplayer gameplay acceptance. In a test world with commands enabled, permission-level-2 `/poketoiletdebug battle` starts a battle with the nearest idle wild Pokémon within eight blocks; `/poketoiletdebug health` reports party and battle HP. Planned damage in the log is not an engine receipt; inspect HP and logs together.

### CI and releases

[Build and Release](.github/workflows/ci.yml) runs full Ubuntu and Windows builds on branch pushes, PRs and manual dispatches, retaining separate Actions artifacts. PRs run read-only and never publish.

1. Update the single version source [`VERSION`](VERSION), refresh README examples and add bilingual `releases/<version>.md` notes. Move aside a local `dist/` containing another version; packaging rejects stale files.
2. Run `python scripts/build.py --tag v1.2` (substitute your version), commit and merge workflow changes into the default `main` branch, and confirm CI passes.
3. Tag the intended commit, for example `git tag -a v1.2 -m "Poketoilet 1.2"`, then `git push origin v1.2`.

Only `v*` tag pushes publish, and the tag must equal `v` plus `VERSION`. After both platforms pass, the workflow uses that run's Ubuntu artifacts, verifies the remote tag's commit, uploads both JARs and checksums to a draft, downloads them for verification, then publishes. Versions with a `-` suffix become prereleases.

Retry failures in Actions: unfinished drafts resume; identical published assets are left unchanged, while differing assets stop for maintainer review. Never move a published tag; use a new version for corrections. Enable Actions and allow the release job `contents: write`; no extra PAT is needed, though organization policy can restrict permissions. Manual dispatch validates without publishing.

Merge workflow changes before the first release: if the target commit adds or changes workflows relative to the default branch, GitHub's Release API may require workflow-write permission unavailable to the built-in `GITHUB_TOKEN`. See [GitHub's release creation permission notes](https://docs.github.com/en/rest/releases/releases#create-a-release).

### Source and licensing

Gameplay lives in `src/main/java/com/poketoilet/`; see [`cobblemon-ext/`](cobblemon-ext/README.md) for Mixins, extension events and the Showdown bridge. Files under `docs/` are historical diagnostics, not current acceptance results.

Project metadata declares **All rights reserved**; this update does not change the license. PoopSky, Cobblemon and their assets belong to their authors under their respective licenses. Obtain dependencies upstream. This project is not affiliated with Mojang, The Pokémon Company or the Cobblemon team.
