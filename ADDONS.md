# Arsenic addons

Addons are modules written as plain `.java` files. Drop them in `.minecraft/Arsenic/addons/` and they are compiled
and loaded when the client starts. No JDK, no jar, no obfuscation step.

Contents: [Quick start](#quick-start) · [Platform facts](#platform-facts) · [Module basics](#module-basics) ·
[Properties](#properties) · [Events](#events) · [Silent rotations](#silent-rotations) · [Utility index](#utility-index) ·
[Cookbook](#cookbook) · [Pitfalls](#pitfalls) · [API reference](#api-reference-generated) ·
[How the loader works](#how-the-obfuscation-problem-is-solved) · [HUD](#hud-elements) · [Packs](#addon-packs) ·
[Default addons and the Addon Manager](#default-addons-and-the-addon-manager)

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

## Platform facts

- **Game:** Minecraft 1.8.9 with Forge. Write against **MCP names** (`mc.thePlayer`, `mc.theWorld`, `playerController`,
  `getHeldItem()`), the same `mcp_stable_20` mappings the client itself is built with. The loader converts them to SRG at
  runtime; you never write `func_`/`field_` names.
- **Java level:** the embedded Eclipse compiler is set to **Java 8** (source, target and compliance). Lambdas, streams,
  `java.util.function`, try-with-resources and diamond work; `var`, `List.of`, records, switch expressions and text
  blocks do not.
- **What you can import:** whatever is on the game's classpath. That is Minecraft and Forge classes, LWJGL
  (`org.lwjgl.*`), Guava, Gson, Log4j, the JDK, and every `arsenic.*` class. Addons run with the game's full
  permissions and there is no sandbox.
- **No mixins and no ASM in addons.** Mixins are applied once at game start, long before an addon is compiled. Addons are
  limited to the public Java surface of Minecraft plus the Arsenic API. For private Minecraft state use the accessor
  interfaces in `arsenic.injection.accessor` by casting (`((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, yaw)`,
  `((IMixinEntityPlayerSP) mc.thePlayer).getLastReportedYaw()`); see the list in the [API reference](#api-reference-generated). A missing
  accessor needs a change to the client, not to the addon.
- **Package:** the template uses the default package. A `package` line is honoured, but the class must still match its file.
- **Reload:** `.addon reload` throws the old module instances away and builds new ones. `static` state is lost; saved
  property values and the enabled flag come back from the config.
- An addon class with the same name as a class already on the classpath wins (this is how the bundled addons can also be
  compiled by the IDE without clashing).

## Module basics

```java
@ModuleInfo(name = "Example", description = "Does a thing", category = ModuleCategory.PLAYER,
            keybind = Keyboard.KEY_NONE, enabled = false, hidden = false, tier = ModuleTier.LEGIT)
```

| Field | Default | Meaning |
|---|---|---|
| `name` | required | Unique module name; also the config key and the name used by `.toggle`, `.set`... |
| `description` | `"placeholder"` | Shown in the ClickGUI. |
| `category` | required | `COMBAT`, `MOVEMENT`, `PLAYER`, `RENDER`, `CLIENT`, `CONFIGS`, `GUI`, `SEARCH`. Use the first four; the rest are client-internal tabs. |
| `keybind` | `0` | An LWJGL key code (`org.lwjgl.input.Keyboard.KEY_*`), `0` for none. Users can rebind in the GUI or with `.bind`. |
| `enabled` | `false` | Initial state when there is no saved config. |
| `hidden` | `false` | Not listed in the HUD module list and gives no enable/disable notification. |
| `tier` | `LEGIT` | ClickGUI toggle the module belongs to: `LEGIT` (always shown), `BLATANT` (shown when the Blatant toggle is on), `DEV` (only exists in the dev jar). |

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
| `boolean allowsTarget(EntityPlayer p)` | `TargetManager`, every time a combat module picks a target | While your module is enabled, only players for which **every enabled module** returns true can be targeted. Default `true`. |
| `boolean isSwappingHotbar()` | `AutoWeapon` | Return `true` while you are changing `inventory.currentItem` over several ticks so AutoWeapon does not switch away from your item. Default `false`. |

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
  `mc.thePlayer` and `mc.theWorld` are `null`. If only one of them is `null` (this happens briefly while changing
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

### Showing a setting only when another has a value: `@PropertyInfo`

```java
@PropertyInfo(reliesOn = "Rotate", value = "true")           // BooleanProperty named "Rotate"
public final DoubleProperty rotSpeed = ...;

@PropertyInfo(reliesOn = "Block", value = "Custom")          // EnumProperty named "Block"
public final DoubleProperty blockId = ...;
```

- `reliesOn` is the **display name** (the first constructor argument) of another property on the same module.
- `value` is compared as a string: for a `BooleanProperty` it is parsed with `Boolean.parseBoolean` (use `"true"` / `"false"`);
  for an `EnumProperty` it is the constant's `name()` and the match is **case-sensitive**.
- Only `BooleanProperty` and `EnumProperty` can be depended on. Chains work: a property whose parent is hidden is hidden too.
- It only controls whether the setting is shown in the ClickGUI. A hidden property still holds and returns its value, so
  read it only where it is relevant.

## Events

Subscribe with `@EventLink public final Listener<EventX> name = event -> { ... };`. All events are in `arsenic.event.impl`.
Only the ones marked **cancellable** extend `CancellableEvent` (`event.cancel()`, `isCancelled()`); the others are
notifications and have no effect when cancelled.

### Order inside one client tick

Derived from the mixins in `arsenic.injection.mixin`:

```
Minecraft.runTick (every 50 ms)
  EventGameLoop, EventRunTick                      start of the tick (identical, pick either)
  EventKey                                         per key press, only when no GUI is open
  ... EntityPlayerSP.onUpdate begins
  EventTick                                        ← the usual place for per-tick logic
  EventMouse.Down / EventMouse.Up                  button edges, buttons 0-2
  EventLiving                                      onLivingUpdate; the silent rotation manager answers here:
    EventSilentRotation, then EventSilentRotation.Post
    EventMovementInput, EventMove, EventJump       during the movement this tick
  EventUpdate.Pre                                  just before the movement packet is built; rotation applied here
  C03/C05/C06 movement packet is sent
  EventUpdate.Post                                 right after the movement packet
  EventTick.Post                                   end of EntityPlayerSP.onUpdate
Frame-rate events, any number per tick:
  EventRender2D, EventRenderWorldLast, EventRenderThirdPerson, EventLook, EventShader
Network (not tied to the tick, see Pitfalls): EventPacket.OutGoing, EventPacket.Incoming.Pre/Post
```

**So: the movement packet is sent after `EventTick`, `EventLiving` and `EventSilentRotation` of the same tick.** Any
action packet you send from those events (a block break, a use-item, an attack) reaches the server *before* this
tick's rotation does. Send it from `EventUpdate.Post`, or from the next tick's `EventTick` (what Nuker does), if it
must be seen with the new rotation. See [Silent rotations](#silent-rotations).

### Catalogue

| Event | Fires | Members | Cancellable |
|---|---|---|---|
| `EventTick` / `EventTick.Post` | start / end of `EntityPlayerSP.onUpdate`, once per tick, only with a player | none | no |
| `EventLiving` | `onLivingUpdate` of the local player | none | no |
| `EventGameLoop`, `EventRunTick` | start of `Minecraft.runTick`, also with no world | none | no |
| `EventUpdate.Pre` / `.Post` | before / after `onUpdateWalkingPlayer`, i.e. around the movement packet | `getX/Y/Z`, `getYaw/Pitch`, `isOnGround` with setters (**Pre only**: values you set go into the packet, then everything is restored for `Post`), `isPre()`, `isPost()` | `Pre`: **yes**, no movement packet is sent at all that tick. `Post`: no effect |
| `EventSilentRotation` / `.Post` | `EventLiving`, see [Silent rotations](#silent-rotations) | yaw, pitch, speed, flags | no |
| `EventMovementInput` | after the keyboard input was read, once per tick | `getSpeed/setSpeed` (forward), `getStrafe/setStrafe`, `isJumping/setJump` | **yes**: zeroes forward and strafe (jump is untouched) |
| `EventMove` | start of `moveFlying` for the local player | `getStrafe/Forward/Friction/Yaw` with setters; the yaw the acceleration is computed with | no |
| `EventJump` | start of `jump()` for any living entity | `getYaw/setYaw`, `getMotion/setMotion` (vertical velocity) | cancelling does **not** stop the jump; it leaves the vanilla jump to run unchanged |
| `EventLook` | whenever the crosshair ray is computed, many times per frame | `getYaw/Pitch` with setters, `hasBeenModified()`; set them to change where the crosshair target is computed from | no |
| `EventAttack` | start of `attackTargetEntityWithCurrentItem` | `getTarget()` | no |
| `EventPacket.OutGoing` | `NetworkManager.sendPacket`, before the packet is written | `getPacket()`, `setPacket(p)` (replace it) | **yes**: the packet is dropped |
| `EventPacket.Incoming.Pre` | a packet arrived, **before** the game processes it | same | **yes**: dropped |
| `EventPacket.Incoming.Post` | after the game processed it | same | no |
| `EventKey` | key pressed with no GUI open | `getKeycode()` | no |
| `EventMouse.Down` / `.Up` | mouse button edge, in `onUpdate` | public `button` field (0 left, 1 right, 2 middle) | no |
| `EventDisplayGuiScreen` | **after** `displayGuiScreen` finished | `getGuiScreen()` | no effect (it is posted afterwards) |
| `EventPlayerJoinWorld` | a player entity was added to the world | `getEntity()`, `getWorld()` | no |
| `EventRender2D` | HUD pass, once per frame | `getSr()` (`ScaledResolution`), `getPartialTicks()` | no |
| `EventRenderWorldLast` | after the world was rendered, once per frame | public `context`, public `partialTicks` | no |
| `EventRenderThirdPerson` | when the player model is drawn | yaw/pitch/prevYaw/prevPitch with setters, `setAccepted(true)` to apply them | no |
| `EventShader.Bloom` / `.Blur` | post-processing passes | `getIterations/setIterations`, `getOffset/setOffset` | no |

Notes on a few of them:

- `EventMovementInput` values are what the player *wanted* (W/A/S/D as -1/0/1, scaled when sneaking). Set them to override.
- `EventUpdate.Pre` values you set are written into the real player and the movement packet, then **restored** before
  `EventUpdate.Post`; this is what makes a rotation "silent". The silent rotation manager already does this, so prefer
  `EventSilentRotation` over setting yaw/pitch here yourself.
- `EventPacket` has `getPacket()` returning a raw `Packet`; cast it (`event.getPacket() instanceof C03PacketPlayer`).

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
| `getRayTrace()` | Block ray (4.5 blocks) along that rotation from the eyes, `null` if it hits nothing. |
| `getRayTraceEntity()` | Same, but the nearest of block and entity. |

### `srm.yaw` / `srm.pitch`

Public fields on the manager. They are updated **after** `EventSilentRotation` has been posted. So inside your
`EventSilentRotation` listener they are the rotation **last sent** (previous tick); from `Post` onwards, and during
`EventTick.Post`/`EventUpdate`, they are the rotation **being sent this tick**; at the next tick's `EventTick` they are
again "last sent". Use them as the "current rotation" to measure how far you still have to turn.

### "Rotate this tick, act next tick"

Packets go out after `EventTick`, so a rotation requested in tick N is on the wire at the end of tick N, and an action
packet sent during tick N before `EventUpdate.Pre` precedes it. Therefore:

1. In `EventSilentRotation`, set the target and the speed.
2. In `EventSilentRotation.Post`, check `event.getRayTrace()` (or your own ray from `getYaw()/getPitch()`) to see whether
   the rotation that will be sent already lines up with the target.
3. If it does, perform the action in `EventUpdate.Post` (same tick, after the rotation packet) or in the next tick's
   `EventTick` (before that tick's rotation packet, but after this tick's has been delivered). Nuker uses the latter:
   it decides in `Post`, acts in the next `EventTick`.

It is wasteful to wait an extra tick when `Post` already shows you on target and you act from `EventUpdate.Post`;
it is wrong to act from `EventTick`/`EventSilentRotation` in the same tick and expect the server to have turned.

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
| Direction vector of a rotation | `((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, yaw)` (note: pitch first) |
| Last rotation/sprint the server saw | `IMixinEntityPlayerSP` (`getLastReportedYaw/Pitch`, `getServerSprintState`) |
| Chat output | `utils.minecraft.PlayerUtils.addWaterMarkedMessageToChat(Object)` (prefixed), `addMessageToChat(String)` |
| Held item checks | `PlayerUtils.isHolding(ItemSword.class)`, `isPlayerHoldingBlocks()`, ... |
| Players / entities nearby | `PlayerUtils.getPlayersWithin(d)`, `getClosestPlayerWithin(d)`, `withinFov(entity, fov)`, `isEntityTeamSameAsPlayer(e)` |
| Click the crosshair target | `PlayerUtils.click()` |
| Inventory clicks | `utils.minecraft.ContainerUtils.click(slot)` (shift-click), `swap(slot, hotbarIndex)`, `drop(slot)`, `getInventoryItems()`, `getBestWeapon()`, `getMostBlocks()`, `getBestTool(ItemPickaxe.class)` ... |
| Movement | `utils.minecraft.MoveUtil` (`isMoving()`, `strafe(speed)`, `getDirection()`, `getSpeed()`, `stop()`) |
| Send / receive a raw packet | `utils.lag.LagManager.sendPacket(p)`, `receivePacket(p)`; hold or delay packets with `acquire/release`, `delayOutgoing` |
| Ping | `LagManager.getPing()` |
| 2D drawing | `utils.render.DrawUtils` (`drawRect`, `drawRoundedRect`, `drawRoundedOutline`, `drawCircle`, `drawVerticalGradient`, `drawShadow`) |
| GL helpers / 3D boxes | `utils.render.RenderUtils` (`color2(rgb, alpha)`, `startBlend/endBlend`, `drawBoundingBox`, `renderBlock`, `drawCircle(entity, ...)`) |
| Modules by class | `Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class)` |
| Accessors for private Minecraft state | `arsenic.injection.accessor.IMixin*` (cast the Minecraft object to the interface) |

## Cookbook

Each recipe is the body of a module (imports omitted). They use only the APIs listed in the API reference and the patterns the
shipped addons (AutoSoup, Nuker, Tracers, AutoHunt) already run. Remember `@RequiresPlayer` on listeners that touch
`mc.thePlayer`.

### Swap a hotbar slot and use an item

```java
private int returnSlot = -1;
private final MSTimer timer = new MSTimer();

@RequiresPlayer @EventLink
public final Listener<EventTick> onTick = event -> {
    if (returnSlot == -1) {
        int slot = findSoup();
        if (slot == -1 || mc.currentScreen != null) return;
        returnSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;                 // the held slot changes client side...
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);   // ...then use whatever is held now
        timer.reset();
    } else if (timer.hasTimeElapsed(500)) {                        // give the server time to finish the use
        if (mc.thePlayer.isUsingItem()) mc.playerController.onStoppedUsingItem(mc.thePlayer);
        mc.thePlayer.inventory.currentItem = returnSlot;
        returnSlot = -1;
    }
};

@Override public boolean isSwappingHotbar() { return returnSlot != -1; }   // keep AutoWeapon away
```

Order matters: assign `currentItem` first, then call `sendUseItem` (it reads the held stack at call time). AutoSoup
spreads the two over separate ticks (`SWITCHED` then `CLICKED`) and restores the slot with `onStoppedUsingItem`, which is the
safe pattern for items with a use duration.

### Click inventory slots

```java
// ContainerPlayer slots: 0 craft out, 1-4 craft grid, 5-8 armour, 9-35 main inventory, 36-44 hotbar
ContainerUtils.click(slot);                    // shift-click the slot (moves it between hotbar and inventory)
ContainerUtils.swap(slot, 2);                  // swap the slot with hotbar index 2 (0-8)
ContainerUtils.drop(slot);                     // drop the whole stack
// or directly, with the open container's window id:
mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, slot, 0, 1, mc.thePlayer);
//                              window id                           slot  button  mode(1=shift)
```

Do clicks from `EventTick`, spaced with an `MSTimer` (AutoSoup clicks one slot every 75 ms while the inventory is open).
`ContainerUtils.*` always address the player's own inventory window; use `windowClick` for chests.

### Raycast, and aim at a block or entity

```java
private Vec3 aim;                                        // the point we want to look at

@RequiresPlayer @EventLink
public final Listener<EventSilentRotation> onRotation = event -> {
    if (aim == null) return;
    float[] r = RotationUtils.rotationsTo(mc.thePlayer.getPositionEyes(1f), aim);
    event.setYaw(r[0]);
    event.setPitch(r[1]);
    event.setSpeed(90);                                  // degrees per tick at most
};

@RequiresPlayer @EventLink
public final Listener<EventSilentRotation.Post> onPost = event -> {
    MovingObjectPosition hit = event.getRayTrace();      // ray along the rotation that is sent this tick
    if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && hit.getBlockPos().equals(targetPos)) { ready = true; }
};
```

For an entity use `event.getRayTraceEntity()` and check `hit.entityHit`. For your own ray (other reach, other origin):

```java
Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
Vec3 look = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, yaw);
Vec3 end  = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);   // blocks only
```

### Send a raw packet

```java
LagManager.sendPacket(new C0APacketAnimation());                        // goes through the normal send queue
mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(slot));   // same thing without the helper
```

Both pass through `EventPacket.OutGoing`, so your own listener will see them; ignore your own packets with a flag if that
matters. To change or drop what the game sends, listen to `EventPacket.OutGoing` and call `setPacket(..)` / `cancel()`.

### Draw in 2D and 3D

```java
@EventLink
public final Listener<EventRender2D> on2d = event -> {          // HUD, screen pixels (scaled GUI coordinates)
    DrawUtils.drawRoundedRect(4, 4, 100, 20, 4, 0x96121212);
    mc.fontRendererObj.drawStringWithShadow("Hello", 8, 9, 0xFFFFFFFF);
};

@RequiresPlayer @EventLink
public final Listener<EventRenderWorldLast> on3d = event -> {   // world space, relative to the camera
    for (EntityPlayer p : mc.theWorld.playerEntities) {
        if (p == mc.thePlayer) continue;
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks - mc.getRenderManager().viewerPosX;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.partialTicks - mc.getRenderManager().viewerPosY;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks - mc.getRenderManager().viewerPosZ;
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_BLEND);
        GL11.glLineWidth(2f); GL11.glColor4f(1f, 0.3f, 0.3f, 1f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(0, mc.thePlayer.getEyeHeight(), 0);      // camera is the origin, so subtract the viewer position
        GL11.glVertex3d(x, y + p.height / 2, z);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
};
```

Always restore the GL state you changed; the next renderer inherits it. See Tracers for the full version. For HUD panels
use `hudElement(...)` so the user can move them.

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
thread) use `mc.addScheduledTask(runnable)` to get back onto the game thread.

## Pitfalls

- **The movement packet is sent after `EventTick`.** A rotation you request is not on the server yet when you send an
  action packet in the same tick before `EventUpdate.Pre`. Act from `EventUpdate.Post` or the next `EventTick`.
- **A rotation request does not mean you arrived.** Speed limits, smoothing and the sensitivity grid mean the rotation
  can take several ticks. Check `EventSilentRotation.Post` before acting.
- **`setBlockUserInput` is per tick.** It resets to `false` each tick, so set it every tick you need it.
- **Exact-class dispatch.** `Listener<EventPacket>` and `Listener<EventUpdate>` never fire. Use the nested classes.
- **Incoming packets arrive on the network thread** (`EventPacket.Incoming.*`). Do not read or modify the world,
  entities or inventory there; set a flag and handle it in `EventTick`, or use `mc.addScheduledTask`.
- **Do not change game state in render events.** `EventRender2D` / `EventRenderWorldLast` run once per *frame*, not per tick,
  at uncapped rates. Draw there, decide in `EventTick`. Restore any GL state you touch.
- **`@RequiresPlayer` only skips when both player and world are null.** Null-check `mc.thePlayer` / `mc.theWorld` in render
  listeners and anywhere a half-loaded world can reach.
- **Optimistic client state.** `sendUseItem` and `windowClick` update the client's copy of the stack before the server
  answers; the server can still refuse, and the stack snaps back. Do not chain decisions on the client stack in the same tick.
- **Assign `inventory.currentItem` before `sendUseItem` / `onPlayerRightClick`**, not after, and put it back afterwards (and
  tell AutoWeapon with `isSwappingHotbar()` while you are away from the player's slot).
- **`EventJump.cancel()` does not block jumping.** To stop a jump use `EventMovementInput.setJump(false)`.
- **`EventDisplayGuiScreen` is posted after the screen opened**, so it cannot veto it.
- **Listener fields are read from your class only.** Listeners declared in a base class you extend are ignored.
- **Properties must be `public` fields** to be shown and saved. `ButtonProperty` and `StringProperty` are never saved.
- **`@PropertyInfo` hides, it does not disable.** A hidden setting keeps its value; ignore it in code when its mode is off.
- **Use `MSTimer`/ticks, not `System.currentTimeMillis()` and not threads** (see the cookbook).
- **Compile errors skip only the one file**, but another addon that depends on that file will fail too. Read the chat output
  of `.addon reload`.
- **Reload drops `static` state and every field of the old module instance**; keep what must survive in properties.

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

**Updates:** on every start, an installed default whose bundled source changed (a new client version) is rewritten in
place, keeping whether it was enabled. The loader remembers a hash of what it installed (`Arsenic/addons/.defaults-hashes`),
so a default you **edited is never overwritten**; to take the new version, delete your copy. A default of unknown origin
(installed before this tracking existed) is replaced after a backup is written next to it (`Name.java.bak`). Addons a
new version adds to an already installed pack appear in it automatically (disabled unless the pack is `autoInstall`).

Click **Addon Manager** in the ClickGUI's bottom right corner: the main card drops the logo and category column and
shows cards in two columns (scrolled like a module category). The **Packs** and **Addons** buttons in the header
switch between all packs and all addons. Left or right click a pack to open its page, which lists the pack's addons
(its Install / Uninstall button handles the whole pack, each addon has its own Install / Uninstall, and a Back button returns to the list). The ClickGUI's
search box filters whichever list is showing, and the bottom right button, now **ClickGUI**, goes back to modules.
Every change reloads the addons straight away.

`registerCommand(new MyCommand())` (see the cookbook), `allowsTarget(player)` and `isSwappingHotbar()` are the hooks
beyond a plain module; see [Module basics](#module-basics).

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
    /** While this module is enabled, TargetManager only accepts targets this returns true for. */ public boolean allowsTarget(EntityPlayer player);
    /** True while the module is moving items around the hotbar, so AutoWeapon keeps its hands off. */ public boolean isSwappingHotbar();
    public List<HudElement> getHudElements();
    public void registerProperties() throws IllegalAccessException;
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
    public int getKeybind();
    public void setKeybind(int keybind);
    public Collection<Property<?>> getContents();
    public List<? extends Property<?>> getProperties();
    public void loadFromJson(JsonObject obj);
    protected void postApplyConfig();
    public JsonObject saveInfoToJson(JsonObject obj);
    public String getJsonKey();
}
```

### arsenic.module.ModuleInfo

```java
public @interface ModuleInfo {
    String name();
    String description() default "placeholder";
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
    public Collection<Module> getContents();
}
```

### arsenic.module.ModuleTier

```java
public enum ModuleTier {
    LEGIT, BLATANT, DEV;
    public String getDisplayName();
}
```

### arsenic.module.property.impl.BooleanProperty

```java
public class BooleanProperty extends SerializableProperty<Boolean> implements IReliable {
    public BooleanProperty(String name, Boolean value);
    public JsonObject saveInfoToJson(JsonObject obj);
    public void loadFromJson(JsonObject obj);
    public Supplier<Boolean> valueCheck(String value);
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
    Supplier<Boolean> valueCheck(String value);
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
    public String getName();
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
    public EventDisplayGuiScreen(GuiScreen guiScreen);
    public GuiScreen getGuiScreen();
    public void setGuiScreen(GuiScreen guiScreen);
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
    public Packet getPacket();
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
    public EventPlayerJoinWorld(EntityPlayer entity, World world);
    public EntityPlayer getEntity();
    public World getWorld();
}
```

### arsenic.event.impl.EventRender2D

```java
public class EventRender2D implements Event {
    public EventRender2D(float partialTicks);
    public EventRender2D(float partialTicks, ScaledResolution sr);
    public ScaledResolution getSr();
    public float getPartialTicks();
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
    public RenderGlobal context;
    public float partialTicks;
    public EventRenderWorldLast(RenderGlobal context, float partialTicks);
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
        public MovingObjectPosition getRayTrace();
        public MovingObjectPosition getRayTraceEntity();
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
public class Arsenic {
    public void init(FMLInitializationEvent event);
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
    public LaunchID getLaunchID();
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
    public HudElement(String label, int x, int y, int width, int height, boolean rightAnchored);
    public void setSize(int width, int height);
    public void reset();
    public void save(JsonObject hud);
    public void load(JsonObject hud);
}
```

### arsenic.injection.accessor.C03PacketPlayerAccessor

```java
public interface C03PacketPlayerAccessor {
    boolean isRotating();
    void setRotating(boolean rotating);
    boolean isMoving();
    void setMoving(boolean moving);
    boolean isOnGround();
    void setOnGround(boolean onGround);
    float getPitch();
    void setPitch(float pitch);
    float getYaw();
    void setYaw(float yaw);
    double getZ();
    void setZ(double z);
    double getY();
    void setY(double y);
    double getX();
    void setX(double x);
}
```

### arsenic.injection.accessor.IMixinEntity

```java
public interface IMixinEntity {
    Vec3 invokeGetVectorForRotation(float p_getVectorForRotation_1_, float p_getVectorForRotation_2_);
}
```

### arsenic.injection.accessor.IMixinEntityPlayerSP

```java
public interface IMixinEntityPlayerSP {
    float getLastReportedYaw();
    float getLastReportedPitch();
    boolean getServerSprintState();
    void setLastReportedYaw(float lastReportedYaw);
    void setLastReportedPitch(float lastReportedPitch);
    void setServerSprintState(boolean serverSprintState);
}
```

### arsenic.injection.accessor.IMixinItemSword

```java
public interface IMixinItemSword extends IWeapon {
    float getAttackDamage();
}
```

### arsenic.injection.accessor.IMixinKeyBinding

```java
public interface IMixinKeyBinding {
}
```

### arsenic.injection.accessor.IMixinMinecraft

```java
public interface IMixinMinecraft {
    Timer getTimer();
    void leftClick();
}
```

### arsenic.injection.accessor.IMixinMovementInputFromOptions

```java
public interface IMixinMovementInputFromOptions {
    GameSettings getGameSettings();
}
```

### arsenic.injection.accessor.IMixinPlayerControllerMp

```java
public interface IMixinPlayerControllerMp {
    NetHandlerPlayClient getNetClientHandler();
    boolean isHittingBlock();
    float getCurBlockDamageMP();
    void setCurBlockDamageMP(float damage);
    int getBlockHitDelay();
    void setBlockHitDelay(int delay);
}
```

### arsenic.injection.accessor.IMixinRenderManager

```java
public interface IMixinRenderManager {
    double getRenderPosX();
    double getRenderPosY();
    double getRenderPosZ();
}
```

### arsenic.injection.accessor.IMixinS12PacketEntityVelocity

```java
public interface IMixinS12PacketEntityVelocity {
    void setMotionX(int motionX);
    void setMotionY(int motionY);
    void setMotionZ(int motionZ);
}
```

### arsenic.injection.accessor.IMixinS14PacketEntity

```java
public interface IMixinS14PacketEntity {
    int getEntityId();
}
```

### arsenic.injection.accessor.IMixinTimer

```java
public interface IMixinTimer {
    float getTicksPerSecond();
}
```

### arsenic.injection.accessor.InboundHandlerTuplePacketListener

```java
public class InboundHandlerTuplePacketListener {
    public InboundHandlerTuplePacketListener(Packet p_i45146_1_, GenericFutureListener<? extends Future<? super Void>>... p_i45146_2_);
}
```

### arsenic.injection.accessor.S03PacketTimeUpdateAccessor

```java
public interface S03PacketTimeUpdateAccessor {
    long getWorldTime();
    void setWorldTime(long worldTime);
}
```

### arsenic.utils.rotations.AimController

```java
public class AimController {
    public float defaultPrediction();
    public void reset();
    public void cancelFlick();
    public void updateDrift();
    public float[] aimAt(Entity e, float ticks);
    public void rotate(EventSilentRotation event, Entity target, float[] rots, RotationMode mode, float minSpeed, float maxSpeed, float budgetTicks);
    public float[] getPredictedRotations(Entity e, float ticks);
    public AxisAlignedBB predictBox(Entity e, float ticks);

    public enum RotationMode {
        Instant, Lazy;
    }
}
```

### arsenic.utils.rotations.RotationUtils

```java
public class RotationUtils extends UtilityClass {
    /** Yaw that faces the horizontal offset (dx, dz). */ public static float yawTo(double dx, double dz);
    /** Pitch that faces the offset (dx, dy, dz); negative looks up. */ public static float pitchTo(double dx, double dy, double dz);
    /** {yaw, pitch} that look from one point at another. */ public static float[] rotationsTo(Vec3 from, Vec3 to);
    public static float[] getRotationsToEntity(EntityLivingBase e);
    public static float[] getCappedRotations(float[] prev, float[] current, float speed);
    public static float[] getPatchedAndCappedRots(float[] prev, float[] current, float speed);
    public static float[] patchGCD(float[] prevRotation, float[] currentRotation);
    public static float getGCD();
    public static Vec3 getBestHitVec(final Entity entity);
    public static double getDistanceToEntityBox(Entity entity);
    public static double getDistanceToEntityBox(Entity target, EntityPlayer from);
    public static float fovFromEntity(Entity en);
    public static float fovToEntity(Entity ent);
    public static float[] getRotations(Vec3 from, Vec3 to);
    public static float[] getPlayerRotationsToVec(Vec3 to);
    public static Vec3 getVec3FromBlockPosAndEnumFacing(BlockPos blockPos, EnumFacing face);
    public static double getDistanceToBlockPos(BlockPos blockPos);
    public static float[] getPlayerRotationsToBlock(BlockPos pos, EnumFacing face);
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
    public static File getArsenicFolderDirAsFile();
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
    /** True while the Hypixel cycle is running, so attacks must wait for its window. */ public boolean isCycling();
    /** Advances the Hypixel cycle by one tick. Three ticks: block, switch the server slot away (which drops the */ public boolean tickHypixel(boolean active);
    /** Legit: really raises the sword for a moment after we are hit (hurt time 10 to 6) while the target is */ public void tickLegit(EntityPlayer target, boolean active);
    /** Puts the server back to normal if the cycle is switched off or interrupted part-way. */ public void abort();
    public void reset();

    public enum Mode {
        None, Legit, Hypixel;
    }
}
```

### arsenic.utils.minecraft.BadPacketsManager

```java
public final class BadPacketsManager {
    public static boolean bad();
    /** Whether any of the selected kinds of packet went out this tick. */ public static boolean bad(boolean slot, boolean attack, boolean swing, boolean block, boolean inventory);
    /** Sends a packet without it counting towards this tick's flags. */ public static void sendSilently(Packet<?> packet);
    public static void reset();
    public Listener<EventPacket.OutGoing> onOutgoing;
}
```

### arsenic.utils.minecraft.ContainerUtils

```java
public class ContainerUtils {
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
    public static <T extends ItemTool> int getBestTool(Class<T> toolClass);
    public static int getBestArmor(int index);
    public static double getEffeciency(ItemStack stack);
    public static boolean canBePlaced(ItemBlock itemBlock);
    public static boolean isInteractable(Block block);
    public static float getEfficiency(final ItemStack itemStack, Block block);
    public static double getDamage(final ItemStack itemStack);
    public static float getPower(ItemStack stack);
    public static ItemStack getItemStack(int i);
    public static int getArmorLevel(final ItemStack itemStack);
    public static int getProtection(final ItemStack itemStack);
    public static boolean isProjectiles(ItemStack itemStackInSlot);

    public static class SlotItem {
        public int slot;
        public ItemStack item;
        public SlotItem(int slot, ItemStack item);
    }
}
```

### arsenic.utils.minecraft.McScaffoldWorld

```java
public final class McScaffoldWorld implements ScaffoldWorld {
    public boolean isAir(int x, int y, int z);
    public boolean isFullCube(int x, int y, int z);
    public boolean canPlaceOnSide(int x, int y, int z, int face);
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
    public static boolean isMoving();
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
    public static boolean isPlayerHoldingBlocks();
    public static boolean isPlayerHoldingSword();
    public static boolean isPlayerHoldingBow();
    public static void addWaterMarkedMessageToChat(Object object);
    public static void addWaterMarkedMessageToChat(Object... object);
    public static boolean playerOverAir();
    public static boolean playerIsEdging(Entity entity);
    public static BlockPos getBlockUnderPlayer();
    public static void click();
    public static EntityPlayer getClosestPlayerWithin(double distance);
    public static boolean isPlayerWearingArmour(EntityPlayer en);
    public static boolean withinFov(Entity entity, float fov);
    public static List<EntityPlayer> getPlayersWithin(double distance);
    public static List<Entity> getEntitysWithin(double distance);
    public static boolean isPlayerNotLoaded();
    public static boolean isEntityTeamSameAsPlayer(EntityLivingBase target);
    public static int getTool(Block block);
    public static float getEfficiency(final ItemStack itemStack, Block block);
}
```

### arsenic.utils.minecraft.ScaffoldUtil

```java
public class ScaffoldUtil extends UtilityClass {
    public static Block block(final double x, double y, double z);
    public static boolean willFallNextTick();
    public static McScaffoldWorld WORLD;
    public static ScaffoldCore.Input coreInput(boolean movement);
    public static AxisAlignedBB getPredictedBoundingBox(double precision);
    public static boolean willFallNextTick(double precision);
    public static Vec3 getNewVector(Scaffold.BlockData lastblockdata);
    /** A point on the given face of the block, jittered around the centre of the face. */ public static Vec3 getNewVector(BlockPos pos, EnumFacing facing);
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

### arsenic.utils.render.DrawUtils

```java
public class DrawUtils extends UtilityClass {
    public static ShaderUtil roundedShader;
    public static float overrideScaleFactor;
    public static void drawRect(float x, float y, float x1, float y1, int color);
    /** A plain top to bottom argb gradient. */ public static void drawVerticalGradient(float x1, float y1, float x2, float y2, int top, int bottom);
    public static void drawCustom(int color, Runnable v);
    public static void drawCustomOutline(int color, float borderWidth, Runnable v);
    public static void drawCustomWithOutline(int fillColour, int borderColour, float borderWidth, Runnable v);
    public static void drawRoundedRect(float x, float y, float x1, float y1, float radius, int color, boolean[] round);
    public static void drawRoundedOutline(float x, float y, float x1, float y1, float radius, float borderSize, int color, boolean[] round);
    public static void drawRoundedOutline(float x, float y, float x1, float y1, float radius, float borderSize, int color);
    public static void drawBorderedRoundedRect(float x, float y, float d, float y1, float radius, float borderSize, int borderC, int insideC, boolean[] round);
    public static void drawRoundedRect(float x, float y, float x1, float y1, float radius, int color);
    public static void drawGradientRoundedRect(float x, float y, float x1, float y1, float radius, int bottomLeft, int topLeft, int bottomRight, int topRight);
    public static void drawShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha);
    public static void drawShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha, int layers);
    public static void drawBlurredShadow(float x1, float y1, float x2, float y2, float radius, float spread, int alpha, float dropY);
    public static void drawEdgeHighlight(float x1, float y1, float x2, float y2, float radius, int color, int alpha);
    public static void drawGlassRect(float x, float y, float x1, float y1, float radius, int filmColor, int rimColor, float strength);
    public static void drawShaderRect(float x, float y, float width, float height, float radius, int c);
    public static void drawGradientRound(float x, float y, float width, float height, float radius, Color bottomLeft, Color topLeft, Color bottomRight, Color topRight);
    public static void drawBorderedRoundedRect(float x, float y, float x1, float y1, float radius, float borderSize, int borderC, int insideC);
    public static void drawBorderedCircle(float centrePointX, float centrePointY, float radius, float borderSize, int borderColour, int insideColour);
    public static void drawCircleOutline(float centrePointX, float centrePointY, float radius, float borderSize, int color);
    public static void drawCircle(float centrePointX, float centrePointY, float radius, int color);
    public static void drawTriangle(float x1, float y1, float width, float height, int colour);
}
```

### arsenic.utils.render.RenderUtils

```java
public class RenderUtils extends UtilityClass {
    public static void setColor(final int color);
    public static void resetColorText();
    public static void resetColor();
    public static void bindTexture(int texture);
    public static int alpha(Color color, int newAlpha);
    public static void setAlphaLimit(float alphaLimit);
    public static boolean captureCoverage;
    public static void applyGuiBlend();
    public static void startBlend();
    public static void endBlend();
    public static ResourceLocation getResourcePath(String s);
    public static Color interpolateColoursColor(Color a, Color b, float f);
    public static int interpolateColours(Color a, Color b, float f);
    public static int interpolateColoursInt(int a, int b, float f);
    public static void renderBlock(BlockPos blockPos, int color, boolean outline, boolean shade);
    public static void renderBox(int x, int y, int z, int color, boolean outline, boolean shade);
    public static void renderBlockFace(BlockPos blockPos, EnumFacing facing, int color, boolean outline, boolean shade);
    public static void drawBoundingBox(AxisAlignedBB abb, float r, float g, float b);
    public static void drawBoundingBox(Vec3 pos, Color color);
    public static void drawBoundingBox(AxisAlignedBB abb, float r, float g, float b, float a);
    public static void drawShadedBoundingBox(AxisAlignedBB abb, int r, int g, int b, int a);
    public static void drawLineToEntity(Entity e, int r, int g, int b, int a, double lw);
    public static void color2(int color, float alpha);
    public static double ticks;
    public static long lastFrame;
    public static void drawCircle(Entity entity, float partialTicks, double rad, int colored, float alpha);
    public static float PI2;
    public static float roundToFloat(double d);
    public static Double interpolate(double oldValue, double newValue, double interpolationValue);
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
