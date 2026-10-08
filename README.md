# [Arsenic](https://arsenicclient.github.io/)

Arsenic is a Minecraft 1.8.9 client built around the Hypixel and Prediction anti-cheats. Anything that flags gets removed from the base client.
The philosophy of this client is to make the experience for the user as simple as possible.
Because of this we have tried to minimise the amount of settings for each module, making sure they 'just work' and the user should not need to spend time configuring them.
We have an extremely open addon system, such that if you want more customisation, you can easily add more modules.

## Installing

Arsenic is loaded into a game that is already running (Forge, Vanilla or Lunar Client). It is no longer a mod: never put
the jar in `.minecraft/mods`. This needs a JDK installed (any of 8, 17, 21...).

Two ways in:

- **Forge/Vanilla**: Start Minecraft 1.8.9, then double-click the jar. Pick the game in the window and press Inject.
- **Lunar Client**: Double click the downloaded jar. Click "inject into lunar", this will close lunar client if its open. Reopen Lunar and play 1.8.9 optifine.
  

## Addons (make your own with AI)

Addons are plain `.java` modules in `.minecraft/Arsenic/addons/`. They are compiled when the client starts, or when
you run `.addon reload` in chat. Full reference: [ADDONS.md](ADDONS.md).

### The fast way: let an AI write it

Give any AI (Claude, ChatGPT, ...) the link to [ADDONS.md](https://raw.githubusercontent.com/ArsenicClient/Arsenic/main/ADDONS.md)
and describe what you want. That is the whole prompt:

> Download and read the whole of https://raw.githubusercontent.com/ArsenicClient/Arsenic/main/ADDONS.md 
> Write me an AutoWaterBucket clutch addon. Give me the complete `.java` file.

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
