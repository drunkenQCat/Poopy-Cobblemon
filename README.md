# Poopy Cobblemon

Let your Pokémon use PoopSky toilets, and bring PoopSky held items into Cobblemon battles.

[简体中文](README.zh-CN.md) · [Downloads](https://github.com/drunkenQCat/Poopy-Cobblemon/releases) · [Issues](https://github.com/drunkenQCat/Poopy-Cobblemon/issues) · [CI](https://github.com/drunkenQCat/Poopy-Cobblemon/actions/workflows/ci.yml)

## Install

For **Minecraft 1.21.1 / NeoForge / Java 21**. Install these dependencies on both client and server:

| Dependency | Tested version |
| --- | --- |
| NeoForge | 21.1.240 |
| [Cobblemon](https://modrinth.com/mod/cobblemon) | 1.7.3 NeoForge |
| [PoopSky](https://github.com/Altnoir/PoopSkyMod/releases) | 2.2+NeoForge1.21.1-Hotfix2 |
| [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge) | 5.12.0 |

Download **poopy-cobblemon-1.2.jar** and **cobblemon-ext-1.2.jar** from [Releases](https://github.com/drunkenQCat/Poopy-Cobblemon/releases). Close the game, replace older copies in `mods`, and install both JARs on client and server. Dependencies are downloaded separately.

The extension provides the held-item battle effects. Other dependency versions may load, but the versions above are the tested combination.

Upgrading from the old `poketoilet` builds: remove the old JAR and replace both mods, including `cobblemon-ext` even if it is already version 1.2. The new mod ID is `poopy_cobblemon`; registry aliases map the old scanner and seat IDs to their new names.

## Play

- **Toilets:** leash a Pokémon and right-click a PoopSky toilet. Open flush-toilet lids first. Ordinary toilets produce every two seconds at 20 TPS; flush toilets use PoopSky's own production and flushing. Players can sit on ordinary toilets by right-clicking with an empty hand.
- **On the Verge:** seating a Pokémon with this effect triggers PoopSky's explosion and toilet conversion. It also triggers if the effect is applied while seated. Without a matching recipe, the toilet breaks. Explosion size scales with the Pokémon and can damage nearby blocks.
- **Scale Scanner:** right-click a Pokémon to inspect its collision-box volume and scale. Craft it with glass, an iron ingot and a stick in a vertical column, top to bottom.

To equip a held item, hold it, sneak-right-click your Pokémon and choose **Held Item** in the interaction wheel.

| Held item | Effect |
| --- | --- |
| Folium Sennae | Lowers the move's resolved target's Speed by one stage, down to −6. Not consumed. A move without a resolved target has no effect. |
| King of Dragon Fruit | Activates after a full turn on the field, then is consumed. A Fire-type at Cobblemon's configured level cap activates after its entry animation instead. |

Dragon Fruit damages the holder and each active opponent, leaving everyone at least **1 HP**. At equal size and level, it deals about 25% of each opponent's maximum HP and 1% of the holder's. Larger or higher-level holders deal more damage. Switching out, fainting, losing the fruit or ending the battle cancels the wait; re-entry starts it again.

## Build

Install **JDK 21**, **Node.js 22** and **Python 3.11+**, set `JAVA_HOME`, then run:

```sh
git clone --recurse-submodules https://github.com/drunkenQCat/Poopy-Cobblemon.git
cd Poopy-Cobblemon
python scripts/build.py
```

For an existing checkout, run `git submodule update --init --recursive` after pulling. [Cobblemon Ext](https://github.com/drunkenQCat/cobblemon-ext) has its own repository, version, tests and release workflow. This repository pins its tested commit as a Git submodule.

The script downloads and verifies [pinned dependencies](scripts/dependencies.json), builds both mods, runs the scheduling and Showdown regression checks, and writes two JARs plus `SHA256SUMS.txt` to `dist/`. No modpack installation is needed.

On Windows, `./build.ps1` runs the same build. `./build.ps1 -Install` also copies the JARs into a sibling `minecraft/mods` directory; close Minecraft first.

## Release

[GitHub Actions](.github/workflows/ci.yml) builds and tests on Ubuntu and Windows for pushes, pull requests and manual runs. Tag pushes publish a GitHub Release after both builds pass.

1. Update [`VERSION`](VERSION) and add English notes in `releases/<version>.md`; keep Chinese notes in a separate `<version>.zh-CN.md` file.
2. Run `python scripts/build.py --tag v1.2` with the intended version, commit to `main`, and wait for CI to pass.
3. Create and push the matching tag: `git tag -a v1.2 -m "Poopy Cobblemon 1.2"`, then `git push origin v1.2`.

`VERSION` controls the addon; `cobblemon-ext/VERSION` controls the extension. To update the extension, push its commit first, then commit the new submodule reference here. Releases bundle both mods using their respective versions.

The release job checks the tag, uploads a draft, verifies downloaded assets, then publishes it. Failed drafts can be retried; published files cannot be replaced with different content. Use a new version for fixes.

Automated tests cover scheduling and the Showdown bridge. An in-game Eevee test confirmed the ordinary holder's turn-two activation and item consumption; multiplayer coverage is limited.

## License

Code is licensed under [MIT](LICENSE). Original non-code assets are licensed under [CC BY-NC 4.0](LICENSE-ASSETS.md). The Showdown JavaScript patch is code and uses MIT.

See [cobblemon-ext](https://github.com/drunkenQCat/cobblemon-ext#readme) for the extension API. Third-party dependencies retain their own licenses. This project is not affiliated with Mojang, The Pokémon Company or the Cobblemon team.
