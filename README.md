# Horizon Java Bridge — my fork

I forked [iaxivers' Horizon Java Bridge](https://github.com/iaxivers/horizon-java-bridge) and added my own compatibility, UI and stability changes. The bridge runs Fabric 26.3 mods through a Java VM while Horizon continues to run the game in Rust. My work builds on the original bridge and its contributors.

**[Download my Windows playtest](https://github.com/badmasti2/horizon-java-bridge/releases/tag/shady-playtest-2026-11-08)** · **[Download this version's source](https://github.com/badmasti2/horizon-java-bridge/archive/refs/heads/my-version.zip)** · **[Read the original bridge documentation](https://github.com/iaxivers/horizon-java-bridge#readme)**

This is a community beta. My Windows package targets **Minecraft Java 26.3**, **Horizon `nightly-2026-11-04-3fb4c31`** and **Windows x64**.

## What I changed

### Fabric startup and mod callbacks

I made the Java client mirror initialize mod-added fields and missing executor queues before startup handlers use them. I also set the game thread before client entrypoints, kept queued tasks when client work moved between threads, and changed options loading so callbacks could not reach a partly built GUI. These changes addressed startup failures I reproduced with Essential 1.5.2.

I expanded the [Mixin callback dispatcher](crates/horizon-jvm/java/platform/dev/horizonlab/platform/HorizonHooks.java) to handle callbacks that receive only `CallbackInfo` or `CallbackInfoReturnable`, even when the target method has arguments. Cancellation and changed return values now pass through the bridge for those handlers. When one injection names several points such as `HEAD` and `RETURN`, I record each distinct point. I still exclude handlers that require captured local variables when the bridge cannot supply a real vanilla frame.

### Modded menus, controls and HUD

I added a [Java title and pause menu layer](crates/horizon-client/src/game/java_title.rs) so mod screens can draw over Horizon's menus and receive mouse, keyboard, scroll and GUI scale information. I changed navigation handling so a mod popup can block clicks from reaching the Horizon menu underneath it. I also kept Escape and the normal singleplayer menu route working in the isolated UI checks.

I increased captured screen images to **up to 2048 pixels per axis** and gave large images their own atlas slots. The [screen renderer](crates/horizon-client/src/game/java_screen.rs) now moves frame operations instead of cloning them, caches decoded item data for repeated UI draws, and waits for a matching frame before replacing the native hotbar with a modded one. I moved local mod HUD rendering onto its own frame schedule, so it can refresh between game ticks, and reduced repeated HUD extraction and image work.

I also added native chat heads that use player skin data and corrected inventory wheel transfers so one wheel step moves one item. These are Horizon-side UI features in my build; they do not mean every Fabric mod with similar features works through the bridge.

### Graphics and resource stability

I fixed GPU resource cleanup at frame boundaries, including buffers still referenced by a recorded render pass. I added uniform padding that keeps adjacent data separate and asynchronous readback for modded UI textures. In an isolated 1920×1080 run, the shared native buffers that had grown each frame stabilized below 1 MiB after the cleanup change. I added [three GPU lifetime regression tests](crates/horizon-jvm/src/gpu/mod.rs).

I also made Horizon discover optional resource packs embedded inside Fabric mod JARs under `resourcepacks/`, and improved how the Java mirror uses the launcher's signed skin profile for the local player.

### Entering and leaving worlds

I filled more of the Java player and connection mirror before mod packet hooks run: per-connection Mixin fields, inventory and player data, command trees, a vanilla delta timer, and selected-slot and food state. I cleaned up Java HUD and screen state when leaving an integrated world or joining a different server, so old overlays do not carry into the next connection. I also addressed the reproduced chunk-cache and null-encoder failures during the isolated Essential world-entry checks.

The implementation notes are in [bridge tasks](docs/java-bridge-tasks.md) and the [technical bridge guide](docs/java-bridge.md). The main source changes are on the [`my-version` branch](https://github.com/badmasti2/horizon-java-bridge/tree/my-version).

## What I tested

I ran **16 focused synthetic-mod tests** against the production callback dispatcher using Fabric Loader, Mixin, ASM and Gson. They cover callback signatures, cancellation, return values, multiple injection points, partially initialized fields, executor queues and thread changes. I also compiled the Java platform against the player's 26.3 JAR and built the Rust client with Java mods enabled.

In isolated playtests, Essential 1.5.2 started, rendered its menu and settings, and reached the tested world-entry path without the reproduced bridge or GPU validation errors. I checked popup click blocking, Escape, ordinary singleplayer navigation, one-item wheel transfer and chat skin display. These checks are recorded in the development notes; I have not rerun the whole suite for this README update.

## What still needs work

- Essential's online and social authentication is not complete in the Java mirror.
- I have not verified full modpacks or every Fabric mod in game. The focused hook tests establish callback behavior, not universal mod compatibility.
- The downloadable Windows executable was built shortly before the last local source fixes. The playable ZIP and the latest source commit are **not an exact build pair**.

## A note on using this fork

I made this as a fun project. It gives people another way to try mods in Horizon, which I think is better than having no bridge at all, but it is not perfect and there are still plenty of bugs. If the mod you want has not been ported to Horizon, I recommend using normal Minecraft Java Edition for regular play. This bridge is not a viable substitute for it, especially with complicated UI-heavy mods.

Some simpler things do work: I added a native Chat Heads-style feature, and Simple Voice Chat has worked in bridge tests, along with other mods. I put particular effort into making Essential feel as close to the normal Java experience as I could. Its menus and settings reached my isolated playtests, while its online and social features still need work.

What i tested that works perfectly pretty much

Essentials mod: Everything works exept for world sharing and cosmetics but who used those anyways

Mod menu: it works everything works 1 bug that if you scroll down then up the photos on the mods dissaper

Apple Skin: works perfecly

## What semi works

Simple voice chat: The ui works not sure if it actully works

Xeros minimap: works but its buggy low fps and you can open the full map but its laggy and gives errors in chat I did try to optimize it so its not unusable but not super good

Uku,s armor Hud: It works but it flashes off and on when you change hotbar slot

Mouse tweaks: Works sorta i cant tell ikl

Continuity: it works on glass but im not sure about other things

What doesnt work 

Things that optimize the game

Sodium

Anything that changes sounds like:

presence footsteps and
Sound phsyics reimagened

Shulkerbox tooltip

remove loading screen

Visuality

and any zoom mods

I wont be working on this any farther but feel free to edit this yourself But if your going to edit it with ai be mindfull it Burns through tokens

## Download and run my Windows build

Download and extract the entire [Windows ZIP](https://github.com/badmasti2/horizon-java-bridge/releases/download/shady-playtest-2026-11-08/Horizon-Fabric-Bridge-Shady-Windows-x64.zip). You need a Microsoft account that owns Minecraft: Java Edition, Minecraft Java 26.3 launched once in the official launcher, and Java 25 for Fabric mods. The patched launcher can provision Java after sign-in.

1. Run `start-patched-launcher.vbs`, sign in and prepare Horizon `nightly-2026-11-04-3fb4c31`.
2. Close any running Horizon game. Open PowerShell in the extracted folder and run:

   ```powershell
   $env:HORIZON_LAUNCHER_NO_SELF_UPDATE = '1'
   .\horizon-launcher.exe java-mods use-build .\horizon-client.exe --version nightly-2026-11-04-3fb4c31
   ```

3. Open `start-patched-launcher.vbs` again and play that nightly. Put your own Fabric 26.3 mod JARs in `%APPDATA%\horizon\mods-java`.

The ZIP includes my test client, the patched launcher, instructions and license notices. It does not include Minecraft files, a Minecraft account, Java, mods or saves.

## Build my source

I keep the source on the default `my-version` branch. To build it yourself:

```sh
git clone --branch my-version https://github.com/badmasti2/horizon-java-bridge.git
cd horizon-java-bridge
cargo build --profile fast -p horizon-client --features horizon-client/java-mods
cd launcher && cargo build --release
```

The bridge still needs Minecraft 26.3 files prepared from your own copy of the game. See the [original setup guide](https://github.com/iaxivers/horizon-java-bridge/blob/java-bridge/docs/java-bridge-beta.md) for the broader bridge workflow.

## Credits and licence

I built this fork on [iaxivers' original bridge](https://github.com/iaxivers/horizon-java-bridge), which credits [martipops](https://github.com/martipops) for Windows support and [Horizon](https://github.com/horizon-rust/horizon) for the Rust game. I have kept the project's [MIT licence](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md). This is an unofficial community project and is not associated with Mojang or Microsoft.
