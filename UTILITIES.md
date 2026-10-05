# Arsenic utility classes

Look here **before** writing a helper. If you catch yourself writing `System.currentTimeMillis()`,
a colour mix, a clamp/lerp, a "mouse is inside this box" check or a hover fade, one of the classes
below already does it. If the same snippet shows up in three places and nothing here covers it,
add a utility class and list it in this file.

All paths are under `src/main/java/arsenic/`. Classes that `extends UtilityClass` are static-only
and expose a protected `mc`.

---

## Time and animation — `utils/timer`

| Class | Use it for |
|---|---|
| `AnimationTimer(maxMs, Supplier<Boolean> active, TickMode)` | A 0..1 value that eases toward 1 over `maxMs` while the supplier is **true** and back toward 0 while it is false. `getPercent()` advances it and returns the eased value. Use for every open/close, toggle, press, fade-in (`() -> hovered`, `() -> open`). |
| `HoverAnimation` | Hover fade in one line: `anim.update(isOver)` returns the eased 0..1. Wraps an `AnimationTimer` so you don't write a supplier + field for every hover. Defaults to `UITheme.DUR_HOVER`, `TickMode.CUBIC`. |
| `TickMode` | Easing curves: `SINE`, `LINEAR`, `ROOT`, `SQR`, `CUBIC` (ease-out), `EXPO`, `BACK`. `toSmoothPercent(x)`. Use instead of hand-written `1 - (1-t)^3`. `TickMode.clamped(x)` clamps to 0..1 first. |
| `MSTimer` | Stopwatch: `reset()`, `hasTimeElapsed(ms[, reset])`, `finished(ms)` (>=), `getTime()`, `setTime(0)`. `MSTimer.expired()` starts already elapsed, for "last happened" fields that begin unset. Use instead of storing `lastX = System.currentTimeMillis()` and subtracting. |
| `Timer` | Cooldown: `start()`, `hasFinished()`, `firstFinish()` (true once per start), `getTimeLeft()`, `getElapsedTime()`. |
| `TimeUtils` | `blink(halfPeriodMs)` for text-cursor blinking. |
| `FrameClock` | Per-frame delta time for "chase the target" smoothing: `clock.tick()` returns seconds since last call (capped at 0.1). `clock.approach(current, target, rate)` is the frame-rate-independent `current += (target-current)*min(1, dt*rate)`. |

**Rule:** no raw `System.currentTimeMillis()` / `nanoTime()` for timing in GUI, HUD or module code.
Use `MSTimer`, `Timer`, `AnimationTimer`, `HoverAnimation` or `FrameClock`.

## Maths and colour — `utils/java`

| Class | Contents |
|---|---|
| `MathUtils` | `clamp(int/float/double, lo, hi)`, `clamp01`, `lerp(a, b, t)`, `inside(mx, my, x1, y1, x2, y2)` (mouse/point-in-rect, inclusive), `insideSized(mx, my, x, y, w, h)` (exclusive on the far edge, vanilla `GuiButton` semantics). |
| `ColorUtils` | `withAlpha(rgb, a)`, `alpha(argb, 0..1)`, `mixRgb(a, b, t)` (24-bit result), `mixArgb(a, b, t)` (alpha mixed too), `luminance(rgb)`, `getColor`/`setColor` channel access, `getThemeRainbowColor`, `getRainbow`. |
| `JavaUtils` | `concat` arrays, `getRandom(min, max)`, `limit(v, min, max)` (double clamp; prefer `MathUtils.clamp`), `autoCompleteHelper` for command tab-complete. |
| `SoundUtils` | UI/feedback sounds: `playSound`, `chordEnable/Disable/Click/...`, `hitConfirm`, `slide`, `tick`. |
| `FileUtils` | `getArsenicFolderDirAsFile/String`, `readInputStream`. |
| `PlayerInfo` | Data holder: last reported yaw/pitch/sprint state. |
| `UtilityClass` | Base for static helpers (gives `mc`). |

Also: `render/RenderUtils.interpolateColoursInt/Color` (alpha-forced-opaque RGB blend, used by
`UITheme.mix`), `gui/click/UITheme` (theme-aware `alpha`, `fade`, `mix`, `readableOn`,
animation durations `DUR_HOVER/PRESS/TOGGLE/EXPAND`), `gui/themes/ThemeManager` (current theme colours).

## Rendering — `utils/render`

