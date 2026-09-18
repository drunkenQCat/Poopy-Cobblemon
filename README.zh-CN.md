# Poopy Cobblemon

让宝可梦使用 PoopSky 厕所，并为 PoopSky 携带物添加 Cobblemon 战斗效果。

[English](README.md) · [下载](https://github.com/drunkenQCat/Poopy-Cobblemon/releases) · [反馈](https://github.com/drunkenQCat/Poopy-Cobblemon/issues) · [CI](https://github.com/drunkenQCat/Poopy-Cobblemon/actions/workflows/ci.yml)

## 安装

适用于 **Minecraft 1.21.1 / NeoForge / Java 21**。客户端和服务端均需安装以下前置：

| 前置 | 已测试版本 |
| --- | --- |
| NeoForge | 21.1.240 |
| [Cobblemon](https://modrinth.com/mod/cobblemon) | 1.7.3 NeoForge |
| [PoopSky](https://github.com/Altnoir/PoopSkyMod/releases) | 2.2+NeoForge1.21.1-Hotfix2 |
| [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) | 5.12.0 |

从 [Releases](https://github.com/drunkenQCat/Poopy-Cobblemon/releases) 下载 **poopy-cobblemon-1.2-Fix2.jar** 和 **cobblemon-ext-1.3.jar**。关闭游戏，替换 `mods` 中的旧版文件，在客户端和服务端安装这两个 JAR。前置模组需另行下载。

扩展库负责携带物的战斗效果。其他前置版本可能可以加载，但目前测试的是上表中的组合。

从旧 `poketoilet` 构建升级时，请删除旧 JAR 并替换两个模组；即使 `cobblemon-ext` 已是 1.2，也需要替换。新模组 ID 为 `poopy_cobblemon`，注册表别名会将旧扫描仪和座椅 ID 映射到新名称。

## 玩法

- **坐厕：** 拴住宝可梦，右键 PoopSky 厕所。抽水马桶要先开盖。普通厕所在 20 TPS 下每两秒排便一次；抽水马桶沿用 PoopSky 的排便与冲水机制。玩家空手右键普通厕所即可坐下。
- **一触即发：** 带有该效果的宝可梦坐厕时会触发 PoopSky 爆炸和厕所转换；坐下后获得效果也会触发。没有对应配方时，厕所会被破坏。爆炸随宝可梦体型增大，可能破坏周围方块。
- **体型扫描仪：** 右键宝可梦查看碰撞箱体积与倍率。合成方式为玻璃、铁锭、木棍从上到下竖排一列。

装备携带物时，手持物品，潜行右键自己的宝可梦，在交互轮盘中选择**携带物品**。

| 携带物 | 效果 |
| --- | --- |
| 番泻叶 | 招式有可解析目标时，使该目标速度降低一级，最低 −6。不消耗；没有可解析目标时不生效。 |
| 帝王火龙果 | 在场完整经历一回合后触发；每场战斗限发动一次。达到 Cobblemon 配置等级上限的火属性宝可梦改为入场动画结束后触发。 |

火龙果对自身和所有敌方出战宝可梦造成伤害，双方都至少保留 **1 HP**。同体型、同等级时，敌方约损失最大 HP 的 25%，自身约损失 1%。体型或等级更高时，对敌伤害增加。等待期间换下、倒下、失去果实或战斗结束都会取消等待；重新上场重新计时。

## 构建

安装 **JDK 21**、**Node.js 22** 和 **Python 3.11+**，设置 `JAVA_HOME`，然后运行：

```sh
git clone --recurse-submodules https://github.com/drunkenQCat/Poopy-Cobblemon.git
cd Poopy-Cobblemon
python scripts/build.py
```

已有工作副本在拉取后运行 `git submodule update --init --recursive`。[Cobblemon Ext](https://github.com/drunkenQCat/cobblemon-ext) 拥有独立仓库、版本、测试与发布流程；本仓库通过 Git 子模块固定使用已验证的提交。

Gradle 根据 [gradle.properties](gradle.properties) 中固定的版本 ID，从 Modrinth Maven 解析 Cobblemon 和 PoopSky，并用[已提交的哈希](gradle/verification-metadata.xml)校验依赖。Ext 子模块通过 `includeBuild` 构建，两个项目的回归检查均由 Gradle 运行；脚本在 `dist/` 生成两个 JAR 和 `SHA256SUMS.txt`。无需安装整合包。增量构建可直接运行 `./gradlew build`（Windows 使用 `./gradlew.bat build`），自动解析依赖并运行所有检查。

PoopSky 固定为 Modrinth 版本 `CEa86OFf`（2.2 Hotfix2），与 CurseForge 文件 8757814 一致。其内容与此前使用的 GitHub 发行文件有差异，现以 Maven 产物作为构建基准。编译所需的 Registrate 从已校验的 PoopSky JAR 中提取。

Windows 下也可运行 `./build.ps1`。`./build.ps1 -Install` 会额外将 JAR 复制到仓库相邻的 `minecraft/mods` 目录；请先关闭游戏。

更新 Maven 依赖时，修改版本 ID，运行 `./gradlew --write-verification-metadata sha256 build` 生成校验文件，并对照发布方核实新增哈希后提交。CI 只验证已提交的哈希。Ext 的依赖变化时，两仓库都需要更新校验文件；组合构建使用父仓库的校验文件。

## 发布

[GitHub Actions](.github/workflows/ci.yml) 在推送、PR 和手动触发时运行 Ubuntu、Windows 构建、测试和免凭据发布预览，并比较两平台产物的 SHA-256。只有构建通过且产物一致，版本标签才会触发 GitHub Release，随后上传 CurseForge；普通分支、PR 和手动运行只验证。

1. 更新 [`VERSION`](VERSION)，将英文发行说明写入 `releases/<版本>.md`，中文另存为 `<版本>.zh-CN.md`。
2. 用目标版本运行 `python scripts/build.py --tag v1.2`，提交到 `main`，等待 CI 通过。
3. 创建并推送对应标签：`git tag -a v1.2 -m "Poopy Cobblemon 1.2"`，再执行 `git push origin v1.2`。

`VERSION` 管理主模组版本，`cobblemon-ext/VERSION` 管理扩展库版本。更新扩展时，先推送扩展仓库的提交，再在本仓库提交新的子模块引用。发布包按各自版本收录两个模组。

发布任务核对标签，上传草稿，下载复验产物后再公开。失败的草稿可以重跑；已经公开的文件不能换成不同内容，修正请使用新版本。

CurseForge 项目 ID 为 **1693823**，发布设置保存在 [publishing/curseforge.json](publishing/curseforge.json)。在本仓库的 **Settings → Secrets and variables → Actions** 添加 `CURSEFORGE_TOKEN`，值为具有此项目上传权限的 CurseForge API token。两个仓库分别设置；GitHub Release 使用 Actions 自动提供的 `GITHUB_TOKEN`，无需额外个人 token。

本仓库只向 CurseForge 上传 `poopy-cobblemon-<版本>.jar`，声明 Cobblemon、PoopSky、Kotlin for Forge 为必需依赖，Cobblemon Ext 为可选依赖（携带物战斗效果需要它）。Ext 由自己的仓库发布到项目 **1693831**。GitHub Release 仍收录两个 JAR。更新 Ext 时，建议先发布 Ext，再发布主模组。

构建后可单独检查待上传文件、版本、依赖和发行说明：

```sh
./gradlew -p publishing curseforgePreview -PreleaseTag=v1.2
```

Windows 使用 `./gradlew.bat`。预览不会读取 token，也不会调用 CurseForge API；它不验证远端项目权限、依赖 slug 或审核状态。Gradle 首次运行仍需下载插件依赖。正式上传任务为 `curseforge`，必须显式传入与 `VERSION` 一致的 `-PreleaseTag`，CI 会下载并复验已经构建的 JAR 后执行它。

CurseForge 上传成功后可能仍需审核。如果上传时网络中断，先检查项目文件列表再重试，以免重复上传；在 Actions 中选择 **Re-run failed jobs**，避免重新执行已成功的发布任务。上传失败不会撤回已经成功发布的 GitHub Release。

[Dependabot](.github/dependabot.yml) 每周检查 Gradle 依赖和 GitHub Actions；更新需要人工审查和 CI 通过。发布插件固定在兼容 Java 21 的 1.1.28。升级它时运行 `./gradlew -p publishing --write-verification-metadata sha256 curseforgePreview`，核实并提交独立的 `publishing/gradle/verification-metadata.xml`。不要仅为消除校验失败而接受未核实的哈希。

自动测试覆盖回合调度和 Showdown 桥接。普通首发伊布已在游戏中验证第二回合触发；多人场景的验证仍有限。

## 许可

代码使用 [MIT](LICENSE) 许可；原创非代码资源使用 [CC BY-NC 4.0](LICENSE-ASSETS.md)。Showdown JavaScript 补丁属于代码，使用 MIT。

扩展接口见 [cobblemon-ext](https://github.com/drunkenQCat/cobblemon-ext/blob/main/README.zh-CN.md)。第三方依赖遵循各自许可。本项目与 Mojang、The Pokémon Company 和 Cobblemon 团队无隶属关系。
