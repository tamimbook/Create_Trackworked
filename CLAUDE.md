# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

This is a NeoForge mod template for Minecraft 1.21.1 using Gradle.

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

**Mod Entry Point:**
- `src/main/java/net/tamim/trackworked/TrackworkMod.java` - Main mod class annotated with `@Mod`. Handles event registration, mod initialization, and registration of deferred objects.

**Registration System:**
- The mod uses NeoForge's `DeferredRegister` system for type-safe, lazy registration of game objects:
  - `BLOCKS` - Registers blocks (see `EXAMPLE_BLOCK`).
  - `ITEMS` - Registers items (see `EXAMPLE_BLOCK_ITEM` and `EXAMPLE_ITEM`).
  - `CREATIVE_MODE_TABS` - Registers creative mode tabs (see `EXAMPLE_TAB`).
- Objects are registered in the mod constructor via the mod event bus (`modEventBus.addListener`).

**Configuration:**
- `src/main/java/net/tamim/trackworked/Config.java` - Example configuration class using NeoForge's `ModConfigSpec`.
- Demonstrates boolean, integer, string, and list config values with validation.
- Automatically synced to `trackworked-common.toml` in the instance's config folder.

**Resources:**
- `src/main/resources/assets/trackworked/lang/en_us.json` - English localization file.
- `src/main/resources/META-INF/neoforge.mods.toml` - Mod metadata (mod ID, version, dependencies, etc.) used by NeoForge for loading.
- Generated resources from data generators (if used) appear in `src/generated/resources` and are automatically included in the build.

**Event Handling:**
- The mod subscribes to lifecycle events (`FMLCommonSetupEvent`, `ServerStartingEvent`) and registry events (`BuildCreativeModeTabContentsEvent`).
- Event bus registration occurs in the constructor: `NeoForge.EVENT_BUS.register(this)` for the mod bus, and `modEventBus.addListener` for the mod-specific bus.

**Typical Workflow:**
1. Add new blocks/items by creating `DeferredBlock`/`DeferredItem` fields and registering them in the respective `DeferredRegister`.
2. Add localized names to `assets/trackworked/lang/en_us.json` (or other language files).
3. For complex items/blocks, create classes that extend the base vanilla classes and reference them in the deferred registrations.
4. Adjust `neoforge.mods.toml` if changing mod ID, version, or adding dependencies.
5. Run `runClient` or `runServer` to test changes in a development environment.

**Important Notes:**
- The mod targets Java 21; ensure the JDK is set accordingly.
- Mappings are configured via Parchment (see `gradle.properties` for versions).
- The `mod_id` must match across:
  - The `@Mod` annotation value (`TrackworkMod.MODID`).
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