| Class | Use it for |
|---|---|
| `DrawUtils` | All 2D drawing: `drawRect`, `drawRoundedRect`, `drawRoundedOutline`, `drawBorderedRoundedRect`, gradients, `drawShadow`, `drawBlurredShadow`, `drawGlassRect`, circles, triangle. **`drawVerticalGradient(x1,y1,x2,y2,top,bottom)`** for plain top→bottom ARGB gradients. |
| `RenderUtils` | GL state (`setColor`, `resetColor`, `startBlend/endBlend`), 3D boxes/blocks/faces/circles, textures (`getResourcePath`), colour interpolation, `interpolate`. |
| `ScissorUtils` | Nested scissor stack: `subScissor`, `endSubScissor`, `resetScissor`. Use for clipped scroll areas. |
| `GlowRenderer`, `ChamsRenderer` | Entity glow / chams passes. |
| `shader/ShaderUtil` | Load/cache fragment shaders, uniforms, `drawQuads`, framebuffers, `renderFullscreen`. |
| `shader/KawaseBlur`, `shader/KawaseBloom` | Blur / bloom passes. |
| `PosInfo`, `RenderInfo` | Small carriers passed through GUI/font draw calls. |

## Fonts — `utils/font`

`Fonts` (font instances), `TTFontRenderer`, `FontRendererExtension` (`drawString`, `drawStringWithShadow`,
`drawWrappingString`, width/height, scale + `CENTREX`/`CENTREY` modifiers). Get one via
`((IFontRenderer) mc.fontRendererObj).getFontRendererExtension()` or the GUI's `RenderInfo.getFr()`.

## Minecraft helpers — `utils/minecraft`

| Class | Contents |
|---|---|
| `PlayerUtils` | Chat messages (`addWaterMarkedMessageToChat`), held-item checks, `click()`, nearby players/entities, FOV checks, team checks, `getTool`. |
| `MoveUtil` | `isMoving`, `strafe`, `getDirection`, `getBaseSpeed`, `getSpeed`, `stop`, speed constants. |
| `ContainerUtils` | Inventory clicks/drops/swaps, "best weapon/tool/armour/blocks" slot finders, item scoring. |
| `ScaffoldUtil` | Block lookup, fall prediction, scaffold input builder, block slot. |
| `ServerInfo` | Server/GUI-state info. |
| `McScaffoldWorld` | Adapter from the Minecraft world to `ScaffoldWorld`. |

## Rotations and aim — `utils/rotations`, `utils/aimcore`

`RotationUtils` (rotations to entity/vec/block, GCD patching, yaw/pitch diffs, `updateRotation`),
`SilentRotationManager`, `AimController`, `aimcore/AimCore`, `aimcore/TargetPicker`.

## Packets and lag — `utils/lag`

`LagManager` (hold/delay/release packets by holder class, ping), `TimedPacket`.

## Cores / simulators — `utils/scaffoldcore`, `utils/botcore`, `utils/bot`

Pure-logic cores kept free of Minecraft types so they can be benchmarked in `src/sim`:
`ScaffoldCore`, `ClutchCore`, `LaneNudge`, `SneakPresses`, `Bot`, `Planner`, `Terrain`, `BotDriver`, …
Measure before changing them. These must stay free of Minecraft types (they are compiled outside gradle by `src/sim`), so they deliberately keep private `clamp`/`lerp` helpers instead of using `MathUtils` (which extends `UtilityClass`).

## Misc

| Class | Contents |
|---|---|
| `io/MouseButton` | Mouse button code ↔ enum. |
| `discord/DiscordRPCManager` | Discord rich presence. |
| `interfaces/*` | `IConfig`, `ISerializable`, `IContainer`, `IFontRenderer`, … shared contracts. |
| `command/CommandUtils` | Property lookup/describe/apply helpers shared by the `.set`, `.config` etc. commands. |

---

## Quick "don't do this" table

| If you wrote… | Use instead |
|---|---|
| `long last = System.currentTimeMillis(); … now - last > x` | `MSTimer` / `Timer` |
| `hover += ((hovered?1:0) - hover) * Math.min(1, dt*14)` with a hand-rolled `dt` | `HoverAnimation` (or `AnimationTimer`) |
| `1f - (1f-t)*(1f-t)*(1f-t)` | `TickMode.CUBIC` |
| `Math.max(0, Math.min(1, v))` | `MathUtils.clamp01` / `clamp` |
| `a + (b-a)*t` | `MathUtils.lerp` |
| private `mix(int,int,float)` / `mixArgb` | `ColorUtils.mixRgb` / `mixArgb` |
| `(alpha<<24) \| (rgb & 0xFFFFFF)` | `ColorUtils.withAlpha` |
| `mouseX >= x && mouseX <= x2 && mouseY >= y && mouseY <= y2` | `MathUtils.inside` |
| a Tessellator quad for a vertical gradient | `DrawUtils.drawVerticalGradient` |

## Known intentional exceptions

Raw `System.currentTimeMillis()` / `nanoTime()` is still used where a timer object does not fit:
timestamps stored per data entry (BackTrack entries, Breadcrumbs, Scaffold placements, AutoSewerChest,
TargetHUD damage flash), search deadlines/budgets (AutoBlockIn BFS, Planner), shader time uniforms and
the `ColorUtils` rainbow phase. The pure cores in `utils/botcore`, `utils/scaffoldcore`, `utils/aimcore`
keep their own small maths helpers (see above).
