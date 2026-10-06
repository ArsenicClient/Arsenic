# Arsenic addons

Addons are modules written as plain `.java` files. Drop them in `.minecraft/Arsenic/addons/` and they are compiled
and loaded when the client starts. No JDK, no jar, no obfuscation step.

## Quick start

In game:

```
.addon new MyModule combat     # creates Arsenic/addons/MyModule.java from a template
.addon reload                  # compile + load everything in the folder
.addon list | .addon folder
```

An addon is a normal module: `@ModuleInfo`, public `Property` fields, `@EventLink` listeners, `onEnable/onDisable`.
Write Minecraft code with the usual MCP names (`mc.thePlayer`, `mc.theWorld.playerEntities`, ...).

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

Rules: public class named like the file, public no-arg constructor, a unique module name. Several files may refer
to each other. A file with compile errors is skipped and the errors are printed in chat and in the log; the rest still load.
Settings and enabled state are saved in configs like any other module, and survive `.addon reload`.

## How the obfuscation problem is solved

Inside the real game Minecraft uses SRG names (`func_71410_a`, `field_71439_g`), not the names you type.

1. The Eclipse compiler (embedded, so a plain JRE works) is shown Minecraft with MCP names: the class files are
   read through FML's own deobfuscation and their member declarations are renamed with `addon-mappings.txt`.
2. The compiled addon is then renamed back to SRG with an owner-aware ASM pass (inherited members, overrides in
   addon subclasses, protected fields and so on are resolved through the class hierarchy).
3. In a dev environment (names already MCP) both passes are skipped.

`addon-mappings.txt` is generated from ForgeGradle's `mcp-srg.srg` by the `generateAddonMappings` task and packaged
into the jar, so it always matches the mappings the client was built with.

Addons run with the full permissions of the game, so only load addons you trust.

## HUD elements

A module puts itself on the HUD by registering an element; the HUD editor lists every element of every module, so
nothing in the client has to know about your module.

```java
private final HudElement hud = hudElement("My Panel", 4, 100, 120, 40);   // label, default x, y, width, height

@EventLink
public final Listener<EventRender2D> onRender = event -> {
    hud.setSize(120, 12 + rows * 13);          // keep the editor box in sync with what you draw
    DrawUtils.drawRoundedRect(hud.x, hud.y, hud.x + hud.width, hud.y + hud.height, 5, 0x96121212);
};
```

`hud.x` / `hud.y` are the saved, draggable position. Pass `true` as a last argument for an element anchored to the
right screen edge (`x` is then an offset from the right). Positions are saved in the module's config entry.

## Addon packs

A pack is a folder (or a zip of that folder) with a `pack.json` and an `impl` folder holding the pack's addons:

```
pit/
  pack.json
  impl/
    AutoHunt.java
    FightBot.java
```

```json
{
  "id": "pit",
  "name": "Pit",
  "description": "Tools for The Pit",
  "icon": "textures/items/gold_sword.png",
  "autoInstall": false,
  "addons": { "AutoHunt": "What AutoHunt does, shown in the Addon Manager" }
}
```

`icon` is any texture from the game's resources (an item texture by default). `autoInstall` packs are installed and
enabled the first time the client runs. Installed packs live in `Arsenic/addons/packs/<id>/`; drop a pack `.zip`
into `Arsenic/addons/packs/` and it is unpacked into a folder of the same name on the next reload (the zip is
renamed to `.zip.imported`). An addon file ending in `.java.disabled` is not loaded.

## Default addons and the Addon Manager

Several modules ship as addons instead of being compiled into the client. Their sources live in `src/addons`
(IntelliJ treats the folders as source and Gradle compiles them as a check) and are bundled into the jar:

- `src/addons/java` and `src/addons/addons.json`: loose default addons and their descriptions.
- `src/addons/packs/<id>`: the bundled packs (`pack.json` plus `impl`). List a new pack's id in `addons.json`.

On startup each default addon or pack that was never seen before is copied into the addons folder: autoInstall
packs enabled, everything else as `.java.disabled` files. Anything you later delete or rename is left alone.

Click **Addon Manager** in the ClickGUI's bottom right corner: the main card drops the logo and category column and
shows cards in two columns (scrolled like a module category). The **Packs** and **Addons** buttons in the header
switch between all packs and all addons. Left or right click a pack to open its page, which lists the pack's addons
(its Install / Uninstall button handles the whole pack, each addon has its own Install / Uninstall, and a Back button returns to the list). The ClickGUI's
search box filters whichever list is showing, and the bottom right button, now **ClickGUI**, goes back to modules.
Every change reloads the addons straight away.

Hooks addons can use beyond a plain module:

- `registerCommand(new MyCommand())` adds a chat command that exists while the addon is loaded (see AutoHunt's `.hunt`).
- `allowsTarget(player)` restricts who TargetManager may pick while the module is enabled.
- `isSwappingHotbar()` tells AutoWeapon to keep its hands off the hotbar.
