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
| King of Dragon Fruit | Activates after a full turn on the field; once per battle, the item stays held. A Fire-type at Cobblemon's configured level cap activates after its entry animation instead. |

Dragon Fruit damages the holder and each active opponent, leaving everyone at least **1 HP**. At equal size and level, it deals about 25% of each opponent's maximum HP and 1% of the holder's. Larger or higher-level holders deal more damage. Switching out, fainting, losing the fruit or ending the battle cancels the wait; re-entry starts it again.

## Build

Install **JDK 21**, **Node.js 22** and **Python 3.11+**, set `JAVA_HOME`, then run:

```sh
git clone --recurse-submodules https://github.com/drunkenQCat/Poopy-Cobblemon.git
cd Poopy-Cobblemon
python scripts/build.py
```

For an existing checkout, run `git submodule update --init --recursive` after pulling. [Cobblemon Ext](https://github.com/drunkenQCat/cobblemon-ext) has its own repository, version, tests and release workflow. This repository pins its tested commit as a Git submodule.

Gradle resolves Cobblemon and PoopSky from Modrinth Maven using the fixed version IDs in [gradle.properties](gradle.properties), and verifies dependencies against [committed checksums](gradle/verification-metadata.xml). It builds the Ext submodule through `includeBuild`, runs both projects' regression checks, and the script writes two JARs plus `SHA256SUMS.txt` to `dist/`. No modpack installation is needed. For incremental builds, `./gradlew build` (`./gradlew.bat build` on Windows) resolves dependencies and runs all checks directly.

PoopSky is pinned to Modrinth release `CEa86OFf` (2.2 Hotfix2), which is identical to CurseForge file 8757814. Its bytes differ from the previously used GitHub release; the Maven artifact is the build reference. Registrate is extracted from this verified PoopSky JAR for compilation.

On Windows, `./build.ps1` runs the same build. `./build.ps1 -Install` also copies the JARs into a sibling `minecraft/mods` directory; close Minecraft first.

When updating a Maven dependency, change its version ID, regenerate `gradle/verification-metadata.xml` with `./gradlew --write-verification-metadata sha256 build`, and review the new checksums against the publisher before committing. CI only verifies committed checksums. When Ext dependencies change, update verification metadata in both repositories; the parent file governs composite builds.

## Release

[GitHub Actions](.github/workflows/ci.yml) builds, tests and previews publishing without credentials on Ubuntu and Windows, then compares SHA-256 hashes of both packages. A version tag publishes to GitHub and then CurseForge only after both builds pass and the artifacts match. Branch pushes, pull requests and manual runs only validate.

1. Update [`VERSION`](VERSION) and add English notes in `releases/<version>.md`; keep Chinese notes in a separate `<version>.zh-CN.md` file.
2. Run `python scripts/build.py --tag v1.2` with the intended version, commit to `main`, and wait for CI to pass.
3. Create and push the matching tag: `git tag -a v1.2 -m "Poopy Cobblemon 1.2"`, then `git push origin v1.2`.

`VERSION` controls the addon; `cobblemon-ext/VERSION` controls the extension. To update the extension, push its commit first, then commit the new submodule reference here. Releases bundle both mods using their respective versions.

The release job checks the tag, uploads a draft, verifies downloaded assets, then publishes it. Failed drafts can be retried; published files cannot be replaced with different content. Use a new version for fixes.

The CurseForge project ID is **1693823**; upload settings live in [publishing/curseforge.json](publishing/curseforge.json). Add `CURSEFORGE_TOKEN` under this repository's **Settings → Secrets and variables → Actions**, using a CurseForge API token with upload permission for this project. Configure each repository separately. GitHub releases use the automatically supplied Actions `GITHUB_TOKEN`; no extra personal token is needed.

This repository uploads only `poopy-cobblemon-<version>.jar` to CurseForge. Cobblemon, PoopSky and Kotlin for Forge are required; Cobblemon Ext is optional (required for the held-item battle effects). Ext publishes from its own repository to project **1693831**. GitHub releases still bundle both JARs. When updating Ext, release it before the main mod.

After building, inspect the proposed file, version, dependencies and changelog:

```sh
./gradlew -p publishing curseforgePreview -PreleaseTag=v1.2
```

Use `./gradlew.bat` on Windows. The preview never reads a token or calls the CurseForge API; it does not validate remote project permissions, dependency slugs or moderation status. Gradle may still download plugin dependencies on the first run. The real `curseforge` task requires an explicit `-PreleaseTag` matching `VERSION`; CI runs it against the downloaded and reverified build artifact.

A successful CurseForge upload may still await moderation. If a connection fails during upload, inspect the project's file list before retrying to avoid duplicates. Choose **Re-run failed jobs** in Actions so successful publishing jobs are not repeated. A CurseForge failure does not retract an already published GitHub release.

[Dependabot](.github/dependabot.yml) checks Gradle dependencies and GitHub Actions weekly; updates require review and passing CI. The publishing plugin is pinned to Java 21-compatible 1.1.28. To update it, run `./gradlew -p publishing --write-verification-metadata sha256 curseforgePreview`, verify the new checksums and commit the separate `publishing/gradle/verification-metadata.xml`. Do not accept unverified checksums just to clear a failed build.

Automated tests cover scheduling and the Showdown bridge. An in-game Eevee test confirmed the ordinary holder's turn-two activation and item consumption; multiplayer coverage is limited.

## License

Code is licensed under [MIT](LICENSE). Original non-code assets are licensed under [CC BY-NC 4.0](LICENSE-ASSETS.md). The Showdown JavaScript patch is code and uses MIT.

See [cobblemon-ext](https://github.com/drunkenQCat/cobblemon-ext#readme) for the extension API. Third-party dependencies retain their own licenses. This project is not affiliated with Mojang, The Pokémon Company or the Cobblemon team.
