# Poketoilet

让宝可梦使用 PoopSky 厕所，并为 PoopSky 携带物添加 Cobblemon 战斗效果。

[English](README.md) · [下载](https://github.com/drunkenQCat/poketoilet/releases) · [反馈](https://github.com/drunkenQCat/poketoilet/issues) · [CI](https://github.com/drunkenQCat/poketoilet/actions/workflows/ci.yml)

## 安装

适用于 **Minecraft 1.21.1 / NeoForge / Java 21**。客户端和服务端均需安装以下前置：

| 前置 | 已测试版本 |
| --- | --- |
| NeoForge | 21.1.240 |
| [Cobblemon](https://modrinth.com/mod/cobblemon) | 1.7.3 NeoForge |
| [PoopSky](https://github.com/Altnoir/PoopSkyMod/releases) | 2.2+NeoForge1.21.1-Hotfix2 |
| [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) | 5.12.0 |

从 [Releases](https://github.com/drunkenQCat/poketoilet/releases) 下载 **poketoilet-1.2.jar** 和 **cobblemon-ext-1.2.jar**。关闭游戏，替换 `mods` 中的旧版文件，在客户端和服务端安装这两个 JAR。前置模组需另行下载。

扩展库负责携带物的战斗效果。其他前置版本可能可以加载，但目前测试的是上表中的组合。

## 玩法

- **坐厕：**拴住宝可梦，右键 PoopSky 厕所。抽水马桶要先开盖。普通厕所在 20 TPS 下每两秒排便一次；抽水马桶沿用 PoopSky 的排便与冲水机制。玩家空手右键普通厕所即可坐下。
- **一触即发：**带有该效果的宝可梦坐厕时会触发 PoopSky 爆炸和厕所转换；坐下后获得效果也会触发。没有对应配方时，厕所会被破坏。爆炸随宝可梦体型增大，可能破坏周围方块。
- **体型扫描仪：**右键宝可梦查看碰撞箱体积与倍率。合成方式为玻璃、铁锭、木棍从上到下竖排一列。

装备携带物时，手持物品，潜行右键自己的宝可梦，在交互轮盘中选择**携带物品**。

| 携带物 | 效果 |
| --- | --- |
| 番泻叶 | 招式有可解析目标时，使该目标速度降低一级，最低 −6。不消耗；没有可解析目标时不生效。 |
| 帝王火龙果 | 在场完整经历一回合后触发并消耗。达到 Cobblemon 配置等级上限的火属性宝可梦改为入场动画结束后触发。 |

火龙果对自身和所有敌方出战宝可梦造成伤害，双方都至少保留 **1 HP**。同体型、同等级时，敌方约损失最大 HP 的 25%，自身约损失 1%。体型或等级更高时，对敌伤害增加。等待期间换下、倒下、失去果实或战斗结束都会取消等待；重新上场重新计时。

## 构建

安装 **JDK 21**、**Node.js 22** 和 **Python 3.11+**，设置 `JAVA_HOME`，然后运行：

```sh
python scripts/build.py
```

脚本下载并校验[锁定的依赖](scripts/dependencies.json)，构建两个模组，运行调度与 Showdown 回归检查，在 `dist/` 生成两个 JAR 和 `SHA256SUMS.txt`。无需安装整合包。

Windows 下也可运行 `./build.ps1`。`./build.ps1 -Install` 会额外将 JAR 复制到仓库相邻的 `minecraft/mods` 目录；请先关闭游戏。

## 发布

[GitHub Actions](.github/workflows/ci.yml) 在推送、PR 和手动触发时运行 Ubuntu、Windows 构建与测试。推送版本标签后，两平台均通过才会发布 GitHub Release。

1. 更新 [`VERSION`](VERSION)，将英文发行说明写入 `releases/<版本>.md`，中文另存为 `<版本>.zh-CN.md`。
2. 用目标版本运行 `python scripts/build.py --tag v1.2`，提交到 `main`，等待 CI 通过。
3. 创建并推送对应标签：`git tag -a v1.2 -m "Poketoilet 1.2"`，再执行 `git push origin v1.2`。

发布任务核对标签，上传草稿，下载复验产物后再公开。失败的草稿可以重跑；已经公开的文件不能换成不同内容，修正请使用新版本。

自动测试覆盖回合调度和 Showdown 桥接。普通首发伊布已在游戏中验证第二回合触发与道具消耗；多人场景的验证仍有限。

## 许可

代码使用 [MIT](LICENSE) 许可；原创非代码资源使用 [CC BY-NC 4.0](LICENSE-ASSETS.md)。Showdown JavaScript 补丁属于代码，使用 MIT。

扩展接口见 [cobblemon-ext](cobblemon-ext/README.zh-CN.md)。第三方依赖遵循各自许可。本项目与 Mojang、The Pokémon Company 和 Cobblemon 团队无隶属关系。
