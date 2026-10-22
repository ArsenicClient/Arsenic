# [Arsenic](https://arsenicclient.github.io/)

Arsenic is a Minecraft client built around the Hypixel and Prediction anti-cheats. Anything that flags gets removed from the base client.
The philosophy of this client is to make the experience for the user as simple as possible.
Because of this we have tried to minimise the amount of settings for each module, making sure they 'just work' and the user should not need to spend time configuring them.
We have an extremely open addon system, such that if you want more customisation, you can easily add more modules.

This branch (`modern`) is the Fabric mod for Minecraft 26.3. The 1.8.9 client, loaded with its own injector, lives on `main`.

| | |
|---|---|
| Minecraft | 26.3 |
| Mod loader | Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 |
| Java | 25 |

## Installing

Install Fabric Loader for 26.3, then put the Arsenic jar and Fabric API into the `mods` folder of that install.

There is one jar per tier: `Arsenic-<version>.jar` (Blatant), and `-qol`, `-visual`, `-legit` and `-dev` jars that load
only the modules of those tiers. Use one at a time.

Configs, addons, themes and everything else Arsenic saves live in a folder in your home folder: `~/Arsenic-26`
(`C:\Users\<name>\Arsenic-26` on Windows) for the main jar, `Arsenic-26-QOL`, `-Visual`, `-Legit`, `-Dev` for the
others. They are kept apart from the 1.8.9 client's `Arsenic` folders, because configs and addons do not carry over
between the two. An `Arsenic` folder left in the game folder by an older version is copied there the first time.

## Addons (make your own with AI)

Addons are plain `.java` modules in `~/Arsenic-26/addons/`. They are compiled when the client starts, or when you run
`.addon reload` in chat. Full reference: [ADDONS.md](ADDONS.md).

### The fast way: let an AI write it

Give any AI (Claude, ChatGPT, ...) the link to [ADDONS.md](https://raw.githubusercontent.com/ArsenicClient/Arsenic/modern/ADDONS.md)
and describe what you want. That is the whole prompt:

> Download and read the whole of https://raw.githubusercontent.com/ArsenicClient/Arsenic/modern/ADDONS.md
> Write me an AutoWaterBucket clutch addon for Minecraft 26.3. Give me the complete `.java` file.

Then:

1. Save the file as `AutoWaterBucketClutch.java` (the name of the public class) in `~/Arsenic-26/addons/`.
2. Run `.addon reload` in game. Compile errors are printed in chat; paste them back to the AI to fix them.
3. Enable the module in the ClickGUI.

Addons written for the 1.8.9 client do not compile here: Minecraft's names and APIs are different.

## Building and editing the client

-   Install JDK 25 (e.g. Eclipse Temurin). `setup.bat` finds it even if `JAVA_HOME` points at an older JDK (like Java 8 for 1.8.9 work); in IntelliJ, set the Gradle JVM to JDK 25.
-   Clone the repository and check out the `modern` branch.
-   Run `.\setup.bat` (or double-click it). It downloads Minecraft and the Fabric toolchain, decompiles Minecraft so your IDE can show its sources, and does a first build. On macOS/Linux, run `./gradlew genSources` and then `./gradlew build` instead.
-   Open the project in your IDE and load the Gradle project.

To launch the game from the project, use the "Minecraft Client" run configuration or `gradlew runClient`.

`gradlew build` compiles the client, checks that every bundled addon compiles, regenerates the API section of
[ADDONS.md](ADDONS.md), and puts the tier jars in `build/libs`.

Helpful docs:

- [ADDONS.md](ADDONS.md): addon system, HUD elements, packs, the Addon Manager
- [UTILITIES.md](UTILITIES.md): shared helper classes (timers, maths, colours, rendering, fonts). Check it before writing a helper.
- [docs/GRIM_SAFE.md](docs/GRIM_SAFE.md): what keeps a module from flagging

Default addons live in [`src/addons`](src/addons): loose ones in `java/`, packs in `packs/<id>/`. They are written
exactly like user addons.

The 1.8.9 sources this branch was last brought up to date with (see `legacy/MERGE_BASE`), and the parts not ported to
26.3, are kept in `legacy/` for reference. They are not compiled.

## Credits

Thanks to everyone who has contributed:

- **Kv**
- **Lily**
- **Stephen**
- **Cosmic**

Reach out to `@kv.dev` on Discord if you need help with the setup.
