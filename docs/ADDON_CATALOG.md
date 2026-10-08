# Arsenic addon catalog (phase 1)

Researched list of 1.8.9 addon ideas the client does not have yet, grouped into proposed addon packs. This is the deliverable of phase 1. **No addon is implemented here**; later sessions implement it batch by batch (see [Implementation order](#9-implementation-order)).

Date: 2026-10-08. Branch: `claude/arsenic-addon-catalog-gcqlg2`.

---

## Notes for the implementation phases

Read these before writing any addon from this catalog.

- **1.8.9 only.** MCP `stable_20` names, Forge 1.8.9 APIs (`mc.thePlayer`, `mc.theWorld`, `playerController`, `getHeldItem()`), Java 8 (no `var`, no `List.of`, no records). No post-1.8 mechanics: no offhand, shields, attack cooldown, sweep, elytra, totems, crystals, tridents, mending, 1.9+ potions/blocks, 1.9+ packets.
- **Defaults from [ADDONS.md](../ADDONS.md#defaults-every-addon-follows)** apply to every addon: Render and HUD visuals on by default (`BooleanProperty("Render", true)`, `BooleanProperty("HUD", true)`), silent rotation pipeline for anything that rotates, places, breaks, attacks or moves toward something, `MovementFix.SILENT` whenever the player can move, jitter and delays, no action while a GUI is open, reach at most 4.5, honest detection notes.
- **Maintainer override:** `Stasis` (movement freeze) is in the catalog at the maintainer's request. It conflicts with the freeze rule, so it must stay opt-in, off by default and clearly labelled as flag-prone.
- **Exceptions to the "no GUI" default** are listed explicitly in the tables (only `AutoBuy` and `ShopPriceHint` need one). Do not add more without writing them here first.
- **No mixins, no ASM.** Private Minecraft state goes through `arsenic.injection.accessor` by cast. Items marked "Needs client change" need a client change before the addon can be written.
- **Pack layout:** `src/addons/packs/<id>/pack.json` plus `impl/*.java`. Add every new pack id to `src/addons/addons.json` under `"packs"`. Existing packs `bedwars` and `pit` are extended, not replaced.
- **Build:** Gradle compiles `src/addons` as a check. The build must pass after each batch.
- **Stale entry found while inventorying:** `src/addons/addons.json` lists `FastCake` under `"addons"` even though it lives in the `pit` pack and has no loose file. Fix it in the first implementation batch that touches `addons.json`.
- **Visuals:** every addon that targets, plans or acts gets a world highlight (`EventRenderWorldLast`) and a HUD panel (`hudElement`) unless the user says otherwise.

---

## Implementation status

Compiled with `./gradlew compileAddonsJava` on Java 8 (Gradle 4.9) against the real Minecraft 1.8.9 dependencies. Nothing here has been run in game or on a server yet.

- **Batch 1 (12 addons):** `NoBob`, `ItemESP`, `ProjectileESP`, `TNTTimer`, `BlockOverlay` (pack `render`); `HitMarker`, `HitSound`, `HitCounter`, `ArmorStatus`, `HealthWarning`, `EnemyWarning` (pack `hud`); `BedAlarm` (pack `bedwars`).
- **Batch 2 (12 addons):** `GameDetector`, `StaffAlert`, `AutoGG`, `AutoPlay` (pack `hypixel`, new); `BedwarsStatus`, `FinalKillCounter` (pack `bedwars`); `TpsMeter`, `LagbackAlert`, `InvulnTimer`, `ItemCounter`, `SessionStats`, `PingWarning` (pack `hud`).
- **Fixes in this round:**
  - `BedAlarm` finds your bed by itself: after you join or respawn it takes your position as home, picks the bed nearest home within the search radius, and highlights both halves. It gives up after 30 seconds.
  - `HitSound` has options: the hit and kill sounds (confirm, click, orb, pop, anvil, level-up, or none), volume, pitch, and an "Only Players" switch.
  - `AntiBot` (built-in) rewritten. Tab lookups use one snapshot per world tick instead of a scan per entity. A grace period (default 2 s) stops late tab entries being flagged. Singleplayer no longer treats everyone as a bot. Name checks are split: names with a space or an `[NPC]` tag are always bots, looser patterns only count for players missing from the tab list. A new Stationary Checks setting flags players missing from the tab list who have not moved for 15 s (default). `EnemyWarning` now ignores what AntiBot filters.
- **Assumptions to check on Hypixel:**
  - `GameDetector` matches sidebar titles loosely. Unknown titles report `UNKNOWN`.
  - `AutoGG` and `AutoPlay` spot the game end from chat text. The trigger list is editable.
  - `BedwarsStatus` reads team lines with ✔ and ✖ marks.
  - `FinalKillCounter` assumes the killer's name comes before "FINAL KILL".
  - `SessionStats` parses "X was killed by Y".
  - `TpsMeter` estimates TPS from time updates.
- **Known gaps:** NPCs that are in the tab list are not caught by AntiBot yet. A sample name from one would show which rule to add.
- **Batch 3 (10 addons):** `BlockESP`, `DamageParticles`, `JumpCircles`, `PlayerTrail`, `KillEffect`, `WaypointMarkers`, `LightLevel`, `DeathMarker` (pack `render`); `ItemAlerts`, `BlockInfo` (pack `hud`).
  - **Dropped: `Chams`.** The built-in ESP module already has a Chams mode, so no addon is needed.
  - **Dropped: `HackerDetector`.** Removed at the maintainer's request. It used fixed speed and hover thresholds and had no server data behind them, so it would flag knockback and boats.
  - `WaypointMarkers` saves its points in its own setting, so they survive a restart. Commands: `.waypoint add|remove|list|clear`.
  - `LightLevel` and `DeathMarker` draw text with a simple billboard transform; orientation has not been checked in game.
- **Batch 4 (12 addons):** `MiddleClick`, `PanicKey`, `AutoRespawn`, `ChatMacros`, `AutoReconnect`, `AutoFish` (pack `player`); `BackgroundFps`, `AutoScreenshot` (pack `qol`); `ShareCoords`, `PrivateMessageAlert`, `ChatLogger`, `GameProfiles` (pack `hypixel`).
  - `MiddleClick` does nothing when you aim at a player, so the built-in middle-click friend toggle keeps that click. No client change is needed.
  - `PanicKey` turns modules off and remembers which were on. It does not hide the HUD.
  - `AutoReconnect` is the one addon that acts with a GUI open: the disconnect screen.
  - `ShareCoords` sends from a button in the settings, not a key, so nothing is sent by accident.
  - `AutoScreenshot` takes the shot on the next frame and reports the file name in chat.
  - `GameProfiles` applies its lists when GameDetector's game changes, and then leaves the modules alone.
- **Batch 5 (4 of 9 addons):** `CustomFov`, `Zoom` (pack `render`), `AntiBlind`, `Ambience` (pack `render`).
  - Client change: a new `EventFov`, posted from `EntityRenderer.getFOVModifier` through a small mixin. Only the camera's FOV is changed; held items are not.
  - `AntiBlind` removes the blindness and nausea effects on your client each tick. The server still applies them.
  - `Ambience` sets time and clear weather client-side each tick. The server's own time update may flicker for a frame.
  - **Deferred: `Freecam`, `FreeLook`, `Perspective`, `CameraClip`, `AspectRatio`.** They need a camera position and rotation hook in `orientCamera`, and a projection hook for the aspect ratio. Those are a later client change, not an addon.
- **Batch 6 (6 of 8 addons):** `PacketLogger`, `ClickRecorder`, `RotationRecorder` (pack `player`, developer tier); `AntiCrash` (pack `qol`); `BetterChat` (pack `hypixel`); `WeaponCompare` (pack `hud`).
  - `PacketLogger` writes packet names and field values to `packetlogs/`. Movement packets are skipped by default.
  - `ClickRecorder` and `RotationRecorder` write CSV files to `recordings/`, for tuning Clicker and silent rotation against real play.
  - `AntiCrash` drops only incoming custom payloads above a size limit and particle bursts above a count limit.
  - `BetterChat` adds a timestamp and a mention marker in front of the server's own text, keeping its colours. Searchable history needs a chat render hook.
  - **Deferred: `PlayerRankColours`.** Nametags are drawn by the client, so an addon overlay would double them. Needs a client change to the nametag colour.
  - **Deferred: `PartyList`.** Needs a sample of `/party list` output to parse. I checked the surveyed Hypixel clients (FDP, Raven B++, LiquidBounce+ Reborn, LiquidBounce legacy) and none parse party output, so there is no example to copy.
- **Batch 7 (10 of 10 addons):** `KnockbackMeter`, `BowCharge`, `FallDistance`, `Speedometer`, `StateIcons`, `NearbyCount`, `AfkDetector`, `ReachDisplay`, `MemoryUsage` (pack `hud`); `RangeRing` (pack `render`).
  - `KnockbackMeter` and `Speedometer` measure motion on the tick after the event, so they include your own movement.
  - `NearbyCount` uses the team check from PlayerUtils, so on BedWars it depends on the bed colour.
  - `AfkDetector` counts a player still when they don't move, so head-turning still counts as still.
- The remaining batches (8 to 11) are not implemented yet.
## 1. Summary

### Counts per pack

| Pack id | Name | Accepted candidates | Action |
|---|---|---|---|
| `combat` | Ghost Combat | 5 | new |
| `movement-legit` | Movement (legit) | 4 | new |
| `player` | Player and QoL | 9 | new |
| `world` | World and building | 4 | new |
| `render` | Render | 29 | new |
| `hud` | HUD and info | 26 | new |
| `bedwars` | BedWars | 11 | **extend** existing pack |
| `pit` | Pit | 1 | **extend** existing pack |
| `murder` | Murder Mystery | 3 | new |
| `zombies` | Hypixel Zombies | 5 | new |
| `minigames` | Other minigames | 1 | new |
| `skywars` | SkyWars | 2 | new |
| `duels` | Duels and Sumo | 3 | new |
| `hypixel` | Hypixel utils | 11 | new |
| `murder` | Murder Mystery | "Murder Mystery helpers: highlights for the suspected murderer and detective by the items they hold, and an alert when the bow drops." | `textures/items/arrow.png` | false |
| `zombies` | Hypixel Zombies | "Hypixel Zombies helpers: aim assist on the silent pipeline (it never fires on its own), auto heal, auto revive and auto ammo, with mob highlights." | `textures/items/rotten_flesh.png` | false |
| `minigames` | Other minigames | "Other Hypixel minigame helpers, starting with Prop Hunt hider highlights." | `textures/items/nether_star.png` | false |
| `qol` | Misc / QoL | 3 | new |
| **Total** | | **117** | |

Priority split: see the counts in [Section 10](#10-counts-and-priority-split). Ten candidates are P1.

### Surveyed clients (1.8.x only, version confirmed from the source tree)

The index is [3000IQPlay/client-library](https://github.com/3000IQPlay/client-library). I cloned each candidate shallowly into a scratch directory outside the repo, checked the Minecraft version from its build files, then read module names and settings with `git show`/`grep`. Nothing was built or run.

"Seen in" columns in the tables below use these codes.

| Code | Client | Repo | Version evidence |
|---|---|---|---|
| MOON | Moonlight 1.8.9 | randomguy3725/MoonLight | `build.gradle`: `version = '1.8.9'` |
| RAV | Raven B++ | ImCzf233/Raven-B-PlusPlus-opensource | `build.gradle`: 1.8.9, `stable_20` |
| FDP | FDPClient | SkidderMC/FDPClient | `build.gradle`: 1.8.9, `stable_22`; `mcmod.info` 1.8.9 |
| HYD | Hydrogen | zPeanut/Hydrogen | `build.gradle`: `stable_22`; `mcmod.info` 1.8.9 |
| HAN | Hanabi 5.0 (Kotlin) | AckerRun1337/Open-Hanabi-5.0 | `build.gradle`: 1.8.9 natives path, `jvmTarget` 1.8 |
| LBR | LiquidBounce+ Reborn | liquidbounceplusreborn/LiquidbouncePlus-Reborn | README "Minecraft 1.8.9 using Minecraft Forge"; Forge gradle |
| LC | LiquidCat (Kotlin) | CatsDevelopment/LiquidCat | README "Minecraft 1.8.9"; Forge gradle |
| LS | LiquidShadow | woodteam/LiquidShadow | `build.gradle`: 1.8.9; `mcmod.info` 1.8.9 |
| LX | LiquidX | PrahXZ/LiquidX | `gradle.properties`: `forgeVersion=1.8.9-11.15.1.2318-1.8.9` |
| LBL | LiquidBounce, `legacy` branch | CCBlueX/LiquidBounce (branch `legacy`) | `mcmod.info` `mcversion` 1.8.9; README "supporting version 1.8.9" |
| SUMO | Sumo Ghost Client Mod 1.8.9 | pesshown/Sumo-Ghost-Client-Mod-1.8.9 | README title "1.8.9"; no build file, small source (lower confidence) |
| YUR | Yuri | unleg1t/Yuri | `build.gradle`: `version = '1.8.9'`, vanilla `net.minecraft.client.main.Main`; README "Optifine 1.8.9-based". Its anti-cheat claims in the README were not used as evidence. |

Twelve 1.8.x clients with source were surveyed, counting Yuri, which the maintainer asked me to add. The brief asked for 15 to 25. The gap is explained under [Survey coverage](#survey-coverage).

### Survey coverage

- **Version-excluded (not 1.8.x, or 1.9+ mechanics), not used:** Backdoored "1.8.2" (contains AutoTotem, AutoCrystal, ElytraFlight: 1.9+ mechanics), Eso / gondal.club 1.0.8 (Offhand, AutoCrystal, Burrow), Kami5 "1.8" (its `gradle.properties` says Forge 1.12.2 / `stable_39`), Atlas (1.12.2), Kami++ (1.12.2), LavaHack (1.12.2), GameSense (1.12.2), Wurst+ 1 and Wurst+ 2, and WurstMinus (1.12.2), Ananta, Omegahack, NineHack, ChipsHack, ShafferHack, IceHack, Melon+, Carrot, Femhack, SexHack, Creepy, Kettle (1.12.2 `gradle.properties`), Legacy (1.12.2 `mcmod.info`), Moonlight (Gav06, 1.12.2). Fabric or 1.13+ builds: LiquidBounce main branch, Alien, Ares, Argon, Astera, Astralis, Achilles, Agalar, Allusion, Blackout, Caizm, CwHack, DrugHack, GhostBleach, Moonlight Meadows, Shrimp, RavenWeave.
- **Repo gone or private (GitHub returned not-found), about 54 index entries:** Ensemble 1.8.9 (both links), Raven B+, Raven XD, Raven BS, Raven N+, Raven O++, Raven B- (WalmartSolutions), LiquidBounce+ (WYSI), LiquidBounce++, Liquido, Augustus, Ruby, Creeper Client Legacy, Rise, GavhackPlus, Dream, Dactyl, MoonGod, Moon (master7720), NullHack, ApeHax, MeowHack, Aquarius, ImpHax, NyaHack, AbHack, Abyss, AbyssOSS, FishHack, Adequacy, AidsHack, AgloHack, Alien 1.3.8 and Alien-X, CCS, Loust, Halal, Impact+, Nhack4, PigHack, JesusHack, RockEZ, Gerald, BloodHack, Supermacy, Tenacity, MawouteClient, OctoHack, Bobr, ArchWare, Paragon.
- **Binary-only, or README only, so not surveyed (no code was run):** Dope (zip), Pandora (jar), Vape V4 (jar and dll), Catalyst (jar and zip), Nhack3 (jar), Hearse and PikaHack (jar links in the index, not fetched), Novoline (README only), Raven B+++ (README only; its README says 1.8.9, but the repo holds no source), SigmaWare (README only).

Raven B+++ is 1.8.9 by its README, but the repo has no source to read, so it is not counted.

### ToDo.md reconciled against the code

| ToDo item | Status |
|---|---|
| Clutch Module | Exists: built-in `Clutch` (MOVEMENT, BLATANT tier). |
| Timer balance, fastbreak? | `FastBreak` exists (PLAYER, BLATANT). "Timer balance" is a timer exploit, rejected (see [Rejected](#6-rejected-ideas)). |
| savemovekeys | Already covered by built-in `InvMove` (MOVEMENT, DEV tier), which buffers the movement keys while the inventory is open. Moonlight has a `SaveMoveKey` module. Extension idea in [Section 5](#5-improvements-to-existing-modules). |
| antifireball | Exists: addon `AntiFireball` in the `bedwars` pack. |
| chestesp | Exists: built-in `ChestESP`. |

No new module is needed for any ToDo item.

### Existing-modules inventory

**Built-in: 56 modules** (`src/main/java/arsenic/module/impl/**`, `@ModuleInfo` count):

| Folder | Count | Modules |
|---|---|---|
| `blatant` | 1 | KillAura |
| `ghost` | 14 | AimAssist, BackTrack (name "Backtrack"), BlockHit, BowAimbot, Clicker, DoubleHit, LagRange (FakeLag class), SprintReset, NoHitDelay, JumpReset, HitSelect, Hitflick, KnockbackDelay, Reach |
| `movement` | 3 | InvMove (DEV), NoJumpDelay, Sprint |
| `world` | 5 | Breadcrumbs, BridgeAssist, Clutch, Scaffold, TellyScaffold |
| `player` | 15 | AntiAFK, AutoBlockIn, AutoGrinder (DEV), AutoPot, AutoTool, AutoWeapon, Blink, ChatBypass, ChestStealer, FastBreak, Fastplace, InvManager, NameHider (RENDER), RageQuit, Refill |
| `visual` | 13 | Pointers (Arrows class), BedPlates, ChestESP, Esp, FullBright, HUD, Nametags, NoHurtCam, PostProcessing (GUI tab), Radar, RotationView, TargetHUD, Trajectories |
| `client` | 5 | Targets (TargetManager), Cape, AntiBot, Recorder, DiscordRPC |

**Loose addons: 3** (`src/addons/java`): AutoSoup, Nuker, Tracers.

**Pack addons: 8.** `bedwars`: AntiFireball, Breaker. `pit`: AutoHunt, AutoSewerChest, AutoUber, FastCake, FightBot, KillTracker.

Total: 56 + 3 + 8 = **67 modules** in place.

Key facts from reading the built-ins that matter for overlaps:
- `InvManager` already equips the best armour (`bestArmorSlots`), so an armour-equip addon would duplicate it.
- `AutoSoup` already eats golden apples (`ItemAppleGold`) and golden heads, so a separate "AutoGap" would duplicate it.
- `FriendManager` and `FriendCommand` exist, so a friends list would duplicate them.
- `ChamsRenderer` and `GlowRenderer` already exist in `utils/render`.
- `SoundUtils.hitConfirm` already exists, so a hit sound only needs a module.
- `S03PacketTimeUpdateAccessor` and `IMixinTimer` exist, so TPS can be read without a client change.

---

## 2. Surveyed clients and what they contributed

Each entry lists only the module names I actually saw in the source trees. Descriptions are my reading of the names and of the `.describe(...)` settings strings where the client has them.

| Code | Strongest ideas it contributed |
|---|---|
| MOON | FinalKills counter, HackerDetector, SaveMoveKey, AutoRod, AutoPearl, JumpCircles, DashTrail, DamageParticles, ItemESP, AspectRatio |
| RAV | BedwarsOverlay, SumoStats, SumoFences, Duels/Sumo helpers, WTap/STap/ShiftTap, MiddleClick, Freecam, Parkour, ChatLogger, DuelsStats |
| FDP | AutoShop, GuiClicker, AutoTrap, BlockIn, HoleFiller, LiquidFiller, BedDefender, BedProtectionESP, BetterChat, Macros, AutoFish, SnapTap, PacketDebugger |
| HYD | Panic key, AutoType (rejected), BedESP, TTFChat, ChatRect, TriggerBot, Freecam, NoSpeedFOV/NoBowFOV, InventoryWalk |
| HAN | ArmorStatus, LagBackChecker, AutoGG, CustomFov, Crosshair, ArrowESP, TNTTimer, OldAnimation, MurdererFinder, TimeChanger |
| LBR | Skeletal, Trails, JumpCircle, DamageParticle, FreeCam, FreeLook, NoBob, AutoHypixel, AntiStaff, AutoPlay, AutoReconnect |
| LC | BlockESP, Chams, FOV, FreeCam, NoBob, MidClick, ComboDamage, ItemView, CameraClip, TNTESP, AutoReconnect, BindsCommand |
| LS | EnemyWarning, FPSHurtCam, FastStairs, NoBob, ItemESP, Trigger, AntiStaff, AutoPlay, MidClick, AutoFish, CameraClip |
| LX | HealthWarn, HitEffect, DamageParticle, Glint, HurtCam, PerspectiveMod, AutoGG, AntiStaff, AntiVanish, MurderDetector, FollowTargetHud |
| LBL | StaffDetector, GameDetector, AutoDisable, FreeLook, NoBob, PacketDebugger, RotationRecorder, ClickRecorder, AutoRespawn, AutoFish, AutoPlay, ChestAura, ItemPhysics |
| SUMO | Keystrokes overlay, CPS counter, FPS display, KB modifier, aim assist, killaura, eagle, chest stealer, fullbright (small source) |
| YUR | Minigame modules: BedWarsUtility (gear and invisibility reveal), MinigameAim (Zombies aim, auto heal, auto revive, auto ammo; Halloween Simulator farm), BedDefender, Breaker, AutoExtinguish, GUIClicker, AutoSwap, AutoF5, WTap, FreeLook, Chams, BlockESP, Skeletons, TargetESP, SessionInfo, AutoServer (mostly rejected) |

---

## 3. Proposed packs

Icons are 1.8.9 item textures. The bedwars pack already uses `textures/items/bed.png`, which confirms the format. Verify each suggested file exists in the 1.8.9 assets before use.

| Pack id | Name | Description (pack.json) | Suggested icon | autoInstall |
|---|---|---|---|---|
| `combat` | Ghost Combat | "Legit-looking combat helpers: bow, rod and pearl throws, and trapping enemies with blocks, all on the silent rotation pipeline." | `textures/items/bow_standby.png` | false |
| `movement-legit` | Movement (legit) | "Key-press helpers only: edge jumps, auto walk and keyboard-level tap resolution. No speed, no flight, no motion edits. Stasis is a maintainer-requested exception to the no-freeze rule and is opt-in." | `textures/items/leather_boots.png` | false |
| `player` | Player and QoL | "Everyday helpers: middle click item use, chat macros, respawn and reconnect, fishing, panic key and developer recorders." | `textures/items/iron_chestplate.png` | false |
| `world` | World and building | "Placement helpers: fill holes and liquids, put out fire with water and build shapes from templates. All placements go through the silent placement pipeline." | `textures/items/bucket_water.png` | false |
| `render` | Render | "Client-side visuals only: highlights, cosmetic effects, camera tweaks and world markers. Nothing here sends packets." | `textures/items/ender_eye.png` | false |
| `hud` | HUD and info | "Information panels and warnings: hit counts, armour status, health and enemy warnings, lag and TPS readouts, and fight stats." | `textures/items/clock_00.png` | false |
| `bedwars` (extend) | BedWars | Existing pack, keeps `AntiFireball` and `Breaker`; adds bed alarms, team status, shop helpers and resource counters. | `textures/items/bed.png` (unchanged) | true (unchanged) |
| `pit` (extend) | Pit | Existing pack, keeps its six addons; adds a gold and XP rate panel. | `minecraft:dirt` (unchanged) | false (unchanged) |
| `skywars` | SkyWars | "SkyWars helpers: void-fall warning and alive/kill status from the sidebar." | `textures/items/ender_pearl.png` | false |
| `duels` | Duels and Sumo | "Duels and Sumo helpers: edge warning for the ring, round counting and optional duel stats from the Hypixel API." | `textures/items/iron_sword.png` | false |
| `hypixel` | Hypixel utils | "Hypixel helpers: game detection, staff alerts, auto GG and auto play, game-based module profiles, chat tools and party info." | `textures/items/book_normal.png` | false |
| `qol` | Misc / QoL | "Small quality-of-life tools: crash guard, background frame limit and screenshots on events." | `textures/items/redstone.png` | false |

Every new pack defaults to `autoInstall: false`, as the brief asks. Only `bedwars` is `true`, and that is unchanged.

---

## 4. Candidate tables

Columns: **Name** (unique, not clashing with an existing module) · **Category** (COMBAT/MOVEMENT/PLAYER/RENDER) · **What it does** · **Grim approach / risks** · **Seen in** (codes from [Section 1](#surveyed-clients-1-8-x-only-version-confirmed-from-the-source-tree)) · **Needs client change?** · **Complexity** (S/M/L) · **Priority** (P1/P2/P3).

"Seen in: none surveyed" means I looked for the idea across the surveyed clients and did not find it. It is then proposed from Arsenic's needs and is marked so.

### 4.1 Pack `combat` (Ghost Combat), 5 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `AutoBow` | COMBAT | Holds right click to draw a bow and releases only at full charge, on the target the aim solver picked. | Release only from the `EventSilentRotation.Post` ray that hits the target; wait for full charge with jitter. Never release early (FastBow is rejected). | HYD, FDP, LBL, LC, LS | No | M | P2 |
| `AutoRod` | COMBAT | Switches to a fishing rod when a target is in range, casts at it and reels in when the hook connects. | Rod use is an item action: aim with the silent rotation, cast only from the Post ray, delay between casts. | MOON, FDP, LBL | No | M | P3 |
| `AutoPearl` | COMBAT | Throws an ender pearl at a chosen spot near an enemy or a safety point, with an aim solver and a cooldown. | Throw only from the Post ray; jitter the landing point; one throw per cooldown; never with a GUI open. | MOON, FDP, RAV | No | M | P2 |
| `AutoTrap` | COMBAT | Surrounds a nearby enemy with blocks in a 3x3 box, using the AutoBlockIn placement pipeline. | Every placement from `EventSilentRotation.Post` on the ray that hits the intended face; reach 4.5; placement delays; skips faces behind walls. | FDP | No | L | P3 |
| `SelfBlockIn` | COMBAT | Surrounds your own feet with blocks while you stand still, so enemies cannot reach you from the sides. | Same placement pipeline as AutoBlockIn; only when not moving, so no movement fix is needed; delays. | FDP | No | M | P3 |

### 4.2 Pack `movement-legit` (Movement, legit), 4 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `Parkour` | MOVEMENT | While you sprint forward, presses jump at a block edge so you do not walk off it. | Presses the jump key only; never changes velocity; jittered timing; not while sneaking; never in the air. | RAV, LBR, LC, LS, LX, FDP, LBL | No | S | P2 |
| `AutoWalk` | MOVEMENT | Holds the forward key until you toggle it off, optionally with sprint. | Holds a real key only, no motion edits; releases on GUI open and on any manual key press. | FDP, LBR, LC, LS, LBL | No | S | P3 |
| `SnapTap` | MOVEMENT | Resolves opposing A+D and W+S presses the way SOCD keyboards do: the most recent key wins and the older one resumes on release. | Only re-times real presses, injects nothing. Confirmed acceptable by the maintainer. | FDP | No | S | P3 |
| `Stasis` | MOVEMENT | Freezes your motion for up to 45 ticks while you are airborne: zeroes velocity, cancels the position packets in that window, then restores the saved motion. | **Maintainer-requested exception to the no-freeze rule.** Cancelled position packets and held motion break movement simulation, so expect Grim flags and lagbacks. Opt-in only, off by default, never runs with a GUI open, and ends on landing. | YUR (StasisModule) | No | M | P3 |

### 4.3 Pack `player` (Player and QoL), 9 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `MiddleClick` | PLAYER | Middle mouse uses a chosen hotbar item (pearl, rod, golden apple): switches to it, uses it, switches back. | Real click; switch with `isSwappingHotbar()` set, one use per press, restore the slot after; no action with a GUI open. **Conflict:** Arsenic's `TargetManager` already toggles friends on middle click, and Yuri adds friends on middle click. Recommended: item use is opt-in, and the friend toggle moves to shift + middle while it is on. Confirmed fine by the maintainer. | RAV, LBL, LC, LS, LBR, LX, YUR (friend add) | No | S | P2 |
| `ChatMacros` | PLAYER | Binds a key to a chat command or message, for example `/play bedwars_eight_one`. | Sends only on a deliberate key press, rate-limited, never repeats on its own. Keep it manual to stay within server chat rules. | FDP | No | S | P2 |
| `AutoRespawn` | PLAYER | Clicks Respawn after a delay when you die. | Respawn is a normal client request on the death screen; delay jitter; no packets added. | HYD, FDP, LC, LS, LBR, LBL | No | S | P2 |
| `AutoReconnect` | PLAYER | Reconnects to the last server after a kick or disconnect, with a delay and a retry limit. | Opens the normal connect flow after a delay; nothing sent to the old connection. | LC, LS, LX, LBR, LBL | Maybe: needs access to the last server data and connect screen (verify) | M | P2 |
| `AutoFish` | PLAYER | Casts a fishing rod and reels in when your bobber splashes. | Right click with the rod from a fixed aim; reel after the splash is detected from the hook entity; delays; not with a GUI open. | FDP, LS, LBL | No | M | P3 |
| `PanicKey` | PLAYER | One key disables every module and hides the overlays. Pressing it again restores what was on. | Client-side only; no packets. | HYD | No | S | P2 |
| `PacketLogger` | PLAYER (DEV tier) | Logs outgoing and incoming packets with their fields to a file, so you can see what the server gets when you investigate a flag. | Read-only; never changes or drops packets; keep off during matches because of file I/O. | FDP, LBL | No | S | P3 |
| `ClickRecorder` | PLAYER (DEV tier) | Records your real click timings to a file, so Clicker's CPS and delays can be tuned against human data. | Read-only; client-side only. | FDP, LBL | No | S | P3 |
| `RotationRecorder` | PLAYER (DEV tier) | Records your real mouse turn speed and jitter to a file, to compare silent rotation against human motion. | Read-only; reads the camera, never writes a rotation. | LBL | No | S | P3 |

### 4.4 Pack `world` (World and building), 4 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `HoleFiller` | PLAYER | Fills holes in the floor around you with blocks from the hotbar, to keep a clean platform. | Placement pipeline only, from the Post ray; delays; no GUI. | FDP | No | M | P3 |
| `LiquidFiller` | PLAYER | Places blocks into the water or lava in front of you so you can walk or bridge across it. | Same placement pipeline as AutoBlockIn; the face comes from the ray. | FDP, LBL (Liquids) | No | M | P3 |
| `AutoExtinguish` | PLAYER | When you are on fire, places a water bucket at your feet to put it out. | Rotate, then place from the Post ray; one bucket per fire. | FDP, YUR | No | M | P3 |
| `PatternBuilder` | PLAYER | Builds a chosen shape (line, bridge, wall) from a template, one block at a time. | Placement pipeline with delays, never faster than the human delay; no GUI. | FDP | No | L | P3 |

### 4.5 Pack `render` (Render), 29 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `BlockOverlay` | RENDER | Outlines the block your crosshair is on. Uses `mc.objectMouseOver`, so it follows silent rotations. | Client-side only. | MOON, LC, LS, LX, LBL, HAN, LBR | No | S | P3 |
| `ItemESP` | RENDER | Highlights dropped items through walls, coloured by item type. | Render only. | MOON, LS, LX, HYD, LBR, LBL | No | S | P2 |
| `BlockESP` | RENDER | Highlights the block types you choose (beds, diamond or emerald ore, TNT) through walls. | Render only, from your chosen list. Does not reveal unloaded chunks. | LC, LS, LBR, LBL, RAV (XRay), YUR | No | M | P2 |
| `ProjectileESP` | RENDER | Highlights thrown projectiles (fireballs, arrows, pearls) through walls. | Render only. | HAN, LBL, LC, RAV | No | S | P2 |
| `Chams` | RENDER | Draws players and mobs as coloured, see-through models, so they are visible through walls. | Render only; reuses the existing `ChamsRenderer`. | MOON, LC, LS, HYD, HAN, RAV, LBR, LBL, YUR | No | M | P2 |
| `SkeletonESP` | RENDER | Draws bone lines on players. | Render only. | LBR, YUR | Yes: per-bone model positions from the player renderer (not in the addon API) | L | P3 |
| `TrueSight` | RENDER | Draws invisible players as translucent models so they are visible. | Render only; shows nothing they did not already send. | LBL, LC, LBR | Partly: invisible players are not drawn, so the addon has to draw them | M | P3 |
| `Freecam` | RENDER | Moves the camera away from your body. Your real position and packets stay where they are. | Camera offset only; movement and packets unchanged. | RAV, HYD, LC, LS, LBR, LBL | Yes: camera position override | L | P2 |
| `FreeLook` | RENDER | Rotates the camera without turning your body or the silent rotation. | Camera only; the server sees your real rotation. | MOON, LBR, LBL, YUR | Yes: camera rotation decoupled from the silent rotation | M | P2 |
| `Perspective` | RENDER | Third-person options: distance, over-the-shoulder side, and keeping the view from behind. | Render only. | LX, YUR (AutoF5 mode) | Yes for distance and side: `EventRenderThirdPerson` only changes yaw and pitch | S | P3 |
| `CustomFov` | RENDER | Sets a fixed FOV, or a separate FOV while sprinting or drawing a bow. | Render only. | HAN, LC, HYD, LS, LBL | Yes: FOV hook | M | P2 |
| `NoBob` | RENDER | Turns off view bobbing. | Sets the vanilla View Bobbing option; no packets. | LC, LS, LBR, LBL | No | S | P1 |
| `AntiBlind` | RENDER | Removes the blindness and nausea overlays and the fog, so you can see while blinded. | Render only; nothing sent. | LC, LS, LBL, HYD, FDP | Yes: fog and nausea render hook | M | P2 |
| `Crosshair` | RENDER | Custom crosshair (dot, cross, circle) that changes colour when your aim is on a target. | Render only. | HAN, LX | Yes: hide the vanilla crosshair (otherwise both draw) | S | P3 |
| `HeldItemAnimations` | RENDER | Swing, block and equip styles for the first-person hand, including 1.7-style blocking. | Render only, first-person hand. Never changes the swing packet. | HAN, MOON, LC, LS, LBL, HYD | Yes: first-person hand render hook | L | P3 |
| `ItemPhysics` | RENDER | Dropped items spin and bob in 3D instead of lying flat. | Render only. | HAN, LBR, LS, LBL | Partly: needs item model drawing | M | P3 |
| `DamageParticles` | RENDER | Floating damage numbers and hit sparks where your hits land. | Triggers on your hit landing (attack event plus a health change); render only. | MOON, LX, LBR | No | S | P3 |
| `JumpCircles` | RENDER | Draws a ring under you on jump and on landing. | Render only. | MOON, LBR, LBL | No | S | P3 |
| `PlayerTrail` | RENDER | A fading line of past positions behind a moving player. | Render only; uses recorded positions. | MOON, LBR | No | S | P3 |
| `EnchantGlint` | RENDER | Recolours or switches off the enchantment glint on items. | Render only. | MOON, LX, LBR | Yes: glint render hook | M | P3 |
| `TNTTimer` | RENDER | Shows the fuse left on primed TNT and highlights it through walls. | Render only. | HAN, LBL, LC, LS, LBR | No | S | P2 |
| `Ambience` | RENDER | Sets a client-only time of day and weather. | Client-side world state; no packets. | LC, LBL, LBR (Ambience), HAN (TimeChanger), MOON (Atmosphere), YUR | Partly: server time updates may overwrite it (verify) | S | P3 |
| `AspectRatio` | RENDER | Forces a chosen aspect ratio for the 3D view. | Render only. | MOON | Yes: projection matrix hook | M | P3 |
| `Zoom` | RENDER | Hold a key to zoom in with a lower FOV. | Render only. | none surveyed | Yes: FOV hook (shared with CustomFov) | M | P3 |
| `CameraClip` | RENDER | Lets the third-person camera pass through blocks instead of stopping at walls. | Render only. | LC, LS, LBL, HYD | Yes: camera trace hook | M | P3 |
| `RangeRing` | RENDER | Draws a ring on the ground at your reach (or your KillAura range) so you can see what is in range. | Render only. | none surveyed | No | S | P3 |
| `KillEffect` | RENDER | Plays a short burst where a kill you made happens. | Render only; kill detected from entity death after your last hit. | MOON | No | S | P3 |
| `WaypointMarkers` | RENDER | Named world points shown as a beam with a HUD distance. Added with a command. | Client-side only. | none surveyed | No | M | P2 |
| `LightLevel` | RENDER | Shows block light levels on the ground in a radius, so you can see where mobs can spawn. | Render only. | none surveyed | No | M | P3 |

### 4.6 Pack `hud` (HUD and info), 26 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `HitCounter` | RENDER | Counts hits you land and hits you take in the current fight. Resets after a quiet period. | Client-side only; uses attack events and health changes. | RAV (SumoStats), LC (ComboDamage) | No | S | P2 |
| `HitMarker` | RENDER | Flashes a small cross on the crosshair when your hit lands. | Client-side only. | LX (HitEffect) | No | S | P1 |
| `HitSound` | RENDER | Plays a chosen sound when your hit lands, and another on a kill. Reuses `SoundUtils.hitConfirm`. | Client-side sounds only. | none surveyed | No | S | P2 |
| `ArmorStatus` | RENDER | Shows the durability of your armour and held item, plus the armour of the nearest player. | Client-side only. | HAN | No | S | P2 |
| `ItemCounter` | RENDER | Shows how many placeable blocks, arrows, pearls or golden apples you carry. | Client-side only. | RAV | No | S | P2 |
| `HealthWarning` | RENDER | Flashes the screen and plays a sound when your health drops below a limit. | Client-side only. | LX (HealthWarn) | No | S | P1 |
| `EnemyWarning` | RENDER | Warns when a non-friend player comes within a chosen distance, with name and distance. Uses the existing FriendManager. | Client-side only. | LS | No | S | P2 |
| `LagbackAlert` | RENDER | Alerts when the server pulls you back (a lagback) and shows how far. | Reads incoming position packets (`EventPacket.Incoming.Post`); client-side only. | HAN | No | S | P2 |
| `HackerDetector` | RENDER | Lists nearby players whose speed, flight or hit pattern looks cheat-like. Information only, nothing is sent. | Client-side heuristics. Show a confidence and a reason, since false positives are common. | MOON | No | M | P2 |
| `ItemAlerts` | RENDER | Chat or sound alert when chosen items drop near you or are picked up. | Client-side only. | MOON | No | S | P3 |
| `BlockInfo` | RENDER | Shows the name and hardness of the block under the crosshair, and your mining time on it. | Client-side only. | none surveyed | No | S | P3 |
| `DeathMarker` | RENDER | Draws a marker at your last death and shows the distance to it. | Client-side only. | none surveyed | No | S | P3 |
| `SessionStats` | RENDER | Kills, deaths, K/D and time played since you enabled it, counted from chat and events. | Client-side only; chat patterns differ per server. | YUR (SessionInfo) | No | S | P2 |
| `TpsMeter` | RENDER | Shows server TPS from time update packets, so server lag is visible. | Reads time updates through `S03PacketTimeUpdateAccessor`; client-side only. | none surveyed | No | S | P2 |
| `PingWarning` | RENDER | Warns when your ping goes over a limit. | Uses `LagManager.getPing()`; client-side only. | none surveyed | No | S | P3 |
| `MemoryUsage` | RENDER | HUD line with used and maximum JVM memory. | Client-side only. | none surveyed | No | S | P3 |
| `InvulnTimer` | RENDER | Shows the invulnerability ticks left on the entity you just hit (1.8 hurt-resistant time). | Reads `hurtResistantTime` on entities; client-side only. | none surveyed | No | S | P2 |
| `KnockbackMeter` | RENDER | Shows how far the last hit pushed you, and the last one you gave. | Client-side only; read-only motion deltas. | none surveyed | No | M | P3 |
| `BowCharge` | RENDER | Shows the bow charge percentage while drawing. | Client-side only. | none surveyed | No | S | P3 |
| `FallDistance` | RENDER | Shows your current fall distance. | Client-side only. | none surveyed | No | S | P3 |
| `Speedometer` | RENDER | Shows horizontal speed in blocks per second. | Client-side only. | none surveyed | No | S | P3 |
| `StateIcons` | RENDER | Small icons for sprinting, sneaking, blocking and using an item. | Client-side only. | none surveyed | No | S | P3 |
| `NearbyCount` | RENDER | Counts enemies and teammates within a chosen radius. | Client-side only; uses the existing TargetManager rules and team checks. | none surveyed | No | S | P3 |
| `AfkDetector` | RENDER | Marks players who have stood still for a long time (AFK in BedWars or in lobbies). | Client-side only. | none surveyed | No | S | P3 |
| `ReachDisplay` | RENDER | Shows the distance of your last successful hit. | Client-side only. | none surveyed | No | S | P3 |
| `WeaponCompare` | RENDER | Shows the damage of the held weapon next to the best weapon in your inventory. | Client-side only; uses `ContainerUtils.getDamage`. | none surveyed | No | S | P3 |

### 4.7 Pack `bedwars` (extend the existing pack), 11 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `BedAlarm` | RENDER | Alerts (HUD, sound, chat) when your bed is broken or a block next to it changes. | Client-side world watch; no packets. | FDP (BedProtectionESP, BedDefender) | No | S | P1 |
| `BedwarsStatus` | RENDER | Team panel from the sidebar: which beds are still up, and how many teams and players are left. | Client-side; parses the scoreboard, so it depends on Hypixel's sidebar format. | RAV (BedwarsOverlay) | No | M | P1 |
| `FinalKillCounter` | RENDER | Counts your kills and final kills from chat messages. | Client-side; chat parsing. | MOON (FinalKills) | No | S | P2 |
| `GeneratorTimer` | RENDER | Countdown to the next diamond and emerald spawn, read from the generator hologram text. | Client-side. Needs checking on Hypixel before writing. | none surveyed | No | M | P2 |
| `ResourceCounter` | RENDER | Totals of iron, gold, diamond and emerald in your inventory. | Client-side only. | none surveyed | No | S | P3 |
| `ShopPriceHint` | RENDER | When a shop window is open, marks the items you can afford with your current resources. | Render only; does not click. Works in a GUI, so it is the one render-only exception to the no-GUI default. | none surveyed | No | M | P3 |
| `BedDistance` | RENDER | Shows distance to your own bed and to the enemy beds. | Client-side; finds beds near your team spawn by bed colour. | none surveyed | No | M | P3 |
| `AutoBuy` | PLAYER | Buys items from the shop window in a priority list, with human delays. | Clicks inside the shop window with delays. **Needs the GUI exception:** acts only on a shop window you opened, and only after the first confirmed open. | FDP (AutoShop, GuiClicker), YUR (GUIClicker) | No | M | P1 |
| `BedDefender` | COMBAT | Places blocks around your bed when enemies come near. | Placement pipeline only, from the Post ray; delays. | FDP, YUR | No | L | P3 |
| `GearReveal` | RENDER | Shows the gear tier of nearby enemies (stone sword, chain, iron or diamond armour), and reveals invisible players by the armour and items they hold. | Render only; reads visible equipment; invisibility detection is a heuristic, so label it as such. | YUR (BedWarsUtility) | No | M | P2 |
| `ShopMiddleBuy` | PLAYER | Middle-clicks a shop item to buy it, one purchase per press, with the same delays as AutoBuy. | Clicks inside the shop window only when you opened it and pressed the button. Vanilla middle click does nothing in survival, so this sends a normal shop click. Needs the same GUI exception as AutoBuy. | none surveyed (MiddleClick idea extended into shops) | No | S | P2 |

### 4.8 Pack `pit` (extend the existing pack), 1 candidate

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `PitStatsHud` | RENDER | Gold and XP per minute, with totals, read from the Pit sidebar. | Client-side only; scoreboard parsing. | none surveyed (Pit-specific) | No | M | P2 |

### 4.9 Pack `skywars` (SkyWars), 2 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `VoidWarning` | RENDER | Warns while you fall toward the void from height, with the fall distance and time left. | Client-side only; does not change movement (unlike AntiVoid, which is rejected). | none surveyed | No | S | P2 |
| `SkywarsStatus` | RENDER | Alive count and kills from the sidebar. | Client-side; scoreboard parsing. | none surveyed | No | M | P2 |

### 4.10 Pack `duels` (Duels and Sumo), 3 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `SumoEdgeWarning` | RENDER | Warns when you are close to the edge of the Sumo ring, from the arena bounds. | Client-side only. Low confidence on the arena geometry: verify on Hypixel. | RAV (SumoFences, low confidence) | No | M | P3 |
| `DuelsStats` | RENDER | Shows W/L and winstreak of the nearest opponent from the Hypixel API, using your own API key. | External HTTP call with a key you store. Check Hypixel's API rules first. | RAV (DuelsStats) | No | L | P3 |
| `DuelRoundCounter` | RENDER | Counts rounds won and lost in a Duels match from chat. | Client-side; chat parsing. | none surveyed | No | S | P3 |

### 4.11 Pack `hypixel` (Hypixel utils), 11 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `GameDetector` | PLAYER | Reads the sidebar to detect the current Hypixel game (lobby, BedWars, SkyWars, Pit, Duels) and shares it with other addons. | Client-side; scoreboard parsing. Foundation for GameProfiles, AutoGG and AutoPlay. | LBL, FDP | No | M | P1 |
| `StaffAlert` | PLAYER | Alerts (sound, HUD, chat) when a player with a staff rank prefix is in your tab list or joins. Optional: leave the game. | Client-side; uses tab names; no packets. | LBL (StaffDetector), LBR, LS, LX | No | M | P1 |
| `AutoGG` | PLAYER | Sends a chosen message once after each game ends. | One message per game, after a delay; never repeats. | LX, HAN | No | S | P1 |
| `AutoPlay` | PLAYER | After a game ends, waits and sends `/play` for the mode you chose, and shows the queue time. | One command after a delay; no GUI. | MOON, LBR, LBL, LS, LX, FDP | No | S | P1 |
| `GameProfiles` | PLAYER | Turns modules on or off when GameDetector changes game, one profile per game. | Client-side enabling and disabling; no packets. | FDP (AutoDisable), LBL, LX | No | M | P2 |
| `BetterChat` | RENDER | Chat timestamps, highlighting of your name and friends, and a searchable history. | Client-side; chat rendering. | FDP, HYD (ChatRect, TTFChat) | Partly: restyling chat lines needs a chat render hook | M | P2 |
| `PrivateMessageAlert` | PLAYER | Plays a sound and flashes the HUD when you receive a private message. | Client-side only. | none surveyed | No | S | P3 |
| `ChatLogger` | PLAYER | Writes chat to a daily log file in the Arsenic folder. | File write only. Logs contain other players' messages, so keep it opt-in. | RAV | No | S | P3 |
| `PartyList` | RENDER | Parses the output of `/party list` into a HUD panel. | Depends on Hypixel's message format. | none surveyed | No | M | P3 |
| `PlayerRankColours` | RENDER | Colours player names by Hypixel rank in the tab list and nametags. | Client-side only. | none surveyed | Partly: name colour hook for nametags | S | P3 |
| `ShareCoords` | PLAYER | Sends your coordinates to team chat when you press a key. | Manual trigger only. Never automatic. | none surveyed | No | S | P3 |

### 4.12 Pack `qol` (Misc / QoL), 3 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `AntiCrash` | PLAYER | Drops incoming packets known to crash clients (oversized books, malformed signs). | Incoming filter only; nothing outgoing changes. | FDP, LBR, LBL | No | M | P3 |
| `AutoScreenshot` | RENDER | Takes a screenshot when a chosen event happens (a kill, a bed broken, a death). | Client-side only. | none surveyed | No | S | P3 |
| `BackgroundFps` | PLAYER | Lowers the frame limit while the game window is unfocused. | Client-side only. | none surveyed | Partly: check the frame-limit option is reachable | S | P3 |

### 4.13 Pack `murder` (Murder Mystery), 3 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `MurderRoles` | RENDER | Highlights players holding a sword (suspected murderer) in red and players holding a bow (suspected detective) in blue, through walls, with a label. | Client-side only; reads the held items of visible players. Holding a sword or bow is only a hint, so the label says "suspected". | HAN (MurdererFinder), RAV (MurderMystery), HYD (MurderMystery), LX (MurderDetector) | No | S | P2 |
| `GunDropAlert` | RENDER | When a bow lands on the ground (a dropped detective gun), alerts you and marks it. | Client-side; reads dropped item entities. | none surveyed | No | S | P2 |
| `MurderRoleHud` | RENDER | HUD list of alive players with the role hints collected so far in the round. | Client-side; resets each round from chat and the sidebar. | none surveyed | No | M | P3 |

### 4.14 Pack `zombies` (Hypixel Zombies), 5 candidates

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `ZombiesAimAssist` | COMBAT | Aim assist on zombies, aiming at the head with prediction, through the silent pipeline. The player fires; the addon never fires on its own. | Silent rotation with jitter and GCD; act only when the real ray hits the mob; aim only. Yuri's auto-fire default is rejected (see Section 6). | YUR (MinigameAim, Zombies) | No | L | P3 |
| `ZombiesAutoHeal` | PLAYER | Uses a healing item from the hotbar when health drops below a limit. AutoPot covers potions; this covers game items. | Item use with delays; restores the hotbar slot via `isSwappingHotbar()`; never with a GUI open. | YUR (MinigameAim, Auto Heal) | No | S | P2 |
| `ZombiesAutoRevive` | PLAYER | Sneaks and right-clicks a downed teammate in reach, so you can revive them without stopping. | Real sneak: press, wait two ticks, check `isSneaking()`. Acts only from the ray on the downed player, reach 4.5. | YUR (MinigameAim, Auto Revive) | No | M | P3 |
| `ZombiesAutoAmmo` | PLAYER | Switches to an ammo refill item when ammo is low. Off by default, as in Yuri. | Hotbar switch with restore; `isSwappingHotbar()` while held. | YUR (MinigameAim, Auto Ammo) | No | S | P3 |
| `ZombiesMobESP` | RENDER | Highlights zombies and other mobs through walls, with their health. | Render only. | none surveyed | No | S | P2 |

### 4.15 Pack `minigames` (Other minigames), 1 candidate

| Name | Category | What it does | Grim approach / risks | Seen in | Needs client change? | Complexity | Priority |
|---|---|---|---|---|---|---|---|
| `PropHuntESP` | RENDER | Highlights players hiding as props in Prop Hunt, through walls. | Render only. Needs research first: how a disguised prop looks to the client. | LS (ProphuntESP) | No | L | P3 |

---

## 5. Improvements to existing modules

These belong in a module that already exists, so they are not counted in the 105.

1. **KillAura**: add a LegitAura-style mode that only hits when the silent ray is on the target, with jitter. Seen in RAV (LegitAura, KvAura).
2. **SprintReset**: add W-tap, S-tap and shift-tap modes next to the current mode. Seen in RAV (WTap, STap, ShiftTap), HYD (SprintReset) and YUR (WTap). Check the existing `wMode` enum before adding.
3. **Clicker**: add an "only when the target is under the crosshair" option. That covers the trigger-bot idea without a new module. Seen in HYD (TriggerBot) and LS (Trigger). Not a separate `TriggerBot` module, because it would duplicate Clicker plus the aim logic.
4. **AutoSoup** (addon): add a golden-apple priority and an eat-when-hit option. Seen in MOON (AutoGap). `AutoSoup` already eats golden heads and golden apples through `ItemAppleGold`, so this is a mode change, not a new module.
5. **Clutch**: add a water-bucket clutch. Seen in RAV (WaterBucket).
6. **BedPlates**: add a bed highlight mode. Seen in HYD (BedESP).
7. **ESP**: add a Glow mode and a target highlight mode. Seen in MOON (GlowESP, TargetESP) and YUR (TargetESP).
8. **ChestESP**: add storage blocks (furnace, dispenser, hopper). Seen in LBL and HYD (StorageESP).
9. **NoHurtCam**: add an adjustable intensity. Seen in LX (HurtCam).
10. **RageQuit**: add triggers for a staff alert and for health below a limit. Seen in LBL (AutoLeave).
11. **InvMove**: already covers the ToDo `savemovekeys` case for the inventory. Extend it to chat and other containers if wanted. Seen in MOON (SaveMoveKey).
12. **Nametags**: add health and rank colour. Seen in MOON and LC (NameTags).
13. **TargetHUD**: show the enemy's armour (the same data as `ArmorStatus`). Seen in HAN (ArmorStatus).
14. **Nuker** (addon): add a replant option after harvesting crops. Seen in FDP (AutoFarm).
15. **FastBreak**: no change. It already exists as a BLATANT module, which answers the ToDo question "fastbreak?".
16. **AutoTool / AutoWeapon**: add a "switch by crosshair target" mode (tool for blocks, sword for entities). Seen in YUR (AutoSwap).
17. **Perspective** (catalog candidate): add an automatic third-person mode, on combat or scaffold. Seen in YUR (AutoF5).
18. **HUD**: add a custom scoreboard style. Seen in YUR (ScoreboardModule).
19. **PostProcessing**: add motion blur. Seen in HAN (MotionBlur) and YUR (MotionBlurModule).
20. **SessionStats** (catalog candidate): YUR already has a session info panel (SessionInfo). Use it as a layout reference.

---

## 6. Rejected ideas

Grouped by reason. "Seen in" lists the clients where I saw the idea, so later sessions do not re-survey it.

| Idea (seen as) | Reason | Seen in |
|---|---|---|
| Fly, Glide, Jetpack, AirJump, ClipFly, Spider, WallClimb, LadderJump, Ladders, FastClimb, WaterWalk | Breaks movement simulation: flight or vertical movement beyond vanilla. | MOON, LBR, LC, LS, LX, FDP, LBL, YUR |
| Speed, BHop (all AAC/NCP/Verus/Spartan hop variants), Boost, BufferSpeed, IceSpeed, CustomSpeed, Strafe, LegitSpeed, SpeedAntiCornerBump | Speed beyond vanilla. | MOON, LBR, LC, LS, LX, FDP, LBL, RAV, YUR |
| Clip, Phase, NoClip, VClip, Teleport, TeleportAura, ReverseStep, HighJump, SlimeJump, Step, FastStairs, LongJump | Movement simulation (step or phase beyond vanilla, or jumps beyond vanilla). | MOON, LBR, LC, LS, LX, FDP, LBL |
| TargetStrafe, KeepRange, Freeze, Anchor, ItemMagnet, AvoidHazards, AntiDodge | Changes movement input or motion beyond the player's own keys. | MOON, HAN, LX, LBL, FDP |
| NoFall, AlwaysSpoofNofall, MLG-nofall, AntiFall, AntiVoid | Ground or fall-damage spoofing. | MOON, HAN, LBR, LX, LBL, LS, FDP, YUR |
| AntiHunger, AntiCactus, AntiBounce, AntiDesync, Regen, FastUse | Packet or ground spoofing, or item-use speed. | MOON, LBR, LC, LS, LBL, FDP |
| NoSlow, NoSlowBreak, NoWeb, NoFluid, NoSlowdown | Removes movement slowdown, which Grim checks. | RAV, HAN, MOON, LBR, LC, LS, LBL, FDP, YUR |
| Velocity, SuperKnockback, MoreKB, KbModifier, CancelVelocity, NoPush, GlitchVelocity | Knockback modification (Grim velocity check). | MOON, HAN, LX, LBL, LC, SUMO, YUR |
| Criticals (all variants: PacketCritical, MotionCritical, FakeCollideCritical, Hypixel*Critical, MoreCritical) | Fake-jump and ground-spoof crits. | MOON, LC, LS, LX, LBL, LBR, YUR |
| Reach beyond the limit, HitBox (all variants), InfiniteAura, TeleportHit, ForwardTrack, GhostHand, GhostInteract, Ghost, ClickTp, ItemTeleport | Reach or hitbox beyond limits, or interaction through walls. | RAV, HYD, LC, LS, LX, LBR, LBL, FDP |
| Timer, TimerRange, TickBase, MatrixTimerBalance, "Timer balance" (ToDo) | Timer exploits. The rule set rejects timer. | MOON, LBR, LBL, RAV, YUR |
| FastBow | Releases the bow before full charge, which changes damage and timing. | HYD, LC, LS, LBL |
| Disabler, BasicDisabler, LessFlagDisabler, Matrix*, NCP*, Verus*, Spartan*, Polar*, Vulcan*, Intave*, AAC* bypass modes, Hypixel bypass modes, OldGrim/NewGrim | Bypass and disabler modes for anticheats. | RAV, LX, LBR, LBL, LS, FDP, YUR |
| MultiActions, ServerCrasher, Kick, ConsoleSpammer, Spammer, Insult, KillSults, Annoy | Server crash, chat spam or harassment. | MOON, LC, LS, LX, LBR, LBL, YUR (Insults) |
| AutoServer: REGISTER (cracked-server login), AUTO_REPORT (posts fake hack reports about players), AUTO_EXCUSE (chat accusations) | Account abuse and harassment through chat. AUTO_PLAY is already covered by AutoPlay. | YUR (AutoServerModule) |
| Halloween Simulator auto-farm (MinigameAim, Halloween mode) | Automates a minigame's currency farm; bot-like, not a legitimate helper. | YUR |
| ItemDelays ("uses items faster") | Item-use speed, same class as FastUse. | YUR |
| AutoReport, AutoChatGame, AutoType, FakeChat, FakeHud, ChatControl, AutoReply, BookBot | Automated or deceptive chat, and reporting automation. | LX, FDP, HYD, RAV |
| AutoRole, AutoAccount, AutoAuthenticate, AutoLogin, AuthBypass | Account abuse, or credential handling for accounts that Hypixel does not need logins for. | FDP, MOON, LBR, LX, LS |
| PingSpoof, ClientSpoofer, BrandSpoofer, ResourcePackSpoof, FPSSpoofer, PotionSpoof | Spoofing the client to the server. | MOON, FDP, HYD, RAV, LC, LS, LBR, LBL |
| NoRotate, NoRotateSet, NoSlotSet, NoInvClose, NoClose, KeepContainer | Suppresses server packets or state. | MOON, LC, LS, LBL, LBR |
| AimBot (hard lock-on) | Locks aim beyond AimAssist's smoothing, so it looks wrong on the wire. | HYD |
| SumoBot, FightBot for Duels | Full combat and movement automation, with high flag risk. Outside the ghost rules. | RAV |
| Ignite | Sets enemies on fire with flint and steel; no legitimate PvP use. | LBL, LS, FDP |
| SpinBot, Derp, HandDerp | Impossible rotations. | LBR, LBL, LC, FDP |
| Fly-type boat modes (BoatFly, BoatJump) | Flight by another name. | LX |
| Burrow, AutoCrystal, AutoTotem, Offhand, ElytraFlight, AutoWither, Auto32k | 1.9+ mechanics (crystals, totems, offhand, elytra). | Backdoored, Eso (excluded clients) |
| Cosmetic-only: Hat, ChineseHat, AsianHat, FunnyHat, DraginWings, DeadEffect, FireFlies, LineGlyphs, GifTest, SkinDerp, Pentagram, SkinChanger, YuriChat, DDLC hotbar | No gameplay value, so not worth a catalog slot. | MOON, LBR, LC, LX, HAN, YUR |
| SnakeGame, Fun modules (FunCraft, FunnyJump, HeadRotations) | No PvP value. | LBL, LC, FDP |

---

## 7. Unsure, or not included

Not added. Each needs a look at the source before a later session decides.

- **Xray (full ore reveal):** BlockESP covers the legitimate render case (user-chosen blocks). A full reveal was not added.
- **Raven "Terminal", "AutoHeader", "Healing", "AntiShuffle":** the purpose could not be confirmed from the names alone.
- **Raven "SumoFences":** only used to back up SumoEdgeWarning, with low confidence.
- **Hanabi "SnapLook" and "HideAndSeek":** purpose not confirmed.
- **LiquidCat "HeadRotations", Moonlight "Camera" and "Interface", LiquidX "Performance":** purpose not confirmed.
- **Raven B+++ and Novoline:** README only, so no source to read.

### Verify on the server before writing

- `GeneratorTimer`: the hologram text format on Hypixel.
- `BedwarsStatus`, `GameDetector`, `PitStatsHud`, `SkywarsStatus`: the sidebar line format.
- `SumoEdgeWarning`: the arena bounds.
- `TpsMeter`: whether Hypixel sends time updates at a steady rate.
- `AutoBuy`: whether the shop window clicks are accepted with delays.
- `GearReveal`: the invisibility heuristic, which can give false positives.
- `ZombiesAimAssist`: aim only. Confirm it does not read as auto-fire on your server.

### Confirmed by the maintainer

- `SnapTap`, `AutoWalk`, `Parkour` (key-only) and `AutoFish`, `MiddleClick` (item-using) are accepted as fine. They are not gated behind a test step, though the usual check on your server still applies.
- Yuri `MediaInfo` (shows OS media playback): needs a native OS hook, so not added.

---

## 8. Client changes that unlock many addons

Ranked by how many catalog entries each one unblocks.

1. **Camera override event** (position, rotation and FOV, in render): unlocks **Freecam, FreeLook, Perspective, CameraClip, CustomFov, Zoom, AspectRatio** (7). This is the biggest single unlock.
2. **Vanilla render hooks for the first-person hand, fog and nausea overlays, and the enchantment glint**: unlocks **AntiBlind, HeldItemAnimations, EnchantGlint, Crosshair** (4).
3. **Player-model part access** (per-bone positions and item model drawing): unlocks **SkeletonESP, TrueSight, ItemPhysics** (3).
4. **Chat render or restyle hook**: unlocks **BetterChat** (restyling), and makes **PlayerRankColours** complete (2).
5. **Access to the last server data and the connect screen**: unlocks **AutoReconnect** (verify first; may already be reachable).

No client change is needed for the other 98 candidates. The 19 that need one are the ones marked "Yes", "Partly" or "Maybe" in the tables.

---

## 9. Implementation order

Each batch is 8 to 12 addons. Batch 1 is the first to implement. Render and HUD packs come first because they carry no anticheat risk. Batch 5 needs client change 1 first. Batch 9 is the ghost batch and follows the silent pipeline rules in the notes.

| Batch | Theme | Addons | Needs first |
|---|---|---|---|
| 1 | Render and HUD, no risk | NoBob, HitMarker, HitSound, ArmorStatus, HealthWarning, EnemyWarning, ItemESP, ProjectileESP, TNTTimer, BlockOverlay, BedAlarm, HitCounter | Nothing |
| 2 | Hypixel and sidebar parsing | GameDetector, StaffAlert, AutoGG, AutoPlay, BedwarsStatus, FinalKillCounter, TpsMeter, LagbackAlert, InvulnTimer, ItemCounter, SessionStats, PingWarning | Batch 1 (for shared HUD helpers) |
| 3 | Render, world highlights | BlockESP, Chams, DamageParticles, JumpCircles, PlayerTrail, KillEffect, WaypointMarkers, LightLevel, HackerDetector, ItemAlerts, BlockInfo, DeathMarker | Nothing |
| 4 | Player QoL | MiddleClick, PanicKey, AutoRespawn, ChatMacros, AutoReconnect, AutoFish, BackgroundFps, AutoScreenshot, ShareCoords, PrivateMessageAlert, ChatLogger, GameProfiles | Batch 2 (GameProfiles uses GameDetector) |
| 5 | Camera hooks | Freecam, FreeLook, CustomFov, AntiBlind, Perspective, CameraClip, Zoom, AspectRatio, Ambience | Client change 1 (and 2 for AntiBlind) |
| 6 | Recorders and tools | PacketLogger, ClickRecorder, RotationRecorder, AntiCrash, BetterChat, PlayerRankColours, PartyList, WeaponCompare | Client change 4 (for BetterChat restyle) |
| 7 | HUD extras | KnockbackMeter, BowCharge, FallDistance, Speedometer, StateIcons, NearbyCount, AfkDetector, ReachDisplay, MemoryUsage, RangeRing | Batch 1 |
| 8 | Other game packs | VoidWarning, SkywarsStatus, PitStatsHud, SumoEdgeWarning, DuelsStats, DuelRoundCounter, GeneratorTimer, ResourceCounter, ShopPriceHint, BedDistance, AutoBuy, ShopMiddleBuy | Batch 2 (GameDetector). AutoBuy needs the GUI exception. |
| 9 | Ghost and legit actions | AutoBow, AutoPearl, AutoRod, Parkour, AutoWalk, SnapTap, SelfBlockIn, AutoTrap, BedDefender, Stasis | Silent pipeline working (AutoBlockIn reference) |
| 10 | Placement and remaining render | HoleFiller, LiquidFiller, AutoExtinguish, PatternBuilder, HeldItemAnimations, ItemPhysics, Crosshair, EnchantGlint, SkeletonESP, TrueSight | Client changes 2 and 3 for the render items |
| 11 | Minigames and server (Murder Mystery, Zombies, Prop Hunt, BedWars gear) | MurderRoles, GunDropAlert, MurderRoleHud, ZombiesAutoHeal, ZombiesAutoRevive, ZombiesAutoAmmo, ZombiesMobESP, ZombiesAimAssist, PropHuntESP, GearReveal | Silent pipeline (for ZombiesAimAssist and ZombiesAutoRevive); sidebar parsing (for the rest) |

Every candidate appears in exactly one batch. The check is in [Section 11](#11-check-that-each-candidate-appears-once).

---

## 10. Counts and priority split

- **Total accepted:** 117 across 15 packs (13 new packs and 2 extensions).
- **P2 (41) and P3 (66)** are the rest, counted from the tables. Each table has the priority in its last column.
- **P1 (10):** NoBob, HitMarker, HealthWarning, BedAlarm, BedwarsStatus, AutoBuy, GameDetector, StaffAlert, AutoGG, AutoPlay.

Top ten P1 picks, with the reason each one is first:

1. **NoBob**: one vanilla option, zero packets.
2. **HitMarker**: render only, and it makes every fight easier to read.
3. **HealthWarning**: render only, and it protects against low-health deaths.
4. **BedAlarm**: render and chat only, and it is a core BedWars need.
5. **BedwarsStatus**: sidebar parsing, which is the foundation for the bedwars pack.
6. **GameDetector**: the foundation the hypixel pack builds on.
7. **StaffAlert**: client-side, and it protects the user.
8. **AutoGG**: one message per game, no spam.
9. **AutoPlay**: one command per game, after a delay.
10. **AutoBuy**: the most useful BedWars helper, but it needs the GUI exception and server testing.

---

## 11. Check that each candidate appears once

Every candidate name in the tables above is listed exactly once in an implementation batch in [Section 9](#9-implementation-order). The table names and the batch names were compared mechanically when this file was written, and they match.

---

_Generated during phase 1 of the addon catalog session. No Java source was changed._
