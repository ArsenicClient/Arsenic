# Running Arsenic with JuiceAgent

[JuiceAgent](https://github.com/xiaozhou233/JuiceAgent) is a JVMTI injector for Windows. It loads a jar into a running
JVM and calls a `public static void run()` method, without `-javaagent` and without the attach API. Arsenic can be
started this way with its `arsenic.inject.JuiceEntry` entry point.

## Setup

1. Build Arsenic and rename the jar (for example `build/libs/<name>.jar`) to `Arsenic.jar`.
2. Download a JuiceAgent release (`JuiceAgent_vx.x.x_x64.zip`) and extract it.
3. Put `Arsenic.jar` and [`juiceagent/config.toml`](../juiceagent/config.toml) in the JuiceAgent folder, next to
   `injector.exe`.
4. Start Minecraft 1.8.9 with Forge.
5. Run `injector.exe` with the game's process id. The JarLoader module loads `Arsenic.jar` and calls
   `arsenic.inject.JuiceEntry.run()`. Progress lines are printed to the console with an `[Arsenic]` prefix.

## Entry point settings

| Key | Value | Why |
|-----|-------|-----|
| `JarPath` | `./Arsenic.jar` | the client jar |
| `EntryClass` | `arsenic.inject.JuiceEntry` | the class with `run()` |
| `EntryMethod` | `run` | must be `public static void run()` |

## Limits

- **Forge only.** Vanilla and Lunar Client need a class transformer on the system class loader, which the JuiceAgent
  API does not expose to Java. Those games still work with the Arsenic injector (`Injector` / `Agent`).
- **Hooks come through Forge's class loader.** Classes that load later are hooked by `arsenic.runtime.HookBridge`. The
  classes already loaded when `run()` is called are retransformed once, using the bytes JuiceAgent captures for them.
- JuiceAgent is marked experimental, and some antivirus software flags it.
