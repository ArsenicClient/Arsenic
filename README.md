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

Other prompts that work the same way:

> Read the ADDONS.md link and make an addon that draws boxes around chests through walls.
>
> Read the ADDONS.md link and make an addon that auto-blocks with my sword when a player is within 3 blocks.

If you want a more accurate result, also paste the link to an existing addon from
[`src/addons`](src/addons/java) (e.g. [Tracers](src/addons/java/Tracers.java)) so the AI can copy its style.

### The manual way

```
.addon new MyModule combat     # creates Arsenic/addons/MyModule.java from a template
.addon reload                  # compile + load everything in the folder
.addon list | .addon folder
```

An addon is a normal module: `@ModuleInfo`, public `Property` fields, `@EventLink` listeners, `onEnable/onDisable`,
and MCP names for Minecraft code (`mc.thePlayer`, `mc.theWorld`, ...).

```java
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.*;

@ModuleInfo(name = "Example", category = ModuleCategory.PLAYER)
public class Example extends Module {
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.thePlayer != null) mc.thePlayer.setSprinting(true);
    };
}
```

Addons can be bundled into **packs** (a folder or zip with a `pack.json`) and managed from the **Addon Manager** button
in the ClickGUI. Addons run with the game's full permissions, so only load ones you trust.

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
