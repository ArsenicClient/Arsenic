# [Arsenic](https://arsenicclient.github.io/)

[Arsenic](https://arsenicclient.github.io/) is a Minecraft cheat that specifically focuses on the Hypixel and Grim anti-cheat systems. Any features that trigger GrimAC should be removed or refactored.

This branch (`modern`) is the Fabric port for Minecraft 26.3. The original 1.8.9 Forge client lives on `main`.

| | |
|---|---|
| Minecraft | 26.3 |
| Mod loader | Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 |
| Java | 25 |

The client features:
- Module System
- Command System
- Custom Events
- Mixins
- Config System
- Custom GUI with Themes

## How To Build/Edit

-   Install JDK 25 (e.g. Eclipse Temurin) and make sure `java` is on your PATH or `JAVA_HOME` is set.
-   Clone the repository and check out the `modern` branch.
-   Run `setup.bat`. It downloads Minecraft and the Fabric toolchain, decompiles Minecraft so your IDE can show its sources, and does a first build. On macOS/Linux, run `./gradlew genSources` and then `./gradlew build` instead.
-   Open the project in your IDE.
-   Load the Gradle project.
-   Start developing!

To launch the game from the project, use the "Minecraft Client" run configuration or `gradlew runClient`.

`gradlew build` puts two jars in `build/libs`: the normal one, and a `-dev` jar that also unlocks the in-development modules. To play with it, drop either jar into the `mods` folder of a Fabric 26.3 install alongside Fabric API.

The 1.8.9 sources, and the parts not yet ported (the Forge loading screen, vanilla screen theming, chams, the old shader effects), are kept in `legacy/` for reference. They are not compiled.

## Credits

A special thanks to everyone who has contributed to the project!

-   **Kv:** 
-   **Lily:** 
-   **Stephen:** 
-   **Cosmic:**

Reach out to `@kv.dev` on Discord if you need help with the setup.
