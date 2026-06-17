# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Create: Trackworked** (`mod_id = trackworked`) is a NeoForge port of the Trackwork mod for **Minecraft 1.21.1**, built as a Create addon. It is **not** a generic mod template — it is an active codebase rebuilt on the **Sable physics engine** (`sable_version=1.2.2`, `sable_companion_version=1.6.0`), which replaces the original VS2 (Valkyrien Skies 2) physics backend.

A VS2→Sable migration is in progress. Since Sable has no ship-attachment or serialization system, physics state that VS2 previously held as attachments is moving to BlockEntity NBT / SavedData (see the `TODO(Phase D - Sable physics)` marker in `TrackworkedMod`).

**Key dependency versions** (from `gradle.properties`):
- NeoForge `21.1.228`, Minecraft `1.21.1`, Parchment mappings `2024.11.17`
- Create `6.0.10-280`, Catnip `0.8.39`, Ponder `1.0.85+mc1.21.1`, Flywheel `1.0.6`
- Registrate `MC1.21-1.3.0+67` (resolves from `maven.ithundxr.dev/snapshots`, also JarJar'd in Create)

## Development Commands

This is a NeoForge mod for Minecraft 1.21.1 using Gradle.

**Common Gradle tasks (use `./gradlew` on Unix or `gradlew.bat` on Windows):**

- `build` - Compiles the mod and builds the JAR artifact.
- `runClient` - Launches Minecraft client with the mod loaded (development environment).
- `runServer` - Launches a dedicated server with the mod loaded.
- `runData` - Runs data generators to produce assets (lang files, blockstates, models, etc.) into `src/generated/resources`.
- `clean` - Cleans the build directory.
- `refreshDependencies` - Forces Gradle to re-download dependencies (useful when libraries are missing in IDE).
- `gameTest` - Runs any registered GameTests (if present).
- `test` - Runs unit tests (currently none configured in this template).

**IDE Setup:**
- Import the project as a Gradle project in IntelliJ IDEA or Eclipse and Visual Studio Code.
- Ensure Java 21 is configured as the project JDK.
- If dependencies fail to resolve, run `gradlew --refresh-dependencies`.

**Running a single test:**
Since this template does not include unit tests by default, the primary test mechanism is NeoForge's GameTest system. To run a specific GameTest, use:
`gradlew gameTest --tests "<testClassName>"`

## Code Architecture & Structure

All source lives under `src/main/java/net/tamim/trackworked/`.

**Mod Entry Points (two `@Mod` classes):**
- `TrackworkedMod.java` — common entry point, `@Mod(TrackworkedMod.MODID)`. Holds `MODID = "trackworked"` (with a legacy `MOD_ID` alias) and the shared `CreateRegistrate REGISTRATE`. The constructor wires up Registrate event listeners and calls each registrar's `register(...)`. This is the merged successor of the old Forge `TrackworkMod` entry point (now deleted).
- `TrackworkedModClient.java` — client-only entry point, `@Mod(value = TrackworkedMod.MODID, dist = Dist.CLIENT)`. Registers the in-game config screen (`IConfigScreenFactory`), partial models, sprite shifts, and the Ponder plugin. Safe place for client-only Create/Flywheel/Ponder code.

**Registration System:**
- Registration goes through **Create's `CreateRegistrate`** (`TrackworkedMod.REGISTRATE`), not raw NeoForge `DeferredRegister`. Tooltip styling uses the Create `STANDARD_CREATE` palette.
- Top-level registrar classes, each invoked from `TrackworkedMod`'s constructor:
  - `TrackBlocks` / `TrackworkItems` / `TrackBlockEntityTypes` / `TrackEntityTypes` — game-object registration (`register()`).
  - `TrackCreativeTabs` / `TrackSounds` — take the `IEventBus` (`register(IEventBus)`).
  - `TrackPackets` — network payload registration (`register()`).
  - `TrackworkConfigs` — config registration (`register(ModContainer)`).
  - `TrackDamageTypes`, `TrackDamageSources`, `TrackAmbientGroups` — supporting registries/data.
  - `TrackPonderPlugin` / `TrackPonders` — Ponder scene registration (client).

**Tracks / Physics package** (`tracks/`):
- `tracks/blocks/` — track & wheel blocks and block entities (`TrackBaseBlock(Entity)`, `WheelBlock(Entity)`, `OleoWheelBlock(Entity)`, `PhysEntityTrackBlock(Entity)`, `SuspensionTrackBlock(Entity)`), plus `blocks/variants/` for Med/Large size variants.
- `tracks/forces/` — physics controllers that apply Sable forces: `PhysicsTrackController`, `PhysEntityTrackController`, `OleoWheelController`, `SimpleWheelController`, and `TrackPhysics`.
- `tracks/data/` — per-block data carriers (`PhysTrackData`, `PhysEntityTrackData`, `OleoWheelData`, `SimpleWheelData`).
- `tracks/network/` — packets (`SimpleWheelPacket`, `OleoWheelPacket`, `SuspensionWheelPacket`, `ThrowTrackPacket`).
- `tracks/render/` — renderers (`SimpleWheelRenderer`, `OleoWheelRenderer`, `SuspensionRenderer`, `PhysEntityTrackRenderer`, `TrackBeltRenderer`).
- `tracks/ITrackPointProvider.java`, `tracks/TrackPonderScenes.java`.

**Configuration:**
- `TrackworkConfigs.java` — built on Catnip's `ConfigBase` (not raw `ModConfigSpec`), with `TServer` / `TClient` inner classes. Exposes settings like `enableTrackStress`, `stressMultiplier`, `maxTrackRPM`, `enableTrackThrow`, `wheelPairDistance`, `wheelRPMPassthrough`. Registered via `register(ModContainer)` and surfaced through the NeoForge in-game config screen.

**Resources:**
- `src/main/resources/assets/trackworked/lang/en_us.json` — English localization.
- `src/main/resources/META-INF/neoforge.mods.toml` — mod metadata.
- Generated resources from data generators land in `src/generated/resources` (gathered via `TrackDatagen::gatherData`, registered on the mod bus at `EventPriority.LOWEST`).

**Important Notes:**
- The mod targets Java 21; ensure the JDK is set accordingly.
- Mappings are configured via Parchment (see `gradle.properties` for versions).
- The `mod_id` must match across:
  - The `@Mod` annotation value (`TrackworkedMod.MODID`).
  - The `mod_id` property in `gradle.properties`.
  - The mod entry in `neoforge.mods.toml`.
  - The namespace used in resource paths (`assets/<mod_id>/...`).

## MCP Server Instructions

### server-sequential-thinking (MANDATORY for Goal/TODO Implementation)
**You MUST use the server-sequential-thinking MCP server EVERY SINGLE TIME when implementing goals, TODOs, or any project-related tasks.** This ensures thorough, well-reasoned implementation without generating sloppy code. Before writing any code for a feature or fix, engage sequential thinking to:
1. Break down the problem into clear steps
2. Consider edge cases and potential issues
3. Plan the implementation approach
4. Verify the solution addresses all requirements
Only proceed to implementation after completing the sequential thinking process.

### Context7 (Use by Default)
Always use context7 when I need code generation, setup or configuration steps, or library/API documentation. Use this server to fetch current documentation whenever the user asks about a library, framework, SDK, API, CLI tool, or cloud service -- even well-known ones like React, Next.js, Prisma, Express, Tailwind, Django, or Spring Boot. This includes API syntax, configuration, version migration, library-specific debugging, setup instructions, and CLI tool usage. Use even when you think you know the answer -- your training data may not reflect recent changes. Prefer this over web search for library docs.

Do not use for: refactoring, writing scripts from scratch, debugging business logic, code review, or general programming concepts.

### mcp-ripgrep (Source Code Search)
Use mcp-ripgrep to search symbols, methods, and source files from:
- This project directory's `.gradle` folder (mandatory for every search operation)
- Your home directory's `.gradle` caches
- The project's source code (`src/` directory)
Always use this tool when you need to find specific implementations, method signatures, or code patterns within the codebase or Gradle caches.

### maven-indexer-mcp (Dependency Source Access)
Use maven-indexer-mcp to access source files from:
- Local Maven repository (`~/.m2/repository`)
- Internal company libraries and artifacts
- Gradle dependency caches
Use this tool when you need to examine the source code of dependencies or internal libraries that are not present in the current workspace but are available in the local Maven/Gradle caches.
