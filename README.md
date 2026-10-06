# [Arsenic](https://arsenicclient.github.io/)

Arsenic is a Minecraft 1.8.9 Forge client built around the Hypixel and Grim anti-cheats. Anything that flags GrimAC
gets removed or reworked.

- Module system with a themed ClickGUI and draggable HUD
- Command system (`.help` in chat), config system, custom event bus, mixins
- **Addons**: write a module as a single `.java` file, drop it in a folder and it loads. No IDE, JDK or jar needed.

## Addons (make your own with AI)

Addons are plain `.java` modules in `.minecraft/Arsenic/addons/`. They are compiled when the client starts, or when
you run `.addon reload` in chat. Full reference: [ADDONS.md](ADDONS.md).

### The fast way: let an AI write it

Give any AI (Claude, ChatGPT, ...) the link to [ADDONS.md](https://raw.githubusercontent.com/ArsenicClient/Arsenic/main/ADDONS.md)
and describe what you want. That is the whole prompt:

> Read https://raw.githubusercontent.com/ArsenicClient/Arsenic/main/ADDONS.md and write me an AutoWaterBucket clutch
> addon. Give me the complete `.java` file.

Then:

1. Save the file as `AutoWaterBucketClutch.java` (the name of the public class) in `.minecraft/Arsenic/addons/`.
2. Run `.addon reload` in game. Compile errors are printed in chat; paste them back to the AI to fix them.
3. Enable the module in the ClickGUI.

## Building and editing the client

1. Clone the repository.
2. Run `setup.bat` (`setup.command` on macOS/Linux).
3. Open the project in your IDE and load the Gradle project.
4. Start developing.

Helpful docs:

- [ADDONS.md](ADDONS.md): addon system, HUD elements, packs, the Addon Manager
- [UTILITIES.md](UTILITIES.md): shared helper classes (timers, maths, colours, rendering, fonts). Check it before writing a helper.

Default addons live in [`src/addons`](src/addons): loose ones in `java/`, packs in `packs/<id>/`. They are written
exactly like user addons.

## Credits

Thanks to everyone who has contributed:

- **Kv**
- **Lily**
- **Stephen**
- **Cosmic**

Reach out to `@kv.dev` on Discord if you need help with the setup.
