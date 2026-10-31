# Arsenic addons

This is the guide for the **Fabric client for Minecraft 26.3** (the `modern` branch). The 1.8.9 client on `main` has its
own ADDONS.md; addons written for one do not compile on the other.

Addons are modules written as plain `.java` files. Drop them in `~/Arsenic-26/addons/` (`C:\Users\<name>\Arsenic-26\addons` on
Windows; each release jar has its own folder, see [Platform facts](#platform-facts)) and they are compiled and loaded when the
client starts. No JDK, no jar, no build step.

Contents: [Quick start](#quick-start) · [Platform facts](#platform-facts) · [Module basics](#module-basics) ·
[Properties](#properties) · [Events](#events) · [Silent rotations](#silent-rotations) ·
[Legit actions](#legit-actions-rotate-then-act) · [Aim solver](#aim-solver) · [Hotbar](#hotbar-handling) ·
[Sneak](#sneak-to-place-on-a-bed-or-container) · [Verify](#verify-then-fall-back) · [Visuals](#visuals-on-by-default) ·
[Staying legit](#staying-legit-grim) · [26.3 notes](#minecraft-263-notes) · [Utility index](#utility-index) ·
[Cookbook](#cookbook) · [Worked example](#worked-example-bedcover) · [Pitfalls](#pitfalls) · [API reference](#api-reference-generated) ·
[How the loader works](#how-the-loader-works) · [HUD](#hud-elements) · [Packs](#addon-packs) ·
[Default addons and the Addon Manager](#default-addons-and-the-addon-manager) · [When something is missing](#when-something-is-missing)

## Quick start

In game:

```
.addon new MyModule combat     # creates Arsenic/addons/MyModule.java from a template
.addon reload                  # compile + load everything in the folder
.addon list | .addon folder
```

An addon is a normal module: `@ModuleInfo`, public `Property` fields, `@EventLink` listeners, `onEnable/onDisable`.
Write Minecraft code with the game's own names, which are Mojang's (`mc.player`, `mc.level.players()`, `mc.gameMode`, ...).

```java
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.*;

@ModuleInfo(name = "Example", category = ModuleCategory.PLAYER)
public class Example extends Module {
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.player != null) mc.player.setSprinting(true);
    };
}
```

Rules: public class named like the file, public no-arg constructor, a unique module name. Several files may refer
to each other. A file with compile errors is skipped and the errors are printed in chat and in the log; the rest still load.
Settings and enabled state are saved in configs like any other module, and survive `.addon reload`.

## Defaults every addon follows

Unless the person asking says otherwise, an addon you write:

1. **Has visuals.** A world highlight of what it targets or plans (`EventRenderWorldLast`) and a small draggable HUD panel
   (`EventRender2D` + `hudElement`), each behind a `BooleanProperty` (`Render`, `HUD`) that defaults to `true`. Skip them only
   if the user explicitly says no visuals. Templates: [Visuals](#visuals-on-by-default).
2. **Goes through the silent rotation pipeline** for anything that rotates, places, breaks, attacks or moves toward
   something, and acts only from the ray of the rotation being sent: [Legit actions](#legit-actions-rotate-then-act).
3. **Uses `MovementFix.SILENT`** (a setting defaulting to it) whenever the player can move.
4. **Is jittered and bounded:** random offsets in the aim point, a base delay plus jitter between actions, a rotation speed
   cap, timeouts per target, no action while a GUI is open, reach at most 4.5.
5. **Is honest about detection.** These choices are built to avoid the common Grim checks; they do not make an addon
   undetectable, so tell the user to test on their server and to report the exact flag name if one still appears.

Start from the [skeleton](#skeleton) or copy the [worked example](#worked-example-bedcover).

## Platform facts

- **Game:** Minecraft 26.3 on Fabric Loader, with Fabric API. Minecraft 26.x ships unobfuscated, so you write against
  **Mojang's names** (`mc.player`, `mc.level`, `mc.gameMode`, `mc.options`, `player.getMainHandItem()`) and those are
  the names the game runs with. There is no remapping step. Coming from 1.8 code: `thePlayer` is `player`,
  `theWorld` is `level`, `playerController` is `gameMode`, `gameSettings` is `options`, `EntityPlayer` is `Player`,
  `EntityLivingBase` is `LivingEntity`, `MovingObjectPosition` is `HitResult`, `EnumFacing` is `Direction`,
  `AxisAlignedBB` is `AABB`, `MathHelper` is `Mth`; see [26.3 notes](#minecraft-263-notes).
- **Java level:** the embedded Eclipse compiler is set to **Java 21** (source, target and compliance). `var`, records,
  switch expressions, text blocks and pattern matching (`if (hit instanceof EntityHitResult e)`) all work.
- **What you can import:** whatever is on the game's classpath. That is Minecraft, Fabric Loader and Fabric API, LWJGL
  (`org.lwjgl.*`), Gson, Guava, SLF4J/Log4j, JOML, the JDK, and every `arsenic.*` class. Addons run with the game's full
  permissions and there is no sandbox.
- **No mixins and no ASM in addons.** The client's mixins are applied when the game starts, long before an addon is compiled. Addons are
  limited to the public Java surface of Minecraft plus the Arsenic API. For private Minecraft state use the accessor
  interfaces in `arsenic.injection.accessor` by casting (`((IMixinLocalPlayer) mc.player).getLastReportedYaw()`,
  `((IMixinMultiPlayerGameMode) mc.gameMode).getCurBlockDamageMP()`); see the list in the [API reference](#api-reference-generated). A missing
  accessor needs a change to the client, not to the addon. A look vector from a rotation is public: `Entity.calculateViewVector(pitch, yaw)`.
- **Folder:** configs, addons and everything else live in a folder in your home folder: `Arsenic-26` for the main (Blatant)
  jar and a dev run, `Arsenic-26-QOL`, `-Visual`, `-Legit`, `-Dev` for the other jars. They are apart from the 1.8.9
  client's `Arsenic` folders.
- **Package:** the template uses the default package. A `package` line is honoured, but the class must still match its file.
- **Reload:** `.addon reload` throws the old module instances away and builds new ones. `static` state is lost; saved
  property values and the enabled flag come back from the config.
- An addon class with the same name as a class already on the classpath wins (this is how the bundled addons can also be
  compiled by the IDE without clashing).

## Module basics

```java
@ModuleInfo(name = "Example", description = "Does a thing", category = ModuleCategory.PLAYER,
            keybind = 0, enabled = false, hidden = false, tier = ModuleTier.LEGIT)
```

| Field | Default | Meaning |
|---|---|---|
| `name` | required | Unique module name; also the config key and the name used by `.toggle`, `.set`... |
| `description` | `"placeholder"` | Shown in the ClickGUI. |
| `category` | required | `COMBAT`, `MOVEMENT`, `PLAYER`, `RENDER`, `CLIENT`, `CONFIGS`, `GUI`, `SEARCH`. Use the first four; the rest are client-internal tabs. |
| `keybind` | `0` | A key code from `com.mojang.blaze3d.platform.InputConstants` (`InputConstants.KEY_R`), `0` for none. Users can rebind in the GUI or with `.bind`. |
| `enabled` | `false` | Initial state when there is no saved config. |
| `hidden` | `false` | Not listed in the HUD module list and gives no enable/disable notification. |
| `tier` | `LEGIT` | Which release jars the module ships in, and its ClickGUI section: `QOL` (anything that is not a cheat), `VISUAL` (rendering that could still be an advantage), `LEGIT` (subtle gameplay help), `BLATANT` (obvious cheating, shown when the Blatant toggle is on), `DEV` (development only). A module only loads in a jar built with its tier; see the build's `qolJar`, `visualJar`, `legitJar`, `jar` (Blatant) and `devJar` tasks. |

**Lifecycle hooks** (override in your class; both are `protected void`, no arguments, and do nothing by default):

```java
@Override protected void onEnable()  { }   // runs first; the module's listeners are subscribed right after it returns
@Override protected void onDisable() { }   // listeners are already unsubscribed when this runs
protected void postApplyConfig()     { }   // after a saved config was loaded into your properties
```

Other overridables: `public String getHudInfo()` (suffix shown after the name in the HUD list, `null` for none).

**Hooks other modules call on yours** (override, they return a safe default):

| Hook | Called by | Meaning |
|---|---|---|
| `boolean allowsTarget(Player p)` | `TargetManager`, every time a combat module picks a target | While your module is enabled, only players for which **every enabled module** returns true can be targeted. Default `true`. |
| `boolean isSwappingHotbar()` | `AutoWeapon` | Return `true` while you are changing the selected hotbar slot over several ticks so AutoWeapon does not switch away from your item. Default `false`. |

**Helpers on `Module`:** `mc` (the `Minecraft` instance), `client` (the `Arsenic` singleton), `registerCommand(Command)`,
`hudElement(...)`, `toggle()`, `setEnabled(boolean)`, `isEnabled()`, `getName()`.

**`Arsenic.getArsenic()`** gives access to `getModuleManager()` (`getModuleByClass(Class)`, `getModuleByName(String)`,
`getModules()`), `getSilentRotationManager()`, `getEventManager()`, `getCommandManager()`, `getThemeManager()`, `getLogger()`.

### `@EventLink` and `@RequiresPlayer`

```java
@RequiresPlayer                         // optional, see below
@EventLink(Priorities.HIGH)             // optional priority; default MEDIUM
public final Listener<EventTick> onTick = event -> { ... };
```

- A listener is a **field** of type `Listener<EventType>` on the module class itself. The event type is read from the
  generic argument. The field may have any visibility, but it must be initialised inline (not in a constructor that
  runs after subscription) and it must be declared in your class, **not in a superclass**.
- Listeners only receive events while the module is **enabled**.
- Priorities (`arsenic.event.bus.Priorities`, bytes): `VERY_LOW=0, LOW=1, MEDIUM=2, HIGH=3, VERY_HIGH=4`. Higher runs first.
- An exception in a listener is caught and reported to the error overlay and `.errors`; it does not crash the game and
  the other listeners still run.
- **`@RequiresPlayer`** skips the listener while the client is not in a world. Precisely: it returns early when **both**
  `mc.player` and `mc.level` are `null`. If only one of them is `null` (this happens briefly while changing
  dimension or joining a server) the listener still runs, so null-check what you touch in render code.
- **Dispatch is by exact class.** `Listener<EventPacket>` never fires; you must listen to `EventPacket.OutGoing`,
  `EventPacket.Incoming.Pre` or `EventPacket.Incoming.Post`. Likewise `EventTick` and `EventTick.Post` are separate
  events, as are `EventUpdate.Pre`/`Post`.

## Properties

A property is a `public` field on the module. `registerProperties()` finds public fields whose type extends `Property`;
a property in a private or protected field is **not shown and not saved**. Declare them `public final`.

All classes live in `arsenic.module.property.impl` (sliders in `...impl.doubleproperty` and `...impl.rangeproperty`).

| Property | Constructor | Read | Write | Saved |
|---|---|---|---|---|
| `BooleanProperty` | `(String name, Boolean value)` | `p.getValue()` → `Boolean` | `p.setValue(true)` | yes |
| `DoubleProperty` | `(String name, DoubleValue v)` or `(name, v, SliderScale)` | `p.getValue().getInput()` → `double` | `p.getValue().setInput(x)` (clamped, snapped to the increment) | yes |
| `RangeProperty` | `(String name, RangeValue v)` or `(name, v, SliderScale)` | `p.getValue().getMin()`, `.getMax()`, `.getRandomInRange()`; `p.hasInRange(x)` | `p.getValue().setMin(x)`, `.setMax(x)` | yes |
| `EnumProperty<E>` | `(String name, E defaultValue)`; options are `E.values()` | `p.getValue()` → `E` | `p.setValue(E.X)`, `p.setByName("x")` (case-insensitive), `p.nextMode()`/`prevMode()` | yes |
| `ColourProperty` | `(String name, int colour)` | `p.getValue()` → packed `int` (in `THEME` mode this is the current theme colour); `p.getColor(channel)` | `p.setValue(c)`, `p.setColor(channel, v)`, `p.setMode(cMode.CUSTOM/THEME)` | yes |
| `TextProperty` | `(String name, String value)` or `(name, value, int maxLength)` | `p.getValue()` → `String` | `p.setValue("x")` | yes |
| `FolderProperty` | `(String name, Property<?>... children)` | `p.getValue()` → the children | none | children that are saved properties |
| `ButtonProperty` | `(String label)`, `(String name, Runnable)` or `(String name, String label, Runnable)` | `p.getValue()` → its name | `p.fire()` runs the action | **no** |
| `StringProperty` | `(String text)` | `p.getValue()` | `p.setValue(..)` | **no** (display only) |

Use the `setValue` family for everything except the two slider types, whose value is a holder object, hence
`getValue().getInput()` (a `double`) versus `getValue()` (the `Boolean` / `Enum` itself). `setValueSilently(..)` skips the
`onValueUpdate()` hook and is what the GUI uses while loading.

```java
public final BooleanProperty rotate = new BooleanProperty("Rotate", true);
public final DoubleProperty  range  = new DoubleProperty("Range", new DoubleValue(1, 6, 4.5, 0.1));
public final RangeProperty   cps    = new RangeProperty("CPS", new RangeValue(1, 20, 8, 12, 1));
public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Fast);
```

**`DoubleValue(min, max, default, increment)`**, in that order. `RangeValue(minBound, maxBound, initialMin, initialMax, increment)`.
The increment is also the rounding step: `0.1` gives one decimal, `1` gives integers.

**`SliderScale`:** `LINEAR` (default) or `LOG`. `LOG` gives most of the slider's travel to the low end of the range, which
suits ranges like 1-360 where you mostly want small values. It only changes the slider, never the stored value.

### Showing a setting only when another has a value: `@ShowWhen`

```java
@ShowWhen(property = "Rotate", is = "true")                    // BooleanProperty named "Rotate"
public final DoubleProperty rotSpeed = ...;

@ShowWhen(property = "Block", is = { "Custom", "List" })       // EnumProperty: any of these
public final DoubleProperty blockId = ...;

@ShowWhen(property = "Mode", not = "Off")                      // anything but these
@ShowWhen(property = "Rotate", is = "true")                    // several on one field must all hold
public final DoubleProperty smoothing = ...;
```

- `property` is the **display name** (the first constructor argument) of another property on the same module; its field
  name works too. Names and values are matched **ignoring case**.
- Give either `is` (shown while the value is any of these) or `not` (shown while it is none of these), not both.
  A `BooleanProperty`'s values are `"true"` / `"false"`; an `EnumProperty`'s are its constants' names.
- Only `BooleanProperty` and `EnumProperty` can be depended on. Chains work: a property whose parent is hidden is hidden too.
- **Every rule is checked when the module loads.** An unknown setting or value, a rule on a non-public field, or a rule
  that depends on itself (also through other settings) is listed in the addon errors (in the log for built-in modules),
  with the names you could have used. That rule is skipped, so the setting stays visible instead of disappearing.
- It only controls whether the setting is shown in the ClickGUI. A hidden property still holds and returns its value, so
  read it only where it is relevant.
- The older `@PropertyInfo(reliesOn = "Rotate", value = "true")` still works, is checked the same way, and means
  `@ShowWhen(property = "Rotate", is = "true")`. It is deprecated; use `@ShowWhen` in new code.

### Sorting settings under the module's icons: `@PropertyGroup`

An open module shows up to three icons at its bottom right: a gear (main settings), an eye (visuals) and a gear with a plus
(advanced). A property without the annotation is a main setting, so most modules need nothing.

```java
@PropertyGroup(Group.VISUAL)                                   // arsenic.module.property.Group
public final BooleanProperty render = new BooleanProperty("Render", true);

@PropertyGroup(Group.ADVANCED)
@ShowWhen(property = "Rotate", is = "true")                    // both annotations can be used together
public final DoubleProperty rotSpeed = ...;
```

- An icon only shows when at least one of its settings is visible right now, so a module with only main settings looks
  as before.
- A `FolderProperty`'s group applies to everything inside it.

## Events

Subscribe with `@EventLink public final Listener<EventX> name = event -> { ... };`. All events are in `arsenic.event.impl`.
Only the ones marked **cancellable** extend `CancellableEvent` (`event.cancel()`, `isCancelled()`); the others are
notifications and have no effect when cancelled.

### Order inside one client tick

Derived from the client's mixins in `arsenic.injection.mixin`:

```
Minecraft.runTick (every 50 ms)
  EventGameLoop, EventRunTick                      start of the tick (identical, pick either)
  EventKey                                         per key press, only when no GUI is open
  ... LocalPlayer.tick begins
  EventTick                                        ← the usual place for per-tick logic
  EventMouse.Down / EventMouse.Up                  button edges, buttons 0-2
  EventLiving                                      onLivingUpdate; the silent rotation manager answers here:
    EventSilentRotation, then EventSilentRotation.Post
    EventMovementInput, EventMove, EventJump       during the movement this tick
  EventUpdate.Pre                                  just before the movement packet is built; rotation applied here
  ServerboundMovePlayerPacket is sent (then the tick-end packet)
  EventUpdate.Post                                 right after the movement packet
  EventTick.Post                                   end of LocalPlayer.tick
Frame-rate events, any number per tick:
  EventRender2D, EventRenderWorldLast, EventRenderThirdPerson, EventLook, EventShader
Network (not tied to the tick, see Pitfalls): EventPacket.OutGoing, EventPacket.Incoming.Pre/Post
```

**Note the order: the movement packet (which carries the rotation) is built after `EventTick`, `EventLiving` and
`EventSilentRotation` of the same tick.** An action packet sent from those events leaves *before* this tick's rotation
packet. Which event to act from is a Grim question, not just a timing one; the working patterns are in
[Legit actions](#legit-actions-rotate-then-act).

### Catalogue

| Event | Fires | Members | Cancellable |
|---|---|---|---|
| `EventTick` / `EventTick.Post` | start / end of `LocalPlayer.tick`, once per tick, only with a player | none | no |
| `EventLiving` | `aiStep` of the local player | none | no |
| `EventGameLoop`, `EventRunTick` | start of `Minecraft.runTick`, also with no world | none | no |
| `EventUpdate.Pre` / `.Post` | before / after `sendPosition`, i.e. around the movement packet | `getX/Y/Z`, `getYaw/Pitch`, `isOnGround` with setters (**Pre only**: values you set go into the packet, then everything is restored for `Post`), `isPre()`, `isPost()` | `Pre`: **yes**, no movement packet is sent at all that tick. `Post`: no effect |
| `EventSilentRotation` / `.Post` | `EventLiving`, see [Silent rotations](#silent-rotations) | yaw, pitch, speed, flags | no |
| `EventMovementInput` | after the keyboard input was read, once per tick | `getSpeed/setSpeed` (forward), `getStrafe/setStrafe`, `isJumping/setJump` | **yes**: zeroes forward and strafe (jump is untouched) |
| `EventMove` | start of `moveRelative` for the local player | `getStrafe/Forward/Friction/Yaw` with setters; the yaw the acceleration is computed with | no |
| `EventJump` | start of `jumpFromGround()` for any living entity | `getYaw/setYaw`, `getMotion/setMotion` (vertical velocity) | cancelling does **not** stop the jump; it leaves the vanilla jump to run unchanged |
| `EventLook` | whenever the crosshair ray is computed, many times per frame | `getYaw/Pitch` with setters, `hasBeenModified()`; set them to change where the crosshair target is computed from | no |
| `EventAttack` | start of `Player.attack` | `getTarget()` | no |
| `EventPacket.OutGoing` | `Connection.send`, before the packet is written | `getPacket()`, `setPacket(p)` (replace it) | **yes**: the packet is dropped |
| `EventPacket.Incoming.Pre` | a packet arrived, **before** the game processes it | same | **yes**: dropped |
| `EventPacket.Incoming.Post` | after the game processed it | same | no |
| `EventKey` | key pressed with no GUI open | `getKeycode()` | no |
| `EventMouse.Down` / `.Up` | mouse button edge, in `onUpdate` | public `button` field (0 left, 1 right, 2 middle) | no |
| `EventDisplayGuiScreen` | **after** `setScreen` finished | `getGuiScreen()` | no effect (it is posted afterwards) |
| `EventPlayerJoinWorld` | a player entity was added to the world | `getEntity()`, `getWorld()` | no |
| `EventRender2D` | HUD pass, once per frame | `getGraphics()` (the GUI draw target), `getWidth()`/`getHeight()` (scaled screen size), `getSr()`, `getPartialTicks()` | no |
| `EventRenderWorldLast` | after the world was rendered, once per frame; draw with the gizmo helpers in `RenderUtils` | public `partialTicks` | no |
| `EventRenderThirdPerson` | when the player model is drawn | yaw/pitch/prevYaw/prevPitch with setters, `setAccepted(true)` to apply them | no |
| `EventShader.Blur` | the frosted-glass pass behind HUD elements, first in the HUD: the shapes you draw are filled with the blurred world behind them (only the alpha of your colour is used) | `getIterations/setIterations`, `getOffset/setOffset` | no |
| `EventShader.Bloom` | the glow pass behind HUD elements: what you draw is drawn again, faint and offset, as a halo | `getIterations/setIterations`, `getOffset/setOffset` | no |
| `EventFov` | when the camera's field of view is worked out (not the held item's) | `getFov()`, `setFov(f)` | no |

Notes on a few of them:

- `EventMovementInput` values are what the player *wanted* (W/A/S/D as -1/0/1, scaled when sneaking). Set them to override.
- `EventUpdate.Pre` values you set are written into the real player and the movement packet, then **restored** before
  `EventUpdate.Post`; this is what makes a rotation "silent". The silent rotation manager already does this, so prefer
  `EventSilentRotation` over setting yaw/pitch here yourself.
- `EventPacket` has `getPacket()` returning a raw `Packet`; pattern-match it (`event.getPacket() instanceof ServerboundMovePlayerPacket move`).
  Packets are named by direction: `Serverbound*` go out, `Clientbound*` come in. Many are records (`ClientboundSetEntityMotionPacket.id()`, `.movement()`).

## Silent rotations

A *silent* rotation is a rotation the server sees but your camera does not. `SilentRotationManager`
(`Arsenic.getArsenic().getSilentRotationManager()`) owns it; modules ask for a rotation by answering an event each tick.

### `EventSilentRotation` (answered every tick, from `EventLiving`)

Constructed with the player's **real view** `yaw`/`pitch` (not the current silent rotation). If nobody changes it,
`hasBeenModified()` is false and the silent rotation melts back to the real view.

| Member | Meaning |
|---|---|
| `getYaw/setYaw`, `getPitch/setPitch` | The rotation you want. Set only when you want to take over. |
| `getSpeed/setSpeed(float)` | Maximum turn per tick, in degrees (with smoothing on, pitch gets about 65% of it). The default is the previous tick's value. You must set it when you set a target, otherwise the rotation turns at whatever speed was last used. |
| `setSmoothing(boolean)` | `true` (default): ease in and out with momentum; the turn can be much less than `speed`. `false`: move exactly `min(speed, remaining)` per tick. |
| `setBlockUserInput(boolean)` | While `true`, the player's own **left and right clicks are ignored** (attack and use-item keybinds read as released). Use it while the rotation differs from the camera so a manual click does not go out with the wrong rotation. Resets to `false` every tick; set it each tick you need it. |
| `setMovementFix(MovementFix)` | `OFF` leave movement as is. `STRICT` movement acceleration uses the silent yaw. `SILENT` (default) additionally remaps W/A/S/D so you keep walking where the camera points. |
| `setJumpFix(boolean)` | `true` (default): the sprint-jump boost uses the silent yaw. |
| `setPreventDuplicateLook(boolean)` | Nudges the yaw by one mouse step when two consecutive turns would be identical. |
| `hasBeenModified()` | Whether yaw or pitch differs from what the event was created with. |

The rotation is always run through the sensitivity grid (GCD patch), so the yaw/pitch that is actually sent can differ
from your target by a fraction of a degree and may take several ticks to arrive. **Never assume you reached the target;
read the result from `EventSilentRotation.Post`.**

### `EventSilentRotation.Post` (posted right after, same tick)

| Member | Meaning |
|---|---|
| `getYaw()`, `getPitch()` | The rotation that **will be sent this tick** (applied at `EventUpdate.Pre`). |
| `getPrevYaw()`, `getPrevPitch()` | The rotation sent last tick. |
| `isModified()` | A silent rotation is active (it differs from the camera). |
| `getSpeed()` | The speed in force. |
| `getRayTrace()` | Block ray (4.5 blocks) along that rotation from the eyes, a `BlockHitResult` (its type is `MISS` if it hits nothing). |
| `getRayTraceEntity()` | Same, but the nearest of block and entity, a `HitResult` (`instanceof EntityHitResult e` for an entity). |

### `srm.yaw` / `srm.pitch`

Public fields on the manager. They are updated **after** `EventSilentRotation` has been posted. So inside your
`EventSilentRotation` listener they are the rotation **last sent** (previous tick); from `Post` onwards, and during
`EventTick.Post`/`EventUpdate`, they are the rotation **being sent this tick**; at the next tick's `EventTick` they are
again "last sent". Use them as the "current rotation" to measure how far you still have to turn.

### Interaction follows the silent rotation, not the camera

While a silent rotation is active the game's own interactions use it, so the camera can look anywhere:

- The crosshair target (`mc.hitResult`, `mc.crosshairPickEntity`) is computed from the **silent** yaw/pitch. The client
  asks `EventLook` for the look direction when it works out what is under the crosshair, and `SilentRotationManager`
  answers with the silent rotation whenever one is active.
- Vanilla left and right clicks act on `mc.hitResult`. So **a normal right click places a block where the silent
  rotation points, and a left click attacks or mines what it points at, even though the camera looks elsewhere.** The same
  goes for `PlayerUtils.click()`, which reads `mc.hitResult`.
- Movement packets carry the silent rotation, and with `MovementFix.SILENT` your keys still move you relative to the camera.

What this means for addons:

- To act on the aimed target you do not have to compute a ray yourself: read `mc.hitResult` (it already reflects the
  silent rotation) or `event.getRayTrace()` in `.Post` (the ray of the rotation being sent this tick). Both agree once the rotation has settled.
- Use `setBlockUserInput(true)` while you hold a silent rotation. Otherwise the user's own click would also go to the silent
  target and not to what they see on screen.
- Do not set `EventLook` yourself to fake this; ask for a rotation through `EventSilentRotation` and let the manager answer `EventLook`.

### Movement fix, in one table

| `MovementFix` | Effect | Use when |
|---|---|---|
| `SILENT` (default) | W/A/S/D are remapped so you keep walking where the camera points, jump boost uses the silent yaw | the player can move while you rotate (almost always) |
| `STRICT` | acceleration uses the silent yaw, keys are not remapped | rarely; movement then follows the silent rotation |
| `OFF` | no correction | only for addons that hold the player still (`AutoBlockIn` does). **Using `OFF` while the player moves is a flag.** |

### How the manager applies your request

Each tick it eases yaw/pitch toward your target with momentum capped by `speed`, then runs `RotationUtils.patchGCD` so the
result is a multiple of the mouse-sensitivity step (`RotationUtils.getGCD()`, degrees). With `setPreventDuplicateLook(true)`
it nudges the yaw by one step when two large turns repeat the same delta. The result is written into the movement packet at
`EventUpdate.Pre` and the camera is left alone.

## Legit actions: rotate, then act

Anything that **places, breaks, attacks, uses an item on something or moves toward something** must go through the
silent rotation pipeline above, the way the shipped legit modules (`AutoBlockIn`, `Breaker`, `Nuker`) do. Never set
`mc.player.setYRot/setXRot` directly, never send a hand-made rotation packet, never act on a target you are not
already aimed at.

Which event to act from, as the shipped modules do it (read from their source):

| Action | Module to copy | Decide | Act |
|---|---|---|---|
| **Place a block** | `AutoBlockIn` | `EventSilentRotation` picks the face and requests the rotation | **inside `EventSilentRotation.Post`**, from `event.getRayTrace()`, only if that ray hits the intended block and face |
| **Break a block** | `Breaker`, `Nuker` | `Post` checks the ray of the rotation being sent and stores `breakPos` + `breakFace` | the **next `EventTick`**: `mc.gameMode.continueDestroyBlock(pos, face)` + `PlayerUtils.swingItem()` |
| **Attack / use item on a target** | follow the break pattern | `Post` checks `getRayTraceEntity()` | the next `EventTick` |

Never act from `EventTick` in the same tick you asked for the rotation, and never from a ray you computed yourself with a
different rotation than the one in `Post`. The client sends the action packet before that tick's rotation packet, so
an action taken from a ray that is not the one being sent has been seen to flag `RotationPlace (pre-flying)`.

### Skeleton

```java
@ModuleInfo(name = "Skeleton", category = ModuleCategory.PLAYER)
public class Skeleton extends Module {
    public final DoubleProperty speed = new DoubleProperty("Rotation Speed", new DoubleValue(20, 180, 130, 1));
    public final EnumProperty<SilentRotationManager.MovementFix> fix =
            new EnumProperty<>("Movement Fix", SilentRotationManager.MovementFix.SILENT);   // see the table above
    public final BooleanProperty render = new BooleanProperty("Render", true);               // visuals default ON

    private final MSTimer cooldown = new MSTimer();
    private final java.util.Random random = new java.util.Random();
    private BlockPos target;                // locked until done, do not flick between targets
    private float aimYaw, aimPitch;         // from the aim solver
    private BlockPos actPos; private Direction actFace;   // set in Post, used next tick (break pattern)

    // 1. ask for a rotation
    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (mc.gui.screen() != null) { target = null; return; }          // never act with a GUI open
        if (target == null) target = chooseTarget();                      // your logic
        if (target == null) return;
        float[] aim = aimAt(target);                                      // the aim solver, returns {yaw, pitch}
        event.setYaw(aim[0]);
        event.setPitch(aim[1]);
        event.setSpeed((float) (speed.getValue().getInput() * (0.95 + random.nextDouble() * 0.1)));
        event.setPreventDuplicateLook(true);
        event.setBlockUserInput(true);                                    // ignore the user's own clicks meanwhile
        event.setMovementFix(fix.getValue());
    };

    // 2. act only when the rotation that is being sent really hits what we want
    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        actPos = null;
        if (target == null) return;
        BlockHitResult mop = event.getRayTrace();                         // block ray of the rotation being sent, reach 4.5
        if (mop.getType() != HitResult.Type.BLOCK
                || !mop.getBlockPos().equals(target)) return;             // not there yet: do nothing, keep rotating
        actPos = mop.getBlockPos();                                       // use the face and hit point FROM THE RAY
        actFace = mop.getDirection();                                            // (for placing use mop.getLocation() as well)
    };

    // 3. break/attack pattern: do it on the next tick. (For placing, call the place code in onPost instead.)
    @RequiresPlayer @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (actPos == null || mc.gui.screen() != null) return;
        if (!cooldown.hasTimeElapsed(60 + random.nextInt(60))) return;    // base delay + jitter, never every tick
        mc.gameMode.continueDestroyBlock(actPos, actFace);
        PlayerUtils.swingItem();
        cooldown.reset();
    };

    private BlockPos chooseTarget() { return null; }                      // your logic
    private float[] aimAt(BlockPos p) {                                   // simplest possible aim; use the solver below
        return RotationUtils.rotationsTo(mc.player.getEyePosition(1f),
                new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
    }
}
```

When `Post` shows a ray that misses, do nothing that tick. If it keeps missing (4 ticks is what `AutoBlockIn` uses),
re-solve the aim; after a few failed re-solves or ~40 ticks on one target, skip it and blacklist it for ~60 ticks.

## Aim solver

Aiming at the centre of a block works for a bot and is the first thing that looks wrong. What the legit modules do
(`AutoBlockIn.solveFace`, `Breaker`): they search for the **middle of the largest patch of the face that a real ray can
hit**, then add a small random offset inside that patch.

1. Pick the face to click (for placing: the face of an existing solid block that touches the block you want).
2. Sample a grid over the face and a margin around it (the worked example uses 9x9 over -0.125..1.125). For each sample
   compute `RotationUtils.rotationsTo(eyes, point)` and **trace a real ray** with that rotation. A sample counts as a hit
   only if the ray ends on the intended block *and* the intended face, within reach.
3. For every hit sample, `clearance` is the angle to the nearest sample that misses. Aim at the hit sample with the largest
   clearance: the middle of the biggest visible patch.
4. Jitter by up to half the clearance, then re-check with a ray; if the jittered rotation no longer hits, keep the plain one.
5. If `clearance < Math.toRadians(RotationUtils.getGCD()) * 3 + 0.0015` (the patch is only a few mouse steps wide) a float
   offset is meaningless because rotations are quantised. Dither instead by whole GCD steps, cycling through offsets such as
   `{0,0} {1,0} {-1,0} {0,1} {0,-1} {2,0} {-2,0}` times `getGCD()` on yaw/pitch, one per tick, until the ray confirms.
6. Choose the next target by the **smallest turn from the current rotation** (`srm.yaw/pitch`), and stay on it until it
   is done.

The complete, compiling implementation (`solve`, `rayHits`, `facePoint`) is in the [worked example](#worked-example-bedcover).

## Hotbar handling

When you need a different item (blocks, a tool), switch once, keep it while there is work, and put the old slot back
when idle. Do not swap back and forth for every action.

- Remember the slot you started from (`savedSlot`) the first time you switch, set it with `getInventory().setSelectedSlot(slot)`, and remember
  what you set (`lastSetSlot`).
- If `getSelectedSlot() != lastSetSlot` later, **the user scrolled**: stop, forget `savedSlot`, and back off for about 20 ticks.
- Restore `savedSlot` after about 8 idle ticks with no target.
- `isSwappingHotbar()` returns true while you hold a swapped slot, so `AutoWeapon` leaves it alone.
- `PlayerUtils.getTool(BlockState)` returns the best hotbar slot for mining a block, or -1.

```java
if (savedSlot == -1) savedSlot = mc.player.getInventory().getSelectedSlot();
mc.player.getInventory().setSelectedSlot(slot);
lastSetSlot = slot;
// later, in EventTick:
if (lastSetSlot != -1 && mc.player.getInventory().getSelectedSlot() != lastSetSlot) { yieldTicks = 20; savedSlot = -1; lastSetSlot = -1; }
if (target == null && ++idleTicks >= 8 && savedSlot != -1) { mc.player.getInventory().setSelectedSlot(savedSlot); savedSlot = lastSetSlot = -1; }
@Override public boolean isSwappingHotbar() { return lastSetSlot != -1; }
```

## Sneak to place on a bed or container

Right-clicking a bed, chest, crafting table etc. opens it (a bed shows the sleep prompt) unless the player is **really
sneaking**. `LocalPlayer.isShiftKeyDown()` follows the sneak key, not a flag you can set, so: press the real key, wait two
ticks so the client and server both agree, and require `isShiftKeyDown()` before clicking.

```java
import arsenic.utils.io.Keys;

// tick bookkeeping (EventTick): hold the key while the support block needs it
if (needsSneak(aim.support)) { mc.options.keyShift.setDown(true); sneakTicks++; }
else { sneakTicks = 0; releaseSneak(); }

// in EventSilentRotation.Post, before clicking:
if (needsSneak(mop.getBlockPos()) && !(mc.player.isShiftKeyDown() && sneakTicks >= 2)) return;

boolean needsSneak(BlockPos support) {
    Block b = mc.level.getBlockState(support).getBlock();
    return b instanceof BedBlock || ContainerUtils.isInteractable(b);
}

// release, but never override the user's own held key (keyboard or mouse)
void releaseSneak() {
    mc.options.keyShift.setDown(Keys.isPhysicallyDown(mc.options.keyShift));
}
```

## Verify, then fall back

The server can refuse a placement (anticheat, a bed that cannot be built on, an entity in the way). Do not assume it
worked.

- After a click, remember `verifyPos`. About 4 ticks later check whether the block is still replaceable.
- If it is, remember **which support and face failed** (`key = support.asLong() * 8 + face.get3DDataValue()`) in a map with an
  expiry tick, and make the solver skip that support for ~60 ticks. The next attempt then uses another support.
- Count how often you failed on one target; give up on it after about three recoveries.

```java
if (verifyPos != null && ++verifyTicks >= 4) {
    if (mc.level.getBlockState(verifyPos).canBeReplaced())
        badSupports.put(verifyKey, tickCounter + 60);
    verifyPos = null;
}
```

## Visuals (on by default)

**If the person asking for an addon does not mention visuals, add some anyway; skip them only if they say they do not
want any.** Every addon that targets, plans or acts on something gets both of these, and a `Render` setting so the user
can turn them off:

```java
public final BooleanProperty render = new BooleanProperty("Render", true);
public final BooleanProperty hud    = new BooleanProperty("HUD", true);
```

**World: highlight the target / plan** in `EventRenderWorldLast`, in theme colours. Current target bright, queued
targets dimmer (this is what `AutoBlockIn` does).

```java
@RequiresPlayer @EventLink
public final Listener<EventRenderWorldLast> onWorld = event -> {
    if (!render.getValue() || target == null) return;
    int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
    RenderUtils.renderBlock(target, ColorUtils.withAlpha(main, 90), false, true);    // fill   (renderBlock(pos, argb, outline, shade))
    RenderUtils.renderBlock(target, ColorUtils.withAlpha(main, 230), true, false);   // outline
    RenderUtils.renderBlockFace(supportPos, face, ColorUtils.withAlpha(main, 200), true, true);   // face we click
};
```

`renderBlock(pos, argb, outline, shade)`: `outline` draws the wireframe, `shade` draws the filled box; the colour is ARGB
(alpha in the top byte, so use `ColorUtils.withAlpha(rgb, alpha0to255)`). Theme colours:
`getCurrentTheme().getMainColor()`, `.getDarkerColor()`, `.getWhite()`. The draw ignores depth, so it shows through walls.
All world drawing goes through the game's gizmos (absolute world positions, no camera offset); `RenderUtils` also has
`drawLine`, `drawQuad`, `drawTriangle`, `drawFlatCircle`, `drawFilledCircle`, `drawPoint`, `drawWorldText`,
`drawBoundingBox` and `renderBox`, and `EntityGlow.glow(entity, rgb)` outlines an entity through walls.

**HUD: a small panel or a progress bar** in `EventRender2D`. Register a `hudElement` so the user can move it, and
keep its size in sync with what you draw:

```java
private final HudElement panel = hudElement("MyAddon", 4, 120, 90, 16);

@EventLink
public final Listener<EventRender2D> onHud = event -> {
    if (!hud.getValue()) return;
    String text = (target != null ? "Working" : "Idle") + "  " + count;
    panel.setSize(Math.max(60, mc.font.width(text) + 12), 16);
    DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
    DrawUtils.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
};
```

A progress bar under the crosshair (what `Breaker` draws for mining):

```java
ScaledResolution sr = new ScaledResolution(mc);
float w = 60f, h = 4f, x = sr.getScaledWidth() / 2f - w / 2f, y = sr.getScaledHeight() / 2f + 14f;
DrawUtils.drawRoundedRect(x, y, x + w, y + h, h / 2f, 0x90000000);                       // track
DrawUtils.drawRoundedRect(x, y, x + Math.max(h, w * progress), y + h, h / 2f,
        Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor() | 0xFF000000);   // fill, progress 0..1
```

Other drawing recipes (lines, text, 2D/3D state) are in the [cookbook](#draw-in-2d-and-3d).

## Staying legit (Grim)

The shipped legit modules are built to avoid the common Grim checks. **Nothing here makes an addon undetectable**, servers
configure Grim differently, and none of this has been proven against your server: test on it, and when a flag still
appears ask for its exact name (`RotationPlace`, `Simulation`, ...), because each name points to a specific fix.

Checklist:

1. **Place from `EventSilentRotation.Post` using `event.getRayTrace()`**; break/attack by deciding in `Post` and acting next `EventTick`. Never act from the same tick's `EventTick`.
2. **Only act when the real ray hits what you intend**, right block and right face. Never assume the rotation arrived.
3. **Use the face and hit point of that ray** (`mop.getDirection()`, `mop.getLocation()`), not the point you hoped to hit.
4. **Aim at the middle of the largest visible patch** plus a small random offset; never the exact centre, never a fixed point. Re-pick per target.
5. **Respect the mouse GCD.** Rotations are quantised; dither in whole GCD steps when the patch is small.
6. **Lock a target until it is done.** No flicking between targets; pick the next by smallest turn from the current rotation.
7. **Limit rotation speed**: the repo's legit modules use about 120-140 degrees per tick, keep `setSmoothing(true)` (default), and vary it a few percent.
8. **`setPreventDuplicateLook(true)`** for repeated large turns.
9. **Movement fix `SILENT`** (or `STRICT`) whenever the player can move. `OFF` only when you freeze the player.
10. **Delays:** a base delay plus random jitter between actions; never every tick. Do nothing while a GUI is open (`mc.gui.screen() != null`).
11. **Reach:** at most 4.5 for blocks (`getRayTrace()` already uses 4.5); a conservative default is lower. Require line of sight, never through walls.
12. **Timeouts:** give up on a target after ~40 ticks, re-solve after ~4 ticks of missing, blacklist a failed support for ~60 ticks.
13. **Sneak is real:** press the key, wait 2 ticks, check `isShiftKeyDown()`.
14. **Hotbar:** switch once and keep it, restore when idle, yield to the user if they change slot.
15. **Use only what the client really has.** Stay with public members and the accessor interfaces; do not read fields or packets that do not exist.
16. **Use `setBlockUserInput(true)`** while your rotation differs from the camera, so a manual click cannot go out with the silent rotation.

Common mistakes:

| Symptom | Cause | Fix |
|---|---|---|
| `RotationPlace (pre-flying)` | placing from `EventTick`, or from a ray you computed yourself | place in `EventSilentRotation.Post` from `event.getRayTrace()` |
| movement flags while placing | `MovementFix.OFF` while the player moves | `SILENT` |
| "can't place on the bed", sleep prompt opens | no real sneak | hold the sneak key, wait 2 ticks, check `isShiftKeyDown()` |
| flicking / snapping between blocks | re-choosing the target every tick | lock the target until done or timed out |
| same spot every time | aiming at the centre | random point in the largest patch |
| the module fights the user's hotbar | swapping back and forth | switch once, yield when the selected slot changed |
| acts every tick | no delay | base delay plus jitter |

## Minecraft 26.3 notes

- **Placing:** `mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, face, supportPos, false))`,
  or `ScaffoldUtil.placeBlock(supportPos, face, hitVec)`. `hitVec` is an **absolute world position**.
- **Using the held item:** `mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND)`; let go with `mc.gameMode.releaseUsingItem(mc.player)`.
- **Breaking:** `mc.gameMode.startDestroyBlock(pos, face)` / `continueDestroyBlock(pos, face)`, stop with `stopDestroyBlock()`.
  `IMixinMultiPlayerGameMode`: `setBlockHitDelay(int)`, `getCurBlockDamageMP()` (mining progress 0..1), `isHittingBlock()`.
- **Attacking:** `mc.gameMode.attack(mc.player, entity)` then `PlayerUtils.swingItem()`.
- **Hotbar:** `mc.player.getInventory().getSelectedSlot()` / `setSelectedSlot(i)`; the client sends the change on its next
  tick, or at once with `((IMixinMultiPlayerGameMode) mc.gameMode).syncCurrentPlayItem()`.
- **Inventory clicks:** `mc.gameMode.handleContainerInput(containerId, slot, button, ContainerInput.QUICK_MOVE, mc.player)`
  (`ContainerInput` is 1.8's click mode: `PICKUP` 0, `QUICK_MOVE` 1 (shift), `SWAP` 2, `CLONE`, `THROW`, ...).
- **Blocks:** `BlockState.canBeReplaced()` is "air or replaceable"; `isAir()`, `isSolid()`. Block shapes come from
  `state.getShape(level, pos)` (a `VoxelShape`: `isEmpty()`, `bounds().move(pos)` for the world box); beds are 9/16 high.
- **Items:** stacks are never null, check `isEmpty()`. Swords have no class of their own: use `ItemUtils.isSword(stack)`,
  `isWeapon`, `attackDamage`, `enchantLevel(Enchantments.SHARPNESS, stack)`. Look items up with `ItemUtils.item("ender_pearl")`.
  Item stacks can only be created once a world is loaded.
- **Motion:** `getDeltaMovement()` / `setDeltaMovement(x, y, z)`; rotation `getYRot()` / `setYRot(f)` (yaw), `getXRot()` (pitch).
- **Text:** chat and names are `Component`s: `getString()` for plain text, `TextUtils.toLegacy(component)` for a string with
  `§` colour codes (what 1.8's `getFormattedText` gave). Chat from the server is `ClientboundSystemChatPacket`
  (`content()`, `overlay()` is the action bar).
- **Sounds:** `PlayerUtils.playSound(name, volume, pitch)` takes 1.8 names (`"random.orb"`) or modern ids.
- **Keys:** `mc.options.keyShift.setDown(true)`; `Keys.isPhysicallyDown(mapping)` reads the real key or button;
  `Keys.isKeyDown(InputConstants.KEY_X)`, `Keys.isMouseDown(0)`.
- **Screens:** `mc.gui.screen()` is the open screen (null in game), `mc.gui.setScreen(screen)`.
- `mc.player.isShiftKeyDown()` follows the sneak key (see the sneak section).

## Utility index

Everything below is a real class under `src/main/java/arsenic/`. The [API reference](#api-reference-generated) has every signature;
this is the map. See also UTILITIES.md for the client's own conventions (use these helpers instead of rewriting them).

| Need | Use |
|---|---|
| Stopwatch / cooldown | `utils.timer.MSTimer` (`reset()`, `hasTimeElapsed(ms)`, `hasTimeElapsed(ms, reset)`, `getTime()`), `Timer` (`start()`, `hasFinished()`, `firstFinish()`) |
| Eased 0..1 animation | `utils.timer.AnimationTimer`, `HoverAnimation`, `TickMode`; per-frame smoothing `FrameClock` |
| Clamp, lerp, point-in-rect | `utils.java.MathUtils` (`clamp`, `clamp01`, `lerp`, `inside`, `horizontalDistance`) |
| Colours | `utils.java.ColorUtils` (`withAlpha`, `mixRgb`, `mixArgb`, `getRainbow`) |
| Yaw/pitch to a point | `utils.rotations.RotationUtils.rotationsTo(Vec3 from, Vec3 to)` → `{yaw, pitch}`; `yawTo(dx, dz)`, `pitchTo(dx, dy, dz)` |
| Rotations to entity / block | `RotationUtils.getRotationsToEntity(e)`, `getPlayerRotationsToBlock(pos, face)`, `getBestHitVec(entity)` |
| Angle differences | `RotationUtils.getYawDifference(a, b)` (wrapped to ±180), `getPitchDifference`, `fovToEntity`, `getDistanceToEntityBox` |
| Sensitivity grid | `RotationUtils.patchGCD(prev, current)`, `getGCD()` |
| Turn toward a value at a max speed | `RotationUtils.updateRotation(current, target, speed)` |
| Silent rotation state | `Arsenic.getArsenic().getSilentRotationManager()` (`yaw`, `pitch`) |
| Direction vector of a rotation | `Entity.calculateViewVector(pitch, yaw)` (note: pitch first) |
| Last rotation/sprint the server saw | `IMixinLocalPlayer` (`getLastReportedYaw/Pitch`, `getServerSprintState`) |
| Chat output | `utils.minecraft.PlayerUtils.addWaterMarkedMessageToChat(Object)` (prefixed), `addMessageToChat(String)` |
| Held item checks | `PlayerUtils.isPlayerHoldingSword()`, `isPlayerHoldingBlocks()`, `isHolding(BowItem.class)`, `ItemUtils.isSword(stack)` ... |
| Players / entities nearby | `PlayerUtils.getPlayersWithin(d)`, `getClosestPlayerWithin(d)`, `withinFov(entity, fov)`, `isEntityTeamSameAsPlayer(e)` |
| Click the crosshair target | `PlayerUtils.click()` |
| Inventory clicks | `utils.minecraft.ContainerUtils.click(slot)` (shift-click), `swap(slot, hotbarIndex)`, `drop(slot)`, `getInventoryItems()`, `getBestWeapon()`, `getMostBlocks()` ... |
| Movement | `utils.minecraft.MoveUtil` (`isMoving()`, `strafe(speed)`, `getDirection()`, `getSpeed()`, `stop()`) |
| Send / receive a raw packet | `utils.lag.LagManager.sendPacket(p)`, `receivePacket(p)`; hold or delay packets with `acquire/release`, `delayOutgoing` |
| Ping | `LagManager.getPing()` |
| 2D drawing | `utils.render.DrawUtils` (`drawRect`, `drawRoundedRect`, `drawRoundedOutline`, `drawCircle`, `drawVerticalGradient`, `drawShadow`, `drawStringWithShadow`, `drawTexture`) |
| World drawing | `utils.render.RenderUtils` (`renderBlock`, `renderBox`, `drawBoundingBox`, `drawLine`, `drawQuad`, `drawFlatCircle`, `drawWorldText`, `drawCircle(entity, ...)`), `EntityGlow.glow(entity, rgb)` |
| Text, sounds, item lookup | `utils.minecraft.TextUtils` (`toLegacy`, `plain`), `PlayerUtils.playSound`, `ItemUtils` (`item`, `block`, `isSword`, `armorInventory`) |
| Modules by class | `Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class)` |
| Accessors for private Minecraft state | `arsenic.injection.accessor.IMixin*` (cast the Minecraft object to the interface) |

## Cookbook

Each recipe is the body of a module (imports omitted). They use only the APIs listed in the API reference and the patterns the
shipped addons (AutoSoup, Nuker, Tracers, AutoHunt) already run. Remember `@RequiresPlayer` on listeners that touch
`mc.player`.

### Swap a hotbar slot and use an item

```java
private int returnSlot = -1;
private final MSTimer timer = new MSTimer();

@RequiresPlayer @EventLink
public final Listener<EventTick> onTick = event -> {
    if (returnSlot == -1) {
        int slot = findSoup();
        if (slot == -1 || mc.gui.screen() != null) return;
        returnSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);                 // the held slot changes client side...
        ((IMixinMultiPlayerGameMode) mc.gameMode).syncCurrentPlayItem();  // ...is sent to the server now...
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);        // ...then use whatever is held now
        timer.reset();
    } else if (timer.hasTimeElapsed(500)) {                        // give the server time to finish the use
        if (mc.player.isUsingItem()) mc.gameMode.releaseUsingItem(mc.player);
        mc.player.getInventory().setSelectedSlot(returnSlot);
        returnSlot = -1;
    }
};

@Override public boolean isSwappingHotbar() { return returnSlot != -1; }   // keep AutoWeapon away
```

Order matters: select the slot first, then call `useItem` (it uses the held stack at call time). AutoSoup
spreads the two over separate ticks (`SWITCHED` then `CLICKED`) and restores the slot with `releaseUsingItem`, which is the
safe pattern for items with a use duration.

### Click inventory slots

```java
// InventoryMenu slots: 0 craft out, 1-4 craft grid, 5-8 armour, 9-35 main inventory, 36-44 hotbar, 45 offhand
ContainerUtils.click(slot);                    // shift-click the slot (moves it between hotbar and inventory)
ContainerUtils.swap(slot, 2);                  // swap the slot with hotbar index 2 (0-8)
ContainerUtils.drop(slot);                     // drop the whole stack
// or directly, with the open container's id:
mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
//                               container id                         slot  button  mode
```

Do clicks from `EventTick`, spaced with an `MSTimer` (AutoSoup clicks one slot every 75 ms while the inventory is open).
`ContainerUtils.*` always address the player's own inventory menu; use `handleContainerInput` for chests.

### Raycast, and aim at a block or entity

```java
private Vec3 aim;                                        // the point we want to look at

@RequiresPlayer @EventLink
public final Listener<EventSilentRotation> onRotation = event -> {
    if (aim == null) return;
    float[] r = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
    event.setYaw(r[0]);
    event.setPitch(r[1]);
    event.setSpeed(90);                                  // degrees per tick at most
};

@RequiresPlayer @EventLink
public final Listener<EventSilentRotation.Post> onPost = event -> {
    BlockHitResult hit = event.getRayTrace();            // ray along the rotation that is sent this tick
    if (hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(targetPos)) { ready = true; }
};
```

For an entity use `event.getRayTraceEntity()` and check `hit instanceof EntityHitResult e` (then `e.getEntity()`). For your own ray (other reach, other origin):

```java
Vec3 eyes = mc.player.getEyePosition(1f);
Vec3 look = Entity.calculateViewVector(pitch, yaw);
Vec3 end  = eyes.add(look.x * reach, look.y * reach, look.z * reach);
BlockHitResult hit = PlayerUtils.rayTraceBlocks(eyes, end);   // blocks only; type MISS when nothing is in the way
```

### Send a raw packet

```java
LagManager.sendPacket(new ServerboundSetCarriedItemPacket(slot));       // goes through the normal send queue
mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));     // same thing without the helper
```

Both pass through `EventPacket.OutGoing`, so your own listener will see them; ignore your own packets with a flag if that
matters. To change or drop what the game sends, listen to `EventPacket.OutGoing` and call `setPacket(..)` / `cancel()`.

### Draw in 2D and 3D

```java
@EventLink
public final Listener<EventRender2D> on2d = event -> {          // HUD, screen pixels (scaled GUI coordinates)
    DrawUtils.drawRoundedRect(4, 4, 100, 20, 4, 0x96121212);
    DrawUtils.drawStringWithShadow("Hello", 8, 9, 0xFFFFFFFF);
};

@RequiresPlayer @EventLink
public final Listener<EventRenderWorldLast> on3d = event -> {   // world space, absolute positions
    for (Player p : mc.level.players()) {
        if (p == mc.player) continue;
        Vec3 pos = RenderUtils.interpolatedPosition(p);            // smooth between ticks
        RenderUtils.drawLine(mc.player.getEyePosition(event.partialTicks), pos.add(0, p.getBbHeight() / 2, 0),
                0xFFFF5555, 2f);                                     // ARGB, line width in pixels
        RenderUtils.drawWorldText(pos.add(0, p.getBbHeight() + 0.5, 0), p.getName().getString(), 0xFFFFFFFF, 1f);
    }
};
```

World drawing goes through the game's gizmo system, so there is no GL state to set or restore and nothing is drawn
outside an `EventRenderWorldLast` listener. See Tracers for the full version. For HUD panels use `hudElement(...)` so the
user can move them.

### Add a chat command

```java
{ registerCommand(new HelloCommand()); }       // in the module body; removed again when the addon unloads

@CommandInfo(name = "hello", args = { "name" }, aliases = { "hi" }, help = "says hello", minArgs = 1)
private class HelloCommand extends Command {
    @Override
    public void execute(String[] args) {                       // args exclude the command word
        PlayerUtils.addWaterMarkedMessageToChat("Hello " + args[0]);
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {   // optional tab completion
        if (arg == 0) list.add("world");
        return list;
    }
}
```

Commands are typed with a leading dot (`.hello Bob`). `minArgs` below the given count prints the usage line for you.

### Schedule a delayed action without a thread

Do not use `Thread.sleep` or your own threads: Minecraft state is only safe on the game thread. Count ticks or compare
an `MSTimer` inside a listener:

```java
private int ticksLeft = -1;
private Runnable pending;

private void later(int ticks, Runnable r) { ticksLeft = ticks; pending = r; }   // call from anywhere on the game thread

@RequiresPlayer @EventLink
public final Listener<EventTick> onTick = event -> {
    if (ticksLeft > 0 && --ticksLeft == 0 && pending != null) { Runnable r = pending; pending = null; r.run(); }
};
```

One tick is 50 ms (`ticksLeft = 4` is about 200 ms), but ticks stretch when the game lags, and `MSTimer` does not.
Use a tick counter when you care about server ticks, an `MSTimer` when you care about real time. For a multi-step
sequence write a small `enum State` and a `switch` in `EventTick`, as AutoSoup does. From a packet listener (network
thread) use `mc.execute(runnable)` to get back onto the game thread.

## Worked example: BedCover

A complete, compiling addon that uses everything above: it finds the nearest bed and covers its open sides with blocks.
Properties with `@ShowWhen`, silent rotation with `MovementFix`, the aim solver, placing from
`EventSilentRotation.Post`, hotbar handling with `isSwappingHotbar`, real sneak for the bed, verify-and-fall-back, world
and HUD visuals that default on. Save it as `BedCover.java`; every API it calls is in the API reference below.
(It has been compiled against the client, not tested on a server.)

```java
import arsenic.utils.io.Keys;
import net.minecraft.world.entity.Entity;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Surrounds the nearest bed with blocks, the legit way: rotate silently, place from the ray of the rotation being sent. */
@ModuleInfo(name = "BedCover", description = "Covers the nearest bed with blocks", category = ModuleCategory.PLAYER)
public class BedCover extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(2, 4.5, 4.5, 0.1));
    public final DoubleProperty speed = new DoubleProperty("Rotation Speed", new DoubleValue(20, 180, 130, 1));
    public final DoubleProperty delay = new DoubleProperty("Place Delay", new DoubleValue(50, 500, 120, 5));
    public final EnumProperty<SilentRotationManager.MovementFix> fix =
            new EnumProperty<>("Movement Fix", SilentRotationManager.MovementFix.SILENT);
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);
    @ShowWhen(property = "HUD", is = "true")
    public final BooleanProperty showCount = new BooleanProperty("Show Count", true);

    private final HudElement panel = hudElement("BedCover", 4, 120, 90, 16);
    private final Random random = new Random();
    private final MSTimer placeTimer = new MSTimer();
    private final Map<Long, Integer> badSupports = new HashMap<>();   // support key -> tick it may be tried again

    private BlockPos target;          // the block we are trying to place, locked until placed or given up
    private Aim aim;                  // how to click it
    private int ticksOnStep, missTicks, sneakTicks, idleTicks, yieldTicks, verifyTicks, tickCounter, placed;
    private int savedSlot = -1, lastSetSlot = -1;
    private BlockPos verifyPos;
    private long verifyKey;
    private boolean weSneak;

    private static final int MAX_STEP_TICKS = 40, RESOLVE_AFTER_MISSES = 4, BLACKLIST_TICKS = 60;
    private static final double[][] DITHER = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}};

    private static final class Aim {
        final BlockPos support; final Direction face; final float yaw, pitch; final double clearanceRad;
        Aim(BlockPos s, Direction f, float y, float p, double c) { support = s; face = f; yaw = y; pitch = p; clearanceRad = c; }
    }

    @Override
    protected void onDisable() {
        releaseSneak();
        restoreSlot();
        target = null;
        aim = null;
    }

    // ---- tick bookkeeping: timers, sneak, hotbar, verification (never place from here) ----
    @RequiresPlayer @EventLink
    public final Listener<EventTick> onTick = event -> {
        tickCounter++;
        if (yieldTicks > 0) yieldTicks--;
        if (lastSetSlot != -1 && mc.player.getInventory().getSelectedSlot() != lastSetSlot) {   // the user scrolled: hands off
            yieldTicks = 20; savedSlot = -1; lastSetSlot = -1;
        }
        if (target == null) {
            idleTicks++;
            if (idleTicks >= 8) { releaseSneak(); restoreSlot(); }
        } else {
            idleTicks = 0;
        }
        if (aim != null && needsSneak(aim.support)) { holdSneak(); sneakTicks++; } else { sneakTicks = 0; releaseSneak(); }
        if (verifyPos != null && ++verifyTicks >= 4) {            // did the server accept the block?
            if (isReplaceable(verifyPos)) badSupports.put(verifyKey, tickCounter + BLACKLIST_TICKS);
            else placed++;
            verifyPos = null;
        }
    };

    // ---- 1. ask for a rotation ----
    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (mc.gui.screen() != null || yieldTicks > 0) { target = null; aim = null; return; }
        if (target == null || !isReplaceable(target) || aim == null) pickTarget();
        if (target == null || aim == null) return;
        if (++ticksOnStep > MAX_STEP_TICKS) { giveUp(); return; }

        float yaw = aim.yaw, pitch = aim.pitch;
        if (aim.clearanceRad < settledFloorRad()) {                // target patch smaller than ~3 mouse steps: dither
            double[] d = DITHER[ticksOnStep % DITHER.length];
            yaw += (float) (d[0] * RotationUtils.getGCD());
            pitch += (float) (d[1] * RotationUtils.getGCD());
        }
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setSpeed((float) (speed.getValue().getInput() * (0.95 + random.nextDouble() * 0.1)));
        event.setPreventDuplicateLook(true);
        event.setBlockUserInput(true);
        event.setMovementFix(fix.getValue());
    };

    // ---- 2. act only if the rotation being sent really hits the intended face ----
    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (target == null || aim == null) return;
        BlockHitResult mop = event.getRayTrace();
        if (mop == null || mop.getType() != HitResult.Type.BLOCK
                || !mop.getBlockPos().relative(mop.getDirection()).equals(target)) {
            if (++missTicks >= RESOLVE_AFTER_MISSES) { missTicks = 0; aim = solve(target); if (aim == null) giveUp(); }
            return;
        }
        missTicks = 0;
        long wait = (long) (delay.getValue().getInput() * (0.85 + random.nextDouble() * 0.3));
        if (!placeTimer.finished(wait)) return;
        if (needsSneak(mop.getBlockPos()) && !(mc.player.isShiftKeyDown() && sneakTicks >= 2)) return;
        int slot = findBlockSlot();
        if (slot == -1) { setEnabled(false); return; }
        if (savedSlot == -1) savedSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        lastSetSlot = slot;
        if (arsenic.utils.minecraft.ScaffoldUtil.placeBlock(mop.getBlockPos(), mop.getDirection(), mop.getLocation())) {
            PlayerUtils.swingItem();
            verifyPos = target;
            verifyTicks = 0;
            verifyKey = key(mop.getBlockPos(), mop.getDirection());
            placeTimer.reset();
            target = null;
            aim = null;
        }
    };

    // ---- target choice ----
    private void pickTarget() {
        target = null; aim = null; ticksOnStep = 0; missTicks = 0;
        BlockPos bed = nearestBed();
        if (bed == null) return;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        Vec3 eyes = mc.player.getEyePosition(1f);
        double bestTurn = Double.MAX_VALUE;
        List<BlockPos> halves = new ArrayList<>();
        halves.add(bed);
        for (Direction side : Direction.Plane.HORIZONTAL)
            if (mc.level.getBlockState(bed.relative(side)).getBlock() instanceof BedBlock) halves.add(bed.relative(side));
        for (BlockPos half : halves) {
            for (Direction side : Direction.values()) {
                BlockPos pos = half.relative(side);
                if (side == Direction.DOWN || !isReplaceable(pos) || intersectsPlayer(pos)) continue;
                Aim a = solve(pos);
                if (a == null) continue;
                // smallest turn from where we already look = least visible movement
                double turn = Math.abs(RotationUtils.getYawDifference(a.yaw, srm.yaw)) + Math.abs(a.pitch - srm.pitch);
                if (turn < bestTurn) { bestTurn = turn; target = pos; aim = a; }
            }
        }
    }

    private BlockPos nearestBed() {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int r = (int) Math.ceil(range.getValue().getInput()) + 1;
        BlockPos origin = mc.player.blockPosition();
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            BlockPos p = origin.offset(x, y, z);
            if (!(mc.level.getBlockState(p).getBlock() instanceof BedBlock)) continue;
            double d = mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    // ---- aim solver: sample the face, keep rays that really hit it, aim at the middle of the biggest clear patch ----
    private Aim solve(BlockPos placePos) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        double reach = range.getValue().getInput();
        Aim best = null;
        for (Direction face : Direction.values()) {
            BlockPos support = placePos.relative(face.getOpposite());
            if (isReplaceable(support)) continue;
            Integer retryAt = badSupports.get(key(support, face));
            if (retryAt != null && retryAt > tickCounter) continue;
            VoxelShape shape = mc.level.getBlockState(support).getShape(mc.level, support);   // beds are only 9/16 high
            if (shape.isEmpty()) continue;
            AABB box = shape.bounds().move(support);

            final int n = 9;                                                    // grid over the face, plus a margin outside it
            float[][][] rots = new float[n][n][];
            boolean[][] hit = new boolean[n][n];
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
                double u = -0.125 + 1.25 * i / (n - 1), v = -0.125 + 1.25 * j / (n - 1);
                rots[i][j] = RotationUtils.rotationsTo(eyes, facePoint(box, face, u, v));
                hit[i][j] = rayHits(eyes, rots[i][j][0], rots[i][j][1], reach, support, face);
            }
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
                if (!hit[i][j]) continue;
                double clearance = Double.MAX_VALUE;                            // angle to the nearest ray that misses
                for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) {
                    if (hit[a][b]) continue;
                    double dy = RotationUtils.getYawDifference(rots[i][j][0], rots[a][b][0]);
                    double dp = rots[i][j][1] - rots[a][b][1];
                    clearance = Math.min(clearance, Math.toRadians(Math.sqrt(dy * dy + dp * dp)));
                }
                if (best != null && clearance <= best.clearanceRad) continue;
                float yaw = rots[i][j][0], pitch = rots[i][j][1];
                float jy = (float) ((random.nextDouble() - 0.5) * Math.toDegrees(clearance) * 0.5);   // never the exact point
                float jp = (float) ((random.nextDouble() - 0.5) * Math.toDegrees(clearance) * 0.5);
                if (rayHits(eyes, yaw + jy, pitch + jp, reach, support, face)) { yaw += jy; pitch += jp; }
                best = new Aim(support, face, yaw, pitch, clearance);
            }
        }
        return best;
    }

    private boolean rayHits(Vec3 eyes, float yaw, float pitch, double reach, BlockPos support, Direction face) {
        Vec3 look = Entity.calculateViewVector(pitch, yaw);   // note: pitch first
        Vec3 end = eyes.add(look.x * reach, look.y * reach, look.z * reach);
        BlockHitResult mop = PlayerUtils.rayTraceBlocks(eyes, end);
        return mop.getType() == HitResult.Type.BLOCK
                && mop.getBlockPos().equals(support) && mop.getDirection() == face;
    }

    private static Vec3 facePoint(AABB b, Direction f, double u, double v) {
        switch (f.getAxis()) {
            case Y: return new Vec3(b.minX + (b.maxX - b.minX) * u, f == Direction.UP ? b.maxY : b.minY, b.minZ + (b.maxZ - b.minZ) * v);
            case X: return new Vec3(f == Direction.EAST ? b.maxX : b.minX, b.minY + (b.maxY - b.minY) * u, b.minZ + (b.maxZ - b.minZ) * v);
            default: return new Vec3(b.minX + (b.maxX - b.minX) * u, b.minY + (b.maxY - b.minY) * v, f == Direction.SOUTH ? b.maxZ : b.minZ);
        }
    }

    private static double settledFloorRad() { return Math.toRadians(RotationUtils.getGCD()) * 3.0 + 0.0015; }

    // ---- helpers ----
    private boolean isReplaceable(BlockPos p) { return mc.level.getBlockState(p).canBeReplaced(); }   // air counts

    private boolean intersectsPlayer(BlockPos p) {
        return mc.player.getBoundingBox().intersects(new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1));
    }

    private static long key(BlockPos p, Direction f) { return p.asLong() * 8 + f.get3DDataValue(); }

    private void giveUp() {
        if (target != null) badSupports.put(aim != null ? key(aim.support, aim.face) : 0L, tickCounter + BLACKLIST_TICKS);
        target = null; aim = null;
    }

    private int findBlockSlot() {
        int best = -1, bestSize = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty() || !(s.getItem() instanceof BlockItem)) continue;
            Block b = ((BlockItem) s.getItem()).getBlock();
            if (!b.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    || ContainerUtils.isInteractable(b)) continue;
            if (s.getCount() > bestSize) { bestSize = s.getCount(); best = i; }
        }
        return best;
    }

    private void restoreSlot() {
        if (savedSlot != -1 && mc.player != null && mc.player.getInventory().getSelectedSlot() == lastSetSlot)
            mc.player.getInventory().setSelectedSlot(savedSlot);
        savedSlot = -1;
        lastSetSlot = -1;
    }

    @Override
    public boolean isSwappingHotbar() { return lastSetSlot != -1; }                 // keeps AutoWeapon away

    // ---- real sneak: press the key, never fake the flag ----
    private boolean needsSneak(BlockPos support) {
        Block b = mc.level.getBlockState(support).getBlock();
        return b instanceof BedBlock || ContainerUtils.isInteractable(b);          // right-clicking these would open them
    }

    private void holdSneak() {
        mc.options.keyShift.setDown(true);
        weSneak = true;
    }

    private void releaseSneak() {
        if (!weSneak) return;
        // keep it down if the user is really holding it (keyboard or mouse)
        mc.options.keyShift.setDown(Keys.isPhysicallyDown(mc.options.keyShift));
        weSneak = false;
    }

    // ---- visuals: on by default, switchable ----
    @RequiresPlayer @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || target == null) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.renderBlock(target, ColorUtils.withAlpha(main, 90), false, true);     // fill
        RenderUtils.renderBlock(target, ColorUtils.withAlpha(main, 230), true, false);    // outline
        if (aim != null) RenderUtils.renderBlockFace(aim.support, aim.face, ColorUtils.withAlpha(main, 200), true, true);
    };

    @EventLink
    public final Listener<EventRender2D> onRenderHud = event -> {
        if (!hud.getValue()) return;
        String text = target != null ? "Placing" : "Idle";
        if (showCount.getValue()) text += "  " + placed;
        panel.setSize(Math.max(60, mc.font.width(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        DrawUtils.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
```

## Pitfalls

- **Act from the right event.** Place from `EventSilentRotation.Post` using `event.getRayTrace()`; break/attack by deciding
  in `Post` and acting on the next `EventTick`. Never act from `EventTick` in the tick you asked for the rotation. See
  [Legit actions](#legit-actions-rotate-then-act).
- **A rotation request does not mean you arrived.** Speed limits, smoothing and the sensitivity grid mean the rotation
  can take several ticks. Check `EventSilentRotation.Post` before acting.
- **`setBlockUserInput` is per tick.** It resets to `false` each tick, so set it every tick you need it.
- **Exact-class dispatch.** `Listener<EventPacket>` and `Listener<EventUpdate>` never fire. Use the nested classes.
- **Incoming packets arrive on the network thread** (`EventPacket.Incoming.*`). Do not read or modify the world,
  entities or inventory there; set a flag and handle it in `EventTick`, or use `mc.execute`.
- **Do not change game state in render events.** `EventRender2D` / `EventRenderWorldLast` run once per *frame*, not per tick,
  at uncapped rates. Draw there, decide in `EventTick`.
- **`@RequiresPlayer` only skips when both player and world are null.** Null-check `mc.player` / `mc.level` in render
  listeners and anywhere a half-loaded world can reach.
- **Optimistic client state.** `useItem` and `handleContainerInput` update the client's copy of the stack before the server
  answers; the server can still refuse, and the stack snaps back. Do not chain decisions on the client stack in the same tick.
- **Visuals are not optional.** An addon without a world highlight and a HUD panel is incomplete unless the user said no
  visuals; give them a `Render` toggle. See [Visuals](#visuals-on-by-default).
- **Select the slot before `useItem` / `useItemOn`**, not after, and put it back afterwards (and
  tell AutoWeapon with `isSwappingHotbar()` while you are away from the player's slot).
- **`EventJump.cancel()` does not block jumping.** To stop a jump use `EventMovementInput.setJump(false)`.
- **`EventDisplayGuiScreen` is posted after the screen opened**, so it cannot veto it.
- **Listener fields are read from your class only.** Listeners declared in a base class you extend are ignored.
- **Properties must be `public` fields** to be shown and saved. `ButtonProperty` and `StringProperty` are never saved.
- **`@ShowWhen` hides, it does not disable.** A hidden setting keeps its value; ignore it in code when its mode is off.
- **Use `MSTimer`/ticks, not `System.currentTimeMillis()` and not threads** (see the cookbook).
- **Compile errors skip only the one file**, but another addon that depends on that file will fail too. Read the chat output
  of `.addon reload`.
- **Reload drops `static` state and every field of the old module instance**; keep what must survive in properties.

## How the loader works

Minecraft 26.x is not obfuscated and Fabric runs it under Mojang's names, so an addon is compiled against exactly the
classes the game runs:

1. The Eclipse compiler (embedded, so a plain JRE works) reads Minecraft, Fabric and Arsenic class files from the game's
   own class loader, and the JDK's from the runtime image.
2. The classes it produces are defined from memory by a class loader that sits below the game's, so they see everything
   the game does. Mixins are not applied to addon classes.
3. Nothing is remapped; a dev environment and a release jar behave the same.

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
  "icon": "textures/item/golden_sword.png",
  "autoInstall": false,
  "addons": { "AutoHunt": "What AutoHunt does, shown in the Addon Manager" },
  "requires": { "FightBot": ["AutoHunt", "hypixel/GameDetector"] }
}
```

`icon` is either a texture from the game's resources (`textures/item/book.png`, `textures/block/red_wool.png`) or an
item id drawn like an inventory slot (`minecraft:clock`). Item icons only show once a world is loaded (item stacks need
its registries), so prefer a texture. `autoInstall` packs are installed and enabled the first time the client
runs.

Every addon in the folder is compiled together, so an addon can use another addon's classes.
`requires` (optional) lists, per addon, the addons it uses: a bare name is an addon of the same pack, `pack/Addon` one
of another pack. Enabling an addon enables what it needs, installing a bundled pack if the needed addon is in one, and
disabling an addon disables the addons that need it. The Addon Manager shows the needs on each addon's card. Only
pack addons can declare needs, and only pack addons can be needed. Installed packs live in `Arsenic/addons/packs/<id>/`; drop a pack `.zip`
into `Arsenic/addons/packs/` and it is unpacked into a folder of the same name on the next reload (the zip is
renamed to `.zip.imported`). Files ending in `.java.disabled` are loaded too; whether an addon's module is on comes from
the config, like any module.

## Default addons and the Addon Manager

Several modules ship as addons instead of being compiled into the client. Their sources live in `src/addons`
(IntelliJ treats the folders as source and Gradle compiles them as a check) and are bundled into the jar:

- `src/addons/java` and `src/addons/addons.json`: loose default addons and their descriptions.
- `src/addons/packs/<id>`: the bundled packs (`pack.json` plus `impl`). List a new pack's id in `addons.json`.

On startup each default addon or pack that was never seen before is copied into the addons folder: autoInstall
packs enabled, everything else as `.java.disabled` files. Anything you later delete or rename is left alone.

**Updates:** on every start, an installed default whose bundled source changed (a new client version) is rewritten in
place, keeping whether it was enabled. The loader remembers a hash of what it installed (`Arsenic/addons/.defaults-hashes`),
so a default you **edited is never overwritten**; to take the new version, delete your copy. A default of unknown origin
(installed before this tracking existed) is replaced after a backup is written next to it (`Name.java.bak`). Addons a
new version adds to an already installed pack appear in it automatically (disabled unless the pack is `autoInstall`).

Click **Addon Manager** in the ClickGUI's bottom right corner: the category column lists **All addons**, every pack
and any loose addons, and the main area shows the selected list's addons in two columns. Each addon is drawn and
behaves exactly like a module row: the switch turns its module on and off, opening it (arrow or right click) shows its
description and settings, and it has a keybind and a Hidden chip. `.toggle`, `.enable` and `.<Addon> <setting> <value>`
work on it too. Addons are only loaded when the game starts, by **Reload addons** and by
`.addon reload`, and every addon in the folder is loaded, on or off. An addon added since then, or one that does not
compile (see Errors), has no module until the next reload. Addons are not listed in the module categories; find them
in the Addon Manager or with the search box.

`registerCommand(new MyCommand())` (see the cookbook), `allowsTarget(player)` and `isSwappingHotbar()` are the hooks
beyond a plain module; see [Module basics](#module-basics).

## When something is missing

The API reference below is generated from the source and is exact. If you still need behaviour, read the source of the
module that already does it (paths under `src/main/java/arsenic/` and `src/addons/`):

| Need | Read |
|---|---|
| legit block placing, face solver, GCD dither, step timeouts | `module/impl/player/AutoBlockIn.java` |
| legit breaking, jittered aim, progress bar | `src/addons/packs/bedwars/impl/Breaker.java` |
| properties, `@ShowWhen`, `SliderScale.LOG`, `EventSilentRotation` + `.Post` | `src/addons/java/Nuker.java` |
| hotbar swap, refilling, `isSwappingHotbar` | `src/addons/java/AutoSoup.java` |
| world drawing | `src/addons/java/Tracers.java` |
| how the rotation is applied | `utils/rotations/SilentRotationManager.java`, `event/impl/EventSilentRotation.java` |
| chat commands, packs, targeting hooks | `src/addons/packs/pit/impl/AutoHunt.java` |

## API reference (generated)

Everything below the marker is **generated from the sources by the build** (`gradlew generateAddonApi`, also run by
every build) and written into this file, so it cannot drift. Do not edit it by hand. It lists the exact public and
protected surface (constructors, fields, methods, enum constants, annotation members, first line of each Javadoc) of
`Module`, `@ModuleInfo`, categories and tiers, all properties, all events and the event bus annotations, `Command`,
`Arsenic`, `HudElement`, the accessor interfaces, and the helpers under `utils/rotations`, `timer`, `java`, `minecraft`,
`lag` plus the draw/render utilities. The scanner is `tools/apidump/ApiDump.java`; to include more classes add their
paths to `apiEntries` in `build.gradle`. A copy is also bundled in the jar as `assets/arsenic/addons/API.md`.

<!-- API:BEGIN -->
### arsenic.module.Module

```java
public class Module implements IContainer<Property<?>>, ISerializable {
    protected static Minecraft mc;
    protected static Arsenic client;
    protected List<SerializableProperty<?>> serializableProperties;
    public Module();
    protected void registerProperty(Property<?> p);
    /** Registers a draggable HUD spot for this module; it appears in the HUD editor and is saved with the module. */ protected HudElement hudElement(String label, int x, int y, int width, int height);
    protected HudElement hudElement(String label, int x, int y, int width, int height, boolean rightAnchored);
    /** Registers a chat command that exists while this module is loaded (used by addons). */ protected void registerCommand(Command command);
    public List<Command> getCommands();
    /** While this module is enabled, TargetManager only accepts targets this returns true for. */ public boolean allowsTarget(Player player);
    /** True while the module is moving items around the hotbar, so AutoWeapon keeps its hands off. */ public boolean isSwappingHotbar();
    public List<HudElement> getHudElements();
    public void registerProperties() throws IllegalAccessException;
    /** What was wrong with this module's @ShowWhen rules when it was registered (see Visibility). */ public List<String> getPropertyProblems();
    public String getHudInfo();
    protected void onEnable();
    protected void onDisable();
    public String getName();
    public String getDescription();
    public ModuleTier getTier();
    public ModuleCategory getCategory();
    public boolean isEnabled();
    public void toggle();
    public void setEnabled(boolean enabled);
    public void setEnabledSilently(boolean enabled);
    public boolean isHidden();
    public void setHidden(boolean hidden);
    public String getDisplayName();
    public void setDisplayName(String displayName);
    public boolean isAddon();
    public int getKeybind();
    public void setKeybind(int keybind);
    public Collection<Property<?>> getContents();
    public List<? extends Property<?>> getProperties();
    /** Each value loads on its own: a missing or broken one keeps its current value and the rest still load. */ public void loadFromJson(JsonObject obj);
    protected void postApplyConfig();
    public JsonObject saveInfoToJson(JsonObject obj);
    public String getJsonKey();
}
```

### arsenic.module.ModuleInfo

```java
public @interface ModuleInfo {
    String name();
    String description() default "";
    ModuleCategory category();
    int keybind() default 0;
    boolean enabled() default false;
    boolean hidden() default false;
    ModuleTier tier() default ModuleTier.LEGIT;
}
```

### arsenic.module.ModuleCategory

```java
public enum ModuleCategory implements IContainer<Module>, IContainable {
    COMBAT, MOVEMENT, PLAYER, RENDER, CLIENT, CONFIGS, GUI, SEARCH;
    public String getName();
    /** Addons are left out here; they are reached from the Addon Manager and from search. */ public Collection<Module> getContents();
}
```

### arsenic.module.ModuleTier

```java
public enum ModuleTier {
    QOL, VISUAL, LEGIT, BLATANT, DEV;
    public String getDisplayName();
}
```

### arsenic.module.property.Group

```java
public enum Group {
    MAIN, VISUAL, ADVANCED;
    public String getLabel();
    /** The icon's file name in assets/arsenic/icons, without .png. */ public String getIcon();
}
```

### arsenic.module.property.impl.BooleanProperty

```java
public class BooleanProperty extends SerializableProperty<Boolean> implements IReliable {
    public BooleanProperty(String name, Boolean value);
    public JsonObject saveInfoToJson(JsonObject obj);
    public void loadFromJson(JsonObject obj);
    public Supplier<Boolean> valueCheck(String value);
    public String currentValue();
    public java.util.List<String> possibleValues();
}
```

### arsenic.module.property.impl.ButtonProperty

```java
public class ButtonProperty extends Property<String> {
    public ButtonProperty(String value);
    public ButtonProperty(String name, Runnable action);
    public ButtonProperty(String name, String label, Runnable action);
    public void fire();
}
```

### arsenic.module.property.impl.ColourProperty

```java
public class ColourProperty extends SerializableProperty<Integer> {
    public ColourProperty(String name, int value);
    public JsonObject saveInfoToJson(JsonObject obj);
    public void loadFromJson(JsonObject obj);
    public void setMode(cMode mode);
    public void setColor(int i, int newValue);
    public Integer getValue();
    public int getColor(int i);

    public enum cMode {
        CUSTOM, THEME;
    }
}
```

### arsenic.module.property.impl.DisplayMode

```java
public enum DisplayMode {
    NORMAL, PERCENT, MILLIS;
    public String getSuffix();
    public String toString();
}
```

### arsenic.module.property.impl.doubleproperty.DoubleProperty

```java
public class DoubleProperty extends SerializableProperty<DoubleValue> {
    public DoubleProperty(String name, DoubleValue value);
    public DoubleProperty(String name, DoubleValue value, SliderScale scale);
    public JsonObject saveInfoToJson(JsonObject obj);
    public void loadFromJson(JsonObject obj);
    public String getValueString();
    public DisplayMode getDisplayMode();
}
```

### arsenic.module.property.impl.doubleproperty.DoubleValue

```java
public class DoubleValue {
    public DoubleValue(double minBound, double maxBound, double value, double inc);
    public double getInput();
    public void setInputSilently(double value);
    public void setInput(double value);
    public double getMaxBound();
    public double getMinBound();
    public void onUpdate();
}
```

### arsenic.module.property.impl.EnumProperty

```java
public class EnumProperty<T extends Enum<?>> extends SerializableProperty<T> implements IReliable {
    public EnumProperty(String name, T value);
    public JsonObject saveInfoToJson(JsonObject obj);
    public void loadFromJson(JsonObject obj);
    public void nextMode();
    public void prevMode();
    public boolean setByName(String name);
    public List<String> getModeNames();
    public Supplier<Boolean> valueCheck(String value);
    public String currentValue();
    public List<String> possibleValues();
}
```

### arsenic.module.property.impl.FolderProperty

```java
public class FolderProperty extends SerializableProperty<List<Property<?>>> {
    public FolderProperty(String name, Property<?>... values);
    public void loadFromJson(JsonObject obj);
    public JsonObject saveInfoToJson(JsonObject obj);
}
```

### arsenic.module.property.impl.rangeproperty.RangeProperty

```java
public class RangeProperty extends SerializableProperty<RangeValue> {
    public RangeProperty(String name, RangeValue value);
    public RangeProperty(String name, RangeValue value, SliderScale scale);
    public JsonObject saveInfoToJson(JsonObject obj);
    public void loadFromJson(JsonObject obj);
    public String getValueString();
    public boolean hasInRange(double value);
    public DisplayMode getDisplayMode();

    public enum Helping {
        MIN, MAX;
    }
}
```

### arsenic.module.property.impl.rangeproperty.RangeValue

```java
public class RangeValue {
    public RangeValue(double minBound, double maxBound, double min, double max, double inc);
    public double getMin();
    public void setMinSilently(double min);
    public void setMin(double min);
    public double getMax();
    public void setMaxSilently(double max);
    public void setMax(double min);
    public double getMaxBound();
    public double getMinBound();
    public double getRandomInRange();
    public void onUpdate();
}
```

### arsenic.module.property.impl.SliderScale

```java
public enum SliderScale {
    LINEAR, LOG;
    public float toPercent(double v, double lo, double hi);
    public double fromPercent(double pct, double lo, double hi);
}
```

### arsenic.module.property.impl.StringProperty

```java
public class StringProperty extends Property<String> {
    public StringProperty(String value);
}
```

### arsenic.module.property.impl.TextProperty

```java
public class TextProperty extends SerializableProperty<String> {
    public TextProperty(String name, String value);
    public TextProperty(String name, String value, int maxLength);
    public void setValue(String value);
    public void setValueSilently(String value);
    public JsonObject saveInfoToJson(JsonObject obj);
    public void loadFromJson(JsonObject obj);
}
```

### arsenic.module.property.IReliable

```java
public interface IReliable {
    /** True while this setting has the value and is itself shown. Kept for older code; ShowWhen uses the methods below. */ Supplier<Boolean> valueCheck(String value);
    /** The value as ShowWhen compares it: "true"/"false", or the enum constant's name. */ String currentValue();
    /** Every value #currentValue() can return, so ShowWhen values can be checked when the module is registered. */ List<String> possibleValues();
}
```

### arsenic.module.property.Property

```java
public abstract class Property<T> implements IContainable {
    protected T value;
    protected Module parent;
    protected Supplier<Boolean> visible;
    public void setParent(Module parent);
    protected Property(T value);
    public T getValue();
    public void setValueSilently(T value);
    public void setValue(T value);
    public void onValueUpdate();
    public void setVisible(Supplier<Boolean> visible);
    public boolean isVisible();
    public Group getGroup();
    /** Normally set from PropertyGroup; for properties that are not fields (e.g. built by an addon at runtime). */ public void setGroup(Group group);
    public String getName();
}
```

### arsenic.module.property.PropertyGroup

```java
public @interface PropertyGroup {
    Group value();
}
```

### arsenic.module.property.PropertyInfo

```java
public @interface PropertyInfo {
    String reliesOn();
    String value();
}
```

### arsenic.module.property.SerializableProperty

```java
public abstract class SerializableProperty<T> extends Property<T> implements ISerializable {
    protected String name;
    protected SerializableProperty(String name, T value);
    public String getJsonKey();
    public String getName();
}
```

### arsenic.module.property.ShowWhen

```java
public @interface ShowWhen {
    String property();
    /** Shown while the target has any of these values. */ String[] is() default;
    /** Shown while the target has none of these values. */ String[] not() default;

    @interface All {
        ShowWhen[] value();
    }
}
```

### arsenic.module.property.Visibility

```java
public final class Visibility {
    /** Wires the rules of every public property field of owner. A rule with a problem is left out, so its setting stays */ public static List<String> wire(Object owner);
}
```

### arsenic.event.types.CancellableEvent

```java
public class CancellableEvent implements Event {
    public boolean isCancelled();
    public void setCancelled(boolean cancelled);
    public void cancel();
}
```

### arsenic.event.types.Event

```java
public interface Event {
}
```

### arsenic.event.impl.EventAttack

```java
public class EventAttack implements Event {
    public EventAttack(Entity target);
    public Entity getTarget();
}
```

### arsenic.event.impl.EventDisplayGuiScreen

```java
public class EventDisplayGuiScreen extends CancellableEvent {
    public EventDisplayGuiScreen(Screen guiScreen);
    public Screen getGuiScreen();
    public void setGuiScreen(Screen guiScreen);
}
```

### arsenic.event.impl.EventFov

```java
public class EventFov implements Event {
    public EventFov(float fov);
    public float getFov();
    public void setFov(float fov);
    public boolean isModified();
}
```

### arsenic.event.impl.EventGameLoop

```java
public class EventGameLoop implements Event {
}
```

### arsenic.event.impl.EventJump

```java
public class EventJump extends CancellableEvent implements Event {
    public EventJump(float yaw, float motion);
    public float getYaw();
    public void setYaw(float yaw);
    public float getMotion();
    public void setMotion(float motion);
}
```

### arsenic.event.impl.EventKey

```java
public class EventKey implements Event {
    public EventKey(int keycode);
    public int getKeycode();
}
```

### arsenic.event.impl.EventLiving

```java
public class EventLiving implements Event {
}
```

### arsenic.event.impl.EventLook

```java
public class EventLook implements Event {
    public EventLook(float yaw, float pitch);
    public float getPitch();
    public float getYaw();
    public void setPitch(float pitch);
    public void setYaw(float yaw);
    public boolean hasBeenModified();
}
```

### arsenic.event.impl.EventMouse

```java
public class EventMouse implements Event {
    public int button;

    public static class Down extends EventMouse {
        public Down(int button);
    }

    public static class Up extends EventMouse {
        public Up(int button);
    }
}
```

### arsenic.event.impl.EventMove

```java
public class EventMove implements Event {
    public EventMove(float strafe, float forward, float friction, float yaw);
    public float getStrafe();
    public void setStrafe(float strafe);
    public float getForward();
    public void setForward(float forward);
    public float getFriction();
    public void setFriction(float friction);
    public float getYaw();
    public void setYaw(float yaw);
}
```

### arsenic.event.impl.EventMovementInput

```java
public class EventMovementInput extends CancellableEvent {
    public EventMovementInput(float speed, float strafe, boolean jump);
    public void setSpeed(float speed);
    public void setStrafe(float strafe);
    public void setJump(boolean jump);
    public float getSpeed();
    public float getStrafe();
    public boolean isJumping();
}
```

### arsenic.event.impl.EventPacket

```java
public class EventPacket extends CancellableEvent {
    public EventPacket(Packet<?> packet);
    public Packet<?> getPacket();
    public void setPacket(Packet<?> packet);

    public static class OutGoing extends EventPacket {
        public OutGoing(Packet<?> packet);
    }

    public static class Incoming extends EventPacket {
        public Incoming(Packet<?> packet);

        public static class Pre extends Incoming {
            public Pre(Packet<?> packet);
        }

        public static class Post extends Incoming {
            public Post(Packet<?> packet);
        }
    }
}
```

### arsenic.event.impl.EventPlayerJoinWorld

```java
public class EventPlayerJoinWorld implements Event {
    public EventPlayerJoinWorld(Player entity, Level world);
    public Player getEntity();
    public Level getWorld();
}
```

### arsenic.event.impl.EventRender2D

```java
public class EventRender2D implements Event {
    public EventRender2D(GuiGraphicsExtractor graphics, float partialTicks);
    public GuiGraphicsExtractor getGraphics();
    public float getPartialTicks();
    /** Scaled screen width, what 1.8 called ScaledResolution#getScaledWidth(). */ public int getWidth();
    public int getHeight();
    /** The scaled screen size under its 1.8 name. */ public arsenic.utils.render.ScaledResolution getSr();
}
```

### arsenic.event.impl.EventRenderThirdPerson

```java
public class EventRenderThirdPerson implements Event {
    public EventRenderThirdPerson(float yaw, float pitch, float prevYaw, float prevPitch);
    public float getPitch();
    public float getYaw();
    public void setPitch(float pitch);
    public void setYaw(float yaw);
    public float getPrevYaw();
    public void setPrevYaw(float prevYaw);
    public float getPrevPitch();
    public void setPrevPitch(float prevPitch);
    public void setAccepted(boolean accepted);
    public boolean getAccepted();
}
```

### arsenic.event.impl.EventRenderWorldLast

```java
public class EventRenderWorldLast implements Event {
    public float partialTicks;
    public EventRenderWorldLast(float partialTicks);
}
```

### arsenic.event.impl.EventRunTick

```java
public class EventRunTick implements Event {
}
```

### arsenic.event.impl.EventShader

```java
public class EventShader implements Event {
    public EventShader(int iterations,int offset,boolean blur);
    public int getIterations();
    public void setIterations(int iterations);
    public int getOffset();
    public void setOffset(int offset);

    public static class Bloom extends EventShader {
        public Bloom(int iterations,int offset);
    }

    public static class Blur extends EventShader {
        public Blur(int iterations,int offset);
    }
}
```

### arsenic.event.impl.EventSilentRotation

```java
public class EventSilentRotation implements Event {
    public EventSilentRotation(float yaw, float pitch,float speed);
    public boolean hasBeenModified();
    public float getYaw();
    public void setYaw(float yaw);
    public float getPitch();
    public void setPitch(float pitch);
    public float getSpeed();
    public void setSpeed(float speed);
    public SilentRotationManager.MovementFix getMovementFix();
    public boolean doJumpFix();
    public void setJumpFix(boolean doJumpFix);
    public void setMovementFix(SilentRotationManager.MovementFix movementFix);
    public boolean isPreventDuplicateLook();
    public void setPreventDuplicateLook(boolean preventDuplicateLook);
    public boolean isSmoothing();
    public void setSmoothing(boolean smoothing);
    public boolean isBlockUserInput();
    public void setBlockUserInput(boolean blockUserInput);

    public static class Post implements Event {
        public Post(float yaw, float pitch, float prevYaw, float prevPitch, boolean modified, float speed);
        public float getYaw();
        public float getPitch();
        public float getPrevYaw();
        public float getPrevPitch();
        public boolean isModified();
        public float getSpeed();
        /** Block the committed rotation is looking at, within vanilla reach. */ public net.minecraft.world.phys.BlockHitResult getRayTrace();
        /** Whatever the committed rotation hits first, entity or block. */ public HitResult getRayTraceEntity();
    }
}
```

### arsenic.event.impl.EventTick

```java
public class EventTick implements Event {
    public static class Post extends EventTick {
    }
}
```

### arsenic.event.impl.EventUpdate

```java
public class EventUpdate extends CancellableEvent {
    protected EventUpdate(double x, double y, double z, float yaw, float pitch, boolean onGround, boolean pre);
    public double getX();
    public void setX(double x);
    public double getY();
    public void setY(double y);
    public double getZ();
    public void setZ(double z);
    public float getYaw();
    public void setYaw(float yaw);
    public float getPitch();
    public void setPitch(float pitch);
    public boolean isOnGround();
    public void setOnGround(boolean onGround);
    public boolean isPre();
    public boolean isPost();

    public static class Pre extends EventUpdate {
        public Pre(double x, double y, double z, float yaw, float pitch, boolean onGround);
    }

    public static class Post extends EventUpdate {
        public Post(double x, double y, double z, float yaw, float pitch, boolean onGround);
    }
}
```

### arsenic.event.bus.Listener

```java
public interface Listener<Event> {
    void call(Event event);
}
```

### arsenic.event.bus.annotations.EventLink

```java
public @interface EventLink {
    byte value() default Priorities.MEDIUM;
}
```

### arsenic.event.bus.Priorities

```java
public final class Priorities {
    public static byte VERY_LOW;
    public static byte LOW;
    public static byte MEDIUM;
    public static byte HIGH;
    public static byte VERY_HIGH;
}
```

### arsenic.asm.RequiresPlayer

```java
public @interface RequiresPlayer {
}
```

### arsenic.command.Command

```java
public abstract class Command {
    protected String name;
    protected String help;
    protected String[] aliases;
    protected String[] args;
    protected String usage;
    public Command();
    public abstract void execute(String[] args);
    public List<String> getAutoComplete(String str, int arg);
    public List<String> getAutoComplete(String[] args);
    protected List<String> getAutoComplete(String str, int arg, List<String> completions);
    public String getName();
    public String getHelp();
    public String[] getAliases();
    public String[] getArgs();
    public boolean isName(String name);
    public int getMinArgs();
    public String getUsage();
}
```

### arsenic.command.CommandInfo

```java
public @interface CommandInfo {
    String name();
    int minArgs() default 0;
    String help() default "No help provided for this command";
    String[] args() default;
    String[] aliases() default;
}
```

### arsenic.main.Arsenic

```java
public class Arsenic implements ClientModInitializer {
    public void onInitializeClient();
    public String getName();
    public static Arsenic getInstance();
    public static Arsenic getArsenic();
    public String getClientName();
    public long getClientVersion();
    public String getClientVersionString();
    public Logger getLogger();
    public EventManager getEventManager();
    public ModuleManager getModuleManager();
    public AddonManager getAddonManager();
    public ErrorOverlay getErrorOverlay();
    public Fonts getFonts();
    public ConfigManager getConfigManager();
    public CommandManager getCommandManager();
    public ClickGuiScreen getClickGuiScreen();
    public SilentRotationManager getSilentRotationManager();
    public ThemeManager getThemeManager();
    public ServerInfo getServerInfo();
    public FriendManager getFriendManager();
}
```

### arsenic.gui.hud.HudElement

```java
public final class HudElement {
    public String label;
    /** When true, #x is an offset from the right edge of the screen (0 = flush right). */ public boolean rightAnchored;
    public int x, y;
    public int width, height;
    /** Whether the element is currently drawn; the HUD editor only lists elements that return true. */ public BooleanSupplier active;
    public HudElement(String label, int x, int y, int width, int height, boolean rightAnchored);
    public void setSize(int width, int height);
    public void reset();
    public void save(JsonObject hud);
    public void load(JsonObject hud);
}
```

### arsenic.injection.accessor.IMixinKeyMapping

```java
public interface IMixinKeyMapping {
    InputConstants.Key getBoundKey();
}
```

### arsenic.injection.accessor.IMixinLocalPlayer

```java
public interface IMixinLocalPlayer {
    float getLastReportedYaw();
    float getLastReportedPitch();
    boolean getServerSprintState();
    void setLastReportedYaw(float lastReportedYaw);
    void setLastReportedPitch(float lastReportedPitch);
    void setServerSprintState(boolean serverSprintState);
}
```

### arsenic.injection.accessor.IMixinMinecraft

```java
public interface IMixinMinecraft {
    boolean leftClick();
    void rightClick();
    int getRightClickDelay();
    void setRightClickDelay(int delay);
}
```

### arsenic.injection.accessor.IMixinMoveEntityPacket

```java
public interface IMixinMoveEntityPacket {
    int getEntityId();
}
```

### arsenic.injection.accessor.IMixinMovePlayerPacket

```java
public interface IMixinMovePlayerPacket {
    boolean getHasRotField();
    boolean getHasPosField();
    boolean getOnGroundField();
    void setOnGround(boolean onGround);
    float getPitch();
    void setPitch(float pitch);
    float getYaw();
    void setYaw(float yaw);
    double getX();
    void setX(double x);
    double getY();
    void setY(double y);
    double getZ();
    void setZ(double z);
}
```

### arsenic.injection.accessor.IMixinMultiPlayerGameMode

```java
public interface IMixinMultiPlayerGameMode {
    int getBlockHitDelay();
    void setBlockHitDelay(int delay);
    float getCurBlockDamageMP();
    void setCurBlockDamageMP(float progress);
    boolean isHittingBlock();
    /** Sends the selected hotbar slot now if it changed, as 1.8's syncCurrentPlayItem did. */ void syncCurrentPlayItem();
}
```

### arsenic.utils.rotations.AimController

```java
public class AimController {
    public float defaultPrediction();
    /** Rough share of the time Heuristics aim spends slipped off the hitbox, 0 to 1. */ public void setMissChance(float chance);
    /** Pitch-band mode: hold aim pitch inside [lo, hi] degrees (positive looks down) and damp its movement there. */ public void setPitchBand(boolean on, float lo, float hi);
    /** Whether the entity's hitbox can be hit with aim pitch inside the pitch band. */ public boolean inPitchBand(Entity e);
    public void reset();
    public void cancelFlick();
    public void updateDrift();
    public float[] aimAt(Entity e, float ticks);
    public void rotate(EventSilentRotation event, Entity target, float[] rots, RotationMode mode, float minSpeed, float maxSpeed, float budgetTicks);
    public float[] getPredictedRotations(Entity e, float ticks);
    public AABB predictBox(Entity e, float ticks);

    public enum RotationMode {
        Instant, Lazy, Heuristics;
    }
}
```

### arsenic.utils.rotations.RotationUtils

```java
public class RotationUtils extends UtilityClass {
    /** Yaw that faces the horizontal offset (dx, dz). */ public static float yawTo(double dx, double dz);
    /** Pitch that faces the offset (dx, dy, dz); negative looks up. */ public static float pitchTo(double dx, double dy, double dz);
    /** {yaw, pitch} that look from one point at another. */ public static float[] rotationsTo(Vec3 from, Vec3 to);
    public static float[] getRotationsToEntity(LivingEntity e);
    public static float[] getCappedRotations(float[] prev, float[] current, float speed);
    public static float[] getPatchedAndCappedRots(float[] prev, float[] current, float speed);
    /** Snaps a rotation change to a whole number of mouse steps, like a real mouse would produce. */ public static float[] patchGCD(float[] prevRotation, float[] currentRotation);
    /** One mouse count, in degrees, at the current sensitivity (MouseHandler#turnPlayer). */ public static float getGCD();
    public static Vec3 getBestHitVec(final Entity entity);
    public static double getDistanceToEntityBox(Entity entity);
    public static double getDistanceToEntityBox(Entity target, Player from);
    public static float fovFromEntity(Entity en);
    public static float fovToEntity(Entity ent);
    public static float[] getRotations(Vec3 from, Vec3 to);
    public static float[] getPlayerRotationsToVec(Vec3 to);
    public static Vec3 getVec3FromBlockPosAndEnumFacing(BlockPos blockPos, Direction face);
    public static double getDistanceToBlockPos(BlockPos blockPos);
    public static float[] getPlayerRotationsToBlock(BlockPos pos, Direction face);
    public static float getYawDifference(float yaw1, float yaw2);
    public static float getPitchDifference(float pitch1, float pitch2);
    public static float[] getRotations(final BlockPos blockPos);
    public static float clamp(final float n);
    public static float updateRotation(float current, float target, float speed);
}
```

### arsenic.utils.rotations.SilentRotationManager

```java
public class SilentRotationManager {
    public float yaw;
    public float pitch;
    public Listener<EventLiving> eventTickListener;
    public boolean isBlockingUserInput();
    public boolean isModified();
    public boolean isPrevModified();
    public float getPrevYaw();
    public float getPrevPitch();
    public Listener<EventUpdate.Pre> eventUpdateListener;
    public Listener<EventLook> eventLookListener;
    public Listener<EventRenderThirdPerson> eventRenderThirdPersonListener;
    public Listener<EventMove> eventMoveListener;
    public Listener<EventMovementInput> eventMovementInputListener;
    public Listener<EventJump> eventJumpListener;
    public float normaliseYaw(float yaw);
    public static float wrapAngleToPi(float value);

    public enum MovementFix {
        OFF, STRICT, SILENT;
    }
}
```

### arsenic.utils.timer.AnimationTimer

```java
public class AnimationTimer {
    public AnimationTimer(int maxMs, Supplier<Boolean> func);
    public AnimationTimer(int maxMs, Supplier<Boolean> func, TickMode tickMode);
    public float getPercent();
    public void setElapsedMs(int ms);
    public void setMaxMs(int maxMs);
}
```

### arsenic.utils.timer.FrameClock

```java
public class FrameClock {
    /** Seconds since the previous call, capped so a stalled frame does not make values jump. */ public float tick();
    /** Frame-rate independent current += (target - current) * rate * dt. */ public static float approach(float current, float target, float rate, float dt);
}
```

### arsenic.utils.timer.HoverAnimation

```java
public class HoverAnimation {
    public static int DEFAULT_MS;
    public HoverAnimation();
    public HoverAnimation(int ms, TickMode mode);
    /** Sets whether the target is hovered/active this frame and returns the eased 0..1 amount. */ public float update(boolean active);
}
```

### arsenic.utils.timer.MSTimer

```java
public class MSTimer {
    public long lastMS;
    /** A timer that already counts as long elapsed, for "last happened at" fields that start unset. */ public static MSTimer expired();
    public void reset();
    public boolean hasTimeElapsed(long time, boolean reset);
    public boolean finished(final long delay);
    public boolean hasTimeElapsed(long time);
    public boolean hasTimeElapsed(double time);
    public long getTime();
    public void setTime(long time);
}
```

### arsenic.utils.timer.TickMode

```java
public enum TickMode {
    SINE, LINEAR, ROOT, SQR, CUBIC, EXPO, BACK;
    public float toSmoothPercent(float f);
    /** Eases the input after clamping it to 0..1. */ public float clamped(float f);
}
```

### arsenic.utils.timer.Timer

```java
public class Timer {
    public Timer();
    public Timer(long coolDownTime);
    public void start();
    public boolean hasFinished();
    public boolean firstFinish();
    public void setCooldown(long coolDownTime);
    public long getCooldownTime();
    public long getElapsedTime();
    public long getElapsedTimeAsPercent();
    public long getTimeLeft();
    public boolean hasExceededTimeBy(long additionalThreshold);
}
```

### arsenic.utils.timer.TimeUtils

```java
public final class TimeUtils {
    /** True for the first half of every period and false for the second: a text cursor blink. */ public static boolean blink(long halfPeriodMs);
}
```

### arsenic.utils.java.ClassScanner

```java
public final class ClassScanner {
    /** Every concrete subclass of type in pkg or below. */ public static <T> List<Class<? extends T>> findSubTypes(String pkg, Class<T> type);
}
```

### arsenic.utils.java.ColorUtils

```java
public class ColorUtils extends UtilityClass {
    public static int setColor(int value, int i, int newValue);
    public static int withAlpha(int rgb, int alpha);
    /** Keeps the colour and scales its alpha to 0..1 of full. */ public static int withAlpha(int rgb, float alpha);
    /** Blends the rgb channels only; the result has no alpha. */ public static int mixRgb(int a, int b, float t);
    /** Blends two argb colours channel by channel, alpha included. */ public static int mixArgb(int a, int b, float t);
    public static float luminance(int rgb);
    public static int getColor(int value, int i);
    public static int getThemeRainbowColor(long speed, long delay);
    public static int getRainbow(float speed, long delay);
}
```

### arsenic.utils.java.FileUtils

```java
public class FileUtils extends UtilityClass {
    public static String getArsenicFolderDirAsString();
    /** The Arsenic folder: configs, addons, themes and everything else Arsenic saves. It sits in the user's home folder */ public static synchronized File getArsenicFolderDirAsFile();
    public static String readInputStream(InputStream inputStream);
}
```

### arsenic.utils.java.JavaUtils

```java
public class JavaUtils extends UtilityClass {
    public static <T> T[] concat(T[] a, T[] b);
    public static double getRandom(double min, double max);
    public static double limit(double value, double min, double max);
    public static List<String> autoCompleteHelper(List<String> list, String arg);
}
```

### arsenic.utils.java.MathUtils

```java
public class MathUtils extends UtilityClass {
    public static int clamp(int v, int lo, int hi);
    public static float clamp(float v, float lo, float hi);
    public static double clamp(double v, double lo, double hi);
    /** Length of the (dx, dz) offset. */ public static double horizontalDistance(double dx, double dz);
    public static float clamp01(float v);
    public static float lerp(float from, float to, float t);
    /** Point in rectangle, inclusive on every edge. */ public static boolean inside(double px, double py, double x1, double y1, double x2, double y2);
    /** Point in a rectangle given as position and size, exclusive on the far edges (vanilla GuiButton hit test). */ public static boolean insideSized(double px, double py, double x, double y, double w, double h);
}
```

### arsenic.utils.java.PlayerInfo

```java
public class PlayerInfo {
    public boolean isServerSprintState();
    public float getLastReportedYaw();
    public float getLastReportedPitch();
    public PlayerInfo(float lastReportedYaw, float lastReportedPitch, boolean serverSprintState);
}
```

### arsenic.utils.java.SoundUtils

```java
public class SoundUtils {
    public static void playSound(String name);
    public static void playSound(String name, float volume);
    public static void playEvent(String name, float pitch);
    /** A vanilla sound by its modern id ("entity.experience_orb.pickup") or its 1.8 name ("random.orb", "note.pling", */ public static SoundEvent byName(String name);
    public static void chordEnable();
    public static void chordDisable();
    public static void chordCategory();
    public static void chordOpen();
    public static void chordEnum();
    public static void chordKeybind();
    public static void chordClick();
    public static void hitConfirm();
    public static void note(int degree);
    public static void cmajStep();
    public static void cmajUp();
    public static void cmajDown();
    public static void cmajTone(int degree);
    public static void slide(float fraction);
    public static void tick();
}
```

### arsenic.utils.java.UtilityClass

```java
public abstract class UtilityClass {
    protected static Minecraft mc;
    protected UtilityClass();
}
```

### arsenic.utils.minecraft.AutoBlocker

```java
public final class AutoBlocker {
    public static boolean isPacketMode(Mode mode);
    public static boolean isRenderBlocking();
    public static void setRenderBlocking(boolean blocking);
    /** Legit: really raises the sword for a moment after we are hit (hurt time 10 to 6) while the target is */ public void tickLegit(Player target, boolean active);
    /** Before KillAura attacks this tick. Nothing attacks in a tick that starts blocking. Hypixel's block from the last */ public void tickPacket(Mode mode, Player target, boolean active);
    /** Hypixel Hold, once this tick's look ray is known: lowers the sword when the next tick is going to attack, since a */ public void releaseBeforeAttack(Mode mode, boolean attackNextTick);
    /** Whether an attack on this entity would hurt it: our look ray meets it within reach, and it is out of its hurt */ public boolean shouldHit(Mode mode, Entity hit, Vec3 hitVec);
    /** #shouldHit(Mode, Entity, Vec3) for an attack this many ticks from now, the look ray kept as it is. */ public boolean shouldHit(Mode mode, Entity hit, Vec3 hitVec, int ticksAhead);
    /** Called by KillAura once its attack for this tick went out, with the entity hit and where the look ray met it. */ public void onAttack(Mode mode, Entity hit, Vec3 hitVec, boolean active);
    public boolean canAttack();
    /** Whether the sword is up because of a packet block, so KillAura does not wait on its own use of the item. */ public boolean isBlocking();
    /** Every outgoing packet, before it is sent. Returns true to drop it: an attack in a tick that already released. */ public boolean onOutgoing(Packet<?> packet);
    /** After the tick's movement packet went out. The client's item use from a block is cleared here: it already slowed */ public void afterMovement();
    public void stopPacket();

    public enum Mode {
        None, Legit, Hypixel, HypixelHold;
    }
}
```

### arsenic.utils.minecraft.BadPacketsManager

```java
public final class BadPacketsManager {
    /** Whether the server sees an item in use (Grim's slowed-by-using-item): from a use-item packet until a release, a */ public static boolean serverUsingItem();
    public static boolean bad();
    /** Whether any of the selected kinds of packet went out this tick. */ public static boolean bad(boolean slot, boolean attack, boolean swing, boolean block, boolean inventory);
    /** Sends a packet without it counting towards this tick's flags. */ public static void sendSilently(Packet<?> packet);
    public static void reset();
    public Listener<EventPacket.OutGoing> onOutgoing;
}
```

### arsenic.utils.minecraft.BedwarsTracker

```java
public final class BedwarsTracker {
    /** True from the start of a BedWars game (or the first respawn or bed message in it) until the world changes. */ public static boolean inGame();
    /** Changes every time a new game starts, so addons can reset per-game counters. */ public static int gameId();
    /** Both halves of your bed, empty while it is not known or after it was destroyed. */ public static List<BlockPos> ownBed();
    /** Whether the position is a half of your bed. */ public static boolean isOwnBed(BlockPos pos);
    /** True once the server said "You can't destroy your own bed!" about it, rather than it being the nearest bed. */ public static boolean isOwnBedConfirmed();
    /** True after "BED DESTRUCTION > Your Bed was destroyed". */ public static boolean ownBedDestroyed();
    /** System time of "Your Bed was destroyed", or 0. */ public static long ownBedDestroyedAt();
    /** Colour names of the other teams whose bed is gone this game, e.g. "Red". */ public static Set<String> destroyedBeds();
    /** Colour names of the teams eliminated this game. */ public static Set<String> eliminatedTeams();
    public Listener<EventPacket.Incoming.Post> onPacket;
    public Listener<EventPacket.OutGoing> onSend;
    public Listener<EventTick> onTick;
}
```

### arsenic.utils.minecraft.ContainerUtils

```java
public class ContainerUtils extends UtilityClass {
    public static void click(int slot);
    public static void drop(int slot);
    public static void swap(int slot, int targetSlot);
    public static List<SlotItem> getInventoryItems();
    public static List<SlotItem> getInventoryItemsWithArmor();
    public static int getBestWeapon();
    public static int getMostProjectiles();
    public static int getMostBlocks();
    public static int getBiggestStack(Item item);
    public static int getBestBow();
    /** Best tool of a kind, e.g. ItemTags.PICKAXES. */ public static int getBestTool(TagKey<Item> toolTag);
    public static int getBestArmor(int index);
    /** Mining speed against stone - a fair stand-in for 1.8's tool-material efficiency. */ public static double getEffeciency(ItemStack stack);
    public static boolean canBePlaced(BlockItem blockItem);
    public static boolean isInteractable(Block block);
    public static float getEfficiency(final ItemStack itemStack, BlockState block);
    public static double getDamage(final ItemStack itemStack);
    public static float getPower(ItemStack stack);
    public static ItemStack getItemStack(int i);
    public static int getArmorLevel(final ItemStack itemStack);
    public static int getProtection(final ItemStack itemStack);
    public static boolean isProjectiles(ItemStack stack);

    public static class SlotItem {
        public int slot;
        public ItemStack item;
        public SlotItem(int slot, ItemStack item);
    }
}
```

### arsenic.utils.minecraft.HotbarUse

```java
public final class HotbarUse extends UtilityClass {
    /** The hotbar slot (0-8) of the first stack that matches, or -1. */ public static int find(Predicate<ItemStack> match);
    /** The inventory menu slot (9-44, hotbar last) of the first stack that matches, or -1. */ public static int findMenuSlot(Predicate<ItemStack> match);
    /** Whether this has moved the hotbar and not yet put it back. Modules return this from isSwappingHotbar. */ public boolean active();
    /** Goes back to the slot we left once a one-shot use is over. */ public void tick();
    /** Switches to the slot and keeps it until #release(). */ public void select(int slot);
    /** Switches to the slot for this tick only, for an action the caller does itself. */ public void switchOnce(int slot);
    /** Switches to the slot, uses the item once and goes back next tick. */ public void use(int slot);
    public void release();
}
```

### arsenic.utils.minecraft.ItemUtils

```java
public class ItemUtils extends UtilityClass {
    public static boolean isSword(ItemStack stack);
    public static boolean isMace(ItemStack stack);
    public static boolean isSpear(ItemStack stack);
    public static boolean isAxe(ItemStack stack);
    public static boolean isWeapon(ItemStack stack);
    public static boolean isBlock(ItemStack stack);
    /** The armour slot this stack equips into, or null if it is not armour. */ public static EquipmentSlot getArmorSlot(ItemStack stack);
    /** The worn armour piece by 1.8's armorInventory index: 0 boots, 1 leggings, 2 chestplate, 3 helmet. */ public static ItemStack armorInventory(net.minecraft.world.entity.LivingEntity entity, int index);
    /** The item with this id ("diamond_sword", "minecraft:ender_pearl"), or null. */ public static net.minecraft.world.item.Item item(String id);
    /** The block with this id ("bed", "minecraft:chest"), or null. */ public static net.minecraft.world.level.block.Block block(String id);
    /** 1.8 armour index: 0 helmet, 1 chestplate, 2 leggings, 3 boots. */ public static EquipmentSlot armorSlotFromIndex(int index);
    /** The value an attribute would have with only this item equipped in slot. */ public static double attribute(ItemStack stack, Holder<Attribute> attribute, double base, EquipmentSlot slot);
    /** Attack damage of a held item, excluding enchantments. */ public static double attackDamage(ItemStack stack);
    public static double armorPoints(ItemStack stack);
    /** Splash potions - what AutoPot throws and Refill stocks. */ public static boolean isSplashPotion(ItemStack stack);
    public static boolean hasEffect(ItemStack stack, Holder<net.minecraft.world.effect.MobEffect> effect);
    public static int enchantLevel(ResourceKey<Enchantment> enchantment, ItemStack stack);
}
```

### arsenic.utils.minecraft.McScaffoldWorld

```java
public final class McScaffoldWorld implements ScaffoldWorld {
    public boolean isAir(int x, int y, int z);
    public boolean isFullCube(int x, int y, int z);
    /** 1.8's ItemBlock#canPlaceBlockOnSide: whether the held block (TNT if none) could go against this face. */ public boolean canPlaceOnSide(int x, int y, int z, int face);
    public boolean boxFree(Box b);
    public boolean rayTrace(double sx, double sy, double sz, double ex, double ey, double ez, Hit out);
}
```

### arsenic.utils.minecraft.MoveUtil

```java
public class MoveUtil extends UtilityClass {
    public static double WALK_SPEED;
    public static double WEB_SPEED;
    public static double SWIM_SPEED;
    public static double SNEAK_SPEED;
    public static double SPRINTING_SPEED;
    public static double[] DEPTH_STRIDER;
    /** 1.8's moveForward / moveStrafing are LivingEntity#zza / #xxa. */ public static boolean isMoving();
    public static boolean isInLiquid();
    public static boolean enoughMovementForSprinting();
    public static void strafe(double speed);
    public static void horitzontalClip(float amount);
    public static float getDirection();
    public static float getMovementYaw();
    public static double getBaseSpeed();
    public static float getPerfectValue(float noSpeed, float speed1, float speed2);
    public static float getSpeed();
    public static void stop();
}
```

### arsenic.utils.minecraft.PlayerUtils

```java
public class PlayerUtils extends UtilityClass {
    public static void addMessageToChat(String msg);
    public static boolean isHolding(Class<? extends Item> type);
    public static boolean isPlayerHoldingWeapon();
    /** The hand that blocks, main hand first, or null. Latest-version servers block with a shield, and 1.8 servers (also */ public static InteractionHand blockingHand();
    public static boolean isPlayerHoldingBlocker();
    public static boolean isPlayerHoldingBlocks();
    public static boolean isPlayerHoldingSword();
    public static boolean isPlayerHoldingBow();
    public static void addWaterMarkedMessageToChat(Object object);
    public static void addWaterMarkedMessageToChat(Object... object);
    public static boolean playerOverAir();
    public static boolean playerIsEdging(Entity entity);
    public static BlockPos getBlockUnderPlayer();
    /** Plays a sound to the player only. Takes the 1.8 sound names ("random.orb", "note.pling") as well as modern ids */ public static void playSound(String name, float volume, float pitch);
    public static void swingItem();
    /** A vanilla left click on whatever the crosshair is on. */ public static void click();
    public static Player getClosestPlayerWithin(double distance);
    /** Despite the name this has always returned true when a piece is MISSING; kept as-is. */ public static boolean isPlayerWearingArmour(Player en);
    public static boolean withinFov(Entity entity, float fov);
    public static List<Player> getPlayersWithin(double distance);
    public static List<Entity> getEntitysWithin(double distance);
    /** 1.8's World#rayTraceBlocks: the first block between two points, against outline shapes. */ public static net.minecraft.world.phys.BlockHitResult rayTraceBlocks(Vec3 from, Vec3 to);
    /** True once there is a player and a level to act on. */ public static boolean isPlayerLoaded();
    public static boolean isEntityTeamSameAsPlayer(LivingEntity target);
    public static int getTool(BlockState block);
    public static float getEfficiency(final ItemStack itemStack, BlockState block);
}
```

### arsenic.utils.minecraft.ScaffoldUtil

```java
public class ScaffoldUtil extends UtilityClass {
    public static Block block(final double x, double y, double z);
    public static boolean willFallNextTick();
    public static McScaffoldWorld WORLD;
    public static ScaffoldCore.Input coreInput(boolean movement);
    public static AABB getPredictedBoundingBox(double precision);
    public static boolean willFallNextTick(double precision);
    public static Vec3 getNewVector(Scaffold.BlockData lastblockdata);
    /** A point on the given face of the block, jittered around the centre of the face. */ public static Vec3 getNewVector(BlockPos pos, Direction facing);
    /** Whether a block can go on side of pos - 1.8's ItemBlock#canPlaceBlockOnSide, */ public static boolean canPlaceOnSide(BlockPos pos, Direction side);
    /** Right-clicks side of pos with the held item at hitVec, like a player placing a block. */ public static boolean placeBlock(BlockPos pos, Direction side, Vec3 hitVec);
    public static int getBlockSlot();
    public static boolean isUsable(ItemStack stack);
}
```

### arsenic.utils.minecraft.ServerInfo

```java
public class ServerInfo {
    public int onGroundTicks,offGroundTicks;
    public float yaw,pitch;
    public boolean blocking,sprinting;
    public boolean isInGuiServerSide();
    public Listener<EventUpdate.Pre> freezeRotationListener;
    public Listener<EventPacket.OutGoing> windowOutListener;
    public Listener<EventPacket.Incoming.Pre> windowInListener;
    public Listener<EventUpdate.Pre> preListener;
    public Listener<EventPacket.OutGoing> outGoingListener;
}
```

### arsenic.utils.minecraft.TextUtils

```java
public class TextUtils extends UtilityClass {
    /** The text with its colours and styles as legacy § codes, as 1.8's getFormattedText gave it. */ public static String toLegacy(Component component);
    /** The text without any formatting, as 1.8's getUnformattedText gave it. */ public static String plain(Component component);
    public static String stripCodes(String text);
}
```

### arsenic.utils.minecraft.WorldUtils

```java
public class WorldUtils extends UtilityClass {
    /** Every entity the client has loaded, as a list - 1.8's World#loadedEntityList. */ public static java.util.List<net.minecraft.world.entity.Entity> entities();
    /** 1.8's World#getPlayerEntityByName. */ public static net.minecraft.world.entity.player.Player getPlayerByName(String name);
    /** Every block entity in the chunks the client has loaded around the player - 1.8's */ public static List<BlockEntity> loadedBlockEntities();
}
```

### arsenic.utils.render.DrawUtils

```java
public class DrawUtils extends UtilityClass {
    /** Kept for call-site compatibility; the new renderer has no scale-dependent masking. */ public static float overrideScaleFactor;
    /** Text in the vanilla font at any position, with a drop shadow; legacy colour codes work. */ public static void drawStringWithShadow(String text, float x, float y, int color);
    public static void drawString(String text, float x, float y, int color);
    public static void drawString(String text, float x, float y, int color, boolean shadow);
    /** Width of the text in the vanilla font. */ public static int getStringWidth(String text);
    public static void drawRect(float x, float y, float x1, float y1, int color);
    public static void drawGradientRect(float x, float y, float x1, float y1, int top, int bottom);
    /** A plain top to bottom argb gradient. */ public static void drawVerticalGradient(float x1, float y1, float x2, float y2, int top, int bottom);
    public static void drawHorizontalGradientRect(float x, float y, float x1, float y1, int left, int right);
    public static void drawRoundedRect(float x, float y, float x1, float y1, float radius, int color, boolean[] round);
    public static void drawRoundedRect(float x, float y, float x1, float y1, float radius, int color);
    public static void drawRoundedOutline(float x, float y, float x1, float y1, float radius, float borderSize, int color, boolean[] round);
    public static void drawRoundedOutline(float x, float y, float x1, float y1, float radius, float borderSize, int color);
    public static void drawBorderedRoundedRect(float x, float y, float x1, float y1, float radius, float borderSize, int borderC, int insideC, boolean[] round);
    public static void drawBorderedRoundedRect(float x, float y, float x1, float y1, float radius, float borderSize, int borderC, int insideC);
    public static void drawGradientRoundedRect(float x, float y, float x1, float y1, float radius, int bottomLeft, int topLeft, int bottomRight, int topRight);
    public static void drawGradientRound(float x, float y, float width, float height, float radius, Color bottomLeft, Color topLeft, Color bottomRight, Color topRight);
    public static void drawShaderRect(float x, float y, float width, float height, float radius, int c);
    public static void drawShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha);
    /** layers is kept for call-site compatibility; the feather is a single smooth ramp. */ public static void drawShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha, int layers);
    /** One feathered pass: opaque at the element's edge, fading to nothing spread px out. */ public static void drawBlurredShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha, float dropY);
    public static void drawEdgeHighlight(float x1, float y1, float x2, float y2, float radius, int color, int alpha);
    public static void drawGlassRect(float x, float y, float x1, float y1, float radius, int filmColor, int rimColor, float strength);
    public static void drawBorderedCircle(float centrePointX, float centrePointY, float radius, float borderSize, int borderColour, int insideColour);
    public static void drawCircleOutline(float centrePointX, float centrePointY, float radius, float borderSize, int color);
    public static void drawCircle(float centrePointX, float centrePointY, float radius, int color);
    /** Draws a perfect triangle when height == width. A negative height points it upwards. */ public static void drawTriangle(float x1, float y1, float width, float height, int colour);
    /** Draws a whole texture stretched over the rectangle, tinted by color (ARGB). */ public static void drawTexture(Identifier texture, float x, float y, float width, float height, int color);
}
```

### arsenic.utils.render.RenderUtils

```java
public class RenderUtils extends UtilityClass {
    public static int alpha(Color color, int newAlpha);
    public static int withAlpha(int color, int alpha);
    public static Color interpolateColoursColor(Color a, Color b, float f);
    public static int interpolateColours(Color a, Color b, float f);
    public static int interpolateColoursInt(int a, int b, float f);
    /** Textures ship in the jar's assets, so they are addressed by id rather than loaded by hand. */ public static Identifier getResourcePath(String path);
    public static float partialTicks();
    public static Vec3 interpolatedPosition(Entity entity);
    public static AABB interpolatedBox(Entity entity);
    public static void renderBlock(BlockPos blockPos, int color, boolean outline, boolean shade);
    public static void renderBox(AABB box, int color, boolean outline, boolean shade);
    public static void renderBlockFace(BlockPos blockPos, Direction facing, int color, boolean outline, boolean shade);
    public static void drawBoundingBox(AABB box, int color);
    public static void drawShadedBoundingBox(AABB box, int r, int g, int b, int a);
    public static void drawLine(Vec3 from, Vec3 to, int color, float width);
    /** A filled quad through four corners, in order round its edge. */ public static void drawQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color);
    /** A triangle (a quad with its last corner doubled). */ public static void drawTriangle(Vec3 a, Vec3 b, Vec3 c, int color);
    /** A flat ring lying on the ground (in the x/z plane) round centre. */ public static void drawFlatCircle(Vec3 centre, double radius, int color, float width);
    /** A filled disc lying on the ground round centre. */ public static void drawFilledCircle(Vec3 centre, double radius, int color);
    /** A dot of size pixels. */ public static void drawPoint(Vec3 pos, int color, float size);
    /** Text floating at pos, always facing the camera. scale 1 is the size of a nametag. */ public static void drawWorldText(Vec3 pos, String text, int color, float scale);
    /** The camera's position, which world-space effects that face the viewer need. */ public static Vec3 cameraPosition();
    public static void drawLineToEntity(Entity e, int color, float width);
    public static double ticks;
    public static long lastFrame;
    /** The bobbing ring drawn around a target - a solid band fading to transparent, plus a rim. */ public static void drawCircle(Entity entity, double rad, int colored, float alpha);
    /** Same as #drawCircle(Entity, double, int, float); the position is interpolated by the gizmo pass already. */ public static void drawCircle(Entity entity, float partialTicks, double rad, int colored, float alpha);
    /** Draws one of the TargetFx styles round the entity. Call from an EventRenderWorldLast listener. */ public static void drawRing(Entity entity, float partialTicks, double rad, int colored, float alpha, TargetFx.Style style);
    public static float PI2;
    public static float roundToFloat(double d);
    public static Double interpolate(double oldValue, double newValue, double interpolationValue);
}
```

### arsenic.utils.render.RenderContext

```java
public final class RenderContext implements AutoCloseable {
    public static RenderContext begin(GuiGraphicsExtractor graphics);
    public static boolean isActive();
    public static GuiGraphicsExtractor graphics();
    public static float getAlpha();
    public static void setAlpha(float value);
    /** Scales a colour's alpha byte by the current opacity. */ public static int applyAlpha(int color);
    public void close();
}
```

### arsenic.utils.render.WorldToScreen

```java
public class WorldToScreen extends UtilityClass {
    /** A projected point in GUI coordinates, plus its distance along the view direction. */ public record Point(float x, float y, float depth);
    /** Projects world to GUI coordinates, or returns null if it is behind the camera. */ public static Point project(Vec3 world);
    public static boolean isOnScreen(Point point);
}
```

### arsenic.utils.io.Keys

```java
public final class Keys {
    public static int NONE;
    /** Converts an LWJGL2 key code from a 1.8 config into a scancode; unknown keys become unbound. */ public static int fromLwjgl2(int code);
    /** Short upper-case name for a scancode, e.g. R, RIGHT_SHIFT; NONE if unbound. */ public static String getKeyName(int code);
    /** The localised name Minecraft's controls screen would show, e.g. for the GUI. */ public static String getDisplayName(int code);
    /** Parses a key name as typed by a user: r, RIGHT_SHIFT, right.shift, or */ public static int getKeyIndex(String name);
    /** Every named keyboard key, for command auto-completion. */ public static List<String> keyNames();
    /** Whether the physical key or button bound to mapping is held right now, regardless of */ public static boolean isPhysicallyDown(net.minecraft.client.KeyMapping mapping);
    /** Holds a key mapping down or lets it go, as 1.8's KeyBinding.setKeyBindState did. */ public static void setDown(net.minecraft.client.KeyMapping mapping, boolean down);
    public static boolean isKeyDown(int code);
    /** Mouse buttons by the old LWJGL index: 0 left, 1 right, 2 middle. Reads SDL directly because */ public static boolean isMouseDown(int button);
    /** SDL mouse button number (1 left, 2 middle, 3 right) to the old LWJGL index. */ public static int fromSdlButton(int sdlButton);
}
```

### arsenic.utils.lag.LagManager

```java
public final class LagManager {
    public static Predicate<Packet<?>> ALL_PACKETS;
    public static int getPing();
    public static int getPingAsTicks();
    public Listener<EventPacket.OutGoing> onOutgoing;
    public Listener<EventPacket.Incoming.Pre> onIncoming;
    public Listener<EventTick> onTick;
    public static void acquire(Class<?> holderClass, Predicate<Packet<?>> filter);
    public static void acquire(Class<?> holderClass);
    public static void release(Class<?> holderId);
    public static void releaseAndDiscard(Class<?> holderId);
    public static int countBuffered();
    public static boolean isLagging();
    public static Set<Class<?>> getHolders();
    public static boolean isHolding(Class<?> holderId);
    public static void delay(Class<?> holderKey, Predicate<Packet<?>> selector, Function<Packet<?>, Long> delayFunction);
    public static void delay(Class<?> holderKey, Class<?> packetClass, Function<Packet<?>, Long> delayFunction);
    public static void undelay(Class<?> holderKey);
    public static void releaseDelayed(Predicate<Packet<?>> filter);
    public static void releaseDelayedFor(Class<?> holderKey);
    public static void releaseDelayedFor(Class<?> holderKey, Predicate<Packet<?>> filter);
    public static void releaseDelayedChunked(Predicate<Packet<?>> filter, int chunkSize);
    public static void discardDelayed(Predicate<Packet<?>> filter);
    public static int countDelayed(Predicate<Packet<?>> filter);
    public static void delayOutgoing(Class<?> holderKey, Function<Packet<?>, Long> delayFunction);
    public static void delayOutgoing(Class<?> holderKey, Predicate<Packet<?>> selector, Function<Packet<?>, Long> delayFunction);
    public static void undelayOutgoing(Class<?> holderKey);
    public static void releaseDelayedOutgoingFor(Class<?> holderKey);
    public static void releaseDelayedOutgoing(Predicate<Packet<?>> filter);
    public static void releaseDelayedOutgoingChunked(Predicate<Packet<?>> filter, int chunkSize);
    public static void discardDelayedOutgoing(Predicate<Packet<?>> filter);
    public static int countDelayedOutgoing(Predicate<Packet<?>> filter);
    public static void sendPacket(Packet<?> packet);
    public static void receivePacket(Packet<?> packet);
}
```
<!-- API:END -->
