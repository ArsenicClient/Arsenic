# Grim-safe guide for Arsenic

Source: [GrimAnticheat/Grim](https://github.com/GrimAnticheat/Grim) at commit `f5bbe9c`. Check names and
descriptions below are taken from `common/src/main/java/ac/grim/grimac/checks/impl/`. Nothing here has been tested
against Grim in game unless marked **verified**. Items marked **unverified** are reasoned from the Grim source.

## How Grim sees the client

**Packet tick window (Post, PacketOrder*).** Grim treats the movement packet (C03) as the end of a client tick.
Action packets sent after it, before the server's transaction reply comes back, are out of order. Actions covered:
held item change, swing (animation), interact entity, attack, use item, block placement, digging. `Post` flags
these, and `PacketOrder*` checks flag the more specific orders. The screenshot in the last report is this check:
`held item change`, `animation`, `interact entity`, `player digging` all sent after a movement packet.

**Knockback sandwich (AntiKB).** The server wraps each velocity packet between two transactions. Grim treats the
knockback as taken once the client acknowledges the second transaction, and then checks the movement that followed.
A knockback the client applies late leaves a horizontal shortfall that decays by 0.91 per tick (air drag). That is
the `AntiKB` plus `Simulation` pattern from the log.

**Simulation.** Grim simulates each movement tick. Its offset threshold is 0.001, and an offset of 0.1 is an
immediate setback. Any displacement the client never simulated shows up as offset.

**Transactions and ping.** Grim measures latency from transaction round trips (`TransactionOrder`, `TimerLimit`).
Anything that changes when the client answers transactions changes Grim's view of latency.

**Timing.** `Timer`, `NegativeTimer`, `TimerLimit` and `TickTimer` check how many packets arrive per real time. A
burst of held packets released at once can trip them.

**Combat.** `Reach` and `Hitboxes` check the attacker's distance and aim at the target's position (`Hitboxes` is
the one BackTrack tripped). `MultiActions*` flag using an item and attacking in the same tick (`MultiActionsA`
cancels the attack, which is marked experimental). `PacketOrderE` flags changing held slot during an attack, a
right-click or a release.

## Rules for Arsenic code

1. **Do combat actions in the pre-movement tick hook, not in the frame loop.** `EventLiving` (the start of
   `onLivingUpdate`) and `EventSilentRotation` run before the movement packet is sent. Attacks, swaps, jumps and
   use-item calls made from `EventRunTick`, key-state polling or render callbacks can land after the movement packet
   and fall in the `Post` window.
2. **Never change held slot while using an item, attacking, or releasing.** Release the use key first, then swap, then
   attack, in separate ticks if needed (`PacketOrderE`, `MultiActionsA`).
3. **Keep rotations on the GCD.** `RotationUtils.patchGCD` keeps yaw and pitch steps on the mouse GCD. Do not write
   raw rotations to `rotationYaw`/`rotationPitch` outside the silent rotation pipeline (`AimModulo360`,
   `AimDuplicateLook`).
4. **Do not delay incoming packets for combat effects.** Holding incoming packets changes what the client has
   simulated and when it acknowledges transactions. That breaks `AntiKB`, `Simulation` and `TransactionOrder`.
5. **Keep any intentional hold short and bounded.** Holding outgoing packets is a form of lag. Keep it under
   roughly 300 ms, and make sure transactions and positions are released in their original order.
6. **Do not make the client's movement disagree with the server's knockback.** The client must apply every
   velocity packet it receives, on the tick it receives it.

## Arsenic utilities and modules

| Item | Status | Why |
|---|---|---|
| `SilentRotationManager` with `RotationUtils.patchGCD` | Safe (unverified) | Keeps rotations on the GCD and runs before movement. |
| `AimController` / `AimCore` (Lazy mode) | Probably safe (unverified) | Output goes through the silent rotation path, with speed limits. |
| `AimAssist` Additive / Override | Probably safe (unverified) | Mouse input is added to the silent rotation before the GCD patch. |
| `EventSilentRotation.Post` raytraces | Safe | Read-only, no packets. |
| `SyntheticKeys` (new) | Safe | Records presses for the HUD only. It sends no packets. |
| `Hitflick` | Needs testing | Attacks from the silent-rotation tick hook, which is the right place. Its 360°/s flick with STRICT movement fix is unusual. |
| `AutoWeapon` | Mostly safe | Swaps in the tick hook. It must not swap while using an item or attacking. |
| `Clicker` / `KillAura` attacks | Risky | Attacks run from the frame loop and can land after movement (rule 1). |
| `BlockHit` Legit | Risky | Holds the use key from the frame loop, so right-click packets can fall after movement. |
| `BlockHit` TwoSword | Removed | The mode was removed. Its swap-while-blocking pattern is what `PacketOrderE` and `MultiActionsA` flag. |
| `JumpReset` | Probably safe (unverified) | Direct jump in the tick hook. Ground and water are checked first. |
| `KnockbackDelay` | Risky (unverified) | Holds movement and transaction replies for up to 300 ms. Watch `Timer` on release and `TransactionOrder`. |
| `BackTrack` | Partly safe | Lag is capped at 150 ms (Grim's 3-tick interpolation window), and tracking stops once the server position is out of reach (2.95). Hits on a stale position still rely on Grim's interpolation window, so it can still flag `Hitboxes` (unverified). |
| `SprintReset` | Risky | Changes sprint state around attacks. `SprintB`, `SprintC` and `PacketOrderF` check this. |
| `LagManager` incoming delay | **Unsafe for combat** | Breaks the knockback sandwich and transaction order. |
| `LagManager` outgoing hold / `acquire` (blink) | Risky | Bursts trip `Timer` and `TimerLimit`. Keep holds short. |
| `ClickManager` (CPS) | Safe | Accounting only. |

## Open questions

- Whether `Post` also flags attacks made in the frame loop when there is no swap or block in the same window.
  Rule 1 assumes so.
- Whether Grim tolerates a 300 ms outgoing hold on a 1.8 client without `Timer`. This is the main thing to test for
  KnockbackDelay.
- Whether any future two-sword style sequence (release, swap, attack, re-block in one pre-movement tick) passes
  `PacketOrderE` and `MultiActionsA`. The mode was removed, so this is only relevant if it is rebuilt.

## Synthetic key presses

`@SyntheticKey(SyntheticKeys.Key.X)` on a method makes the class transformer add a `SyntheticKeys.press` call at the
start of that method, so the Keystrokes HUD shows it. It only works on a method where every call is one press. Addon
classes go through the same transform before they are defined, so the annotation works in addons too.
