# BattleCraft Server Pack

BattleCraft is a consolidated Minecraft Forge 1.20.1 server-oriented modpack that integrates multiple custom gameplay modules into a unified codebase.

## Technical Specifications

*   **Platform**: Minecraft Forge 1.20.1 (Forge 47.4.10)
*   **Java Version**: Toolchain set to Java 17
*   **Build System**: Gradle

## Modules Included

1.  **Airdrop Mod** (`/airdrop`): Handles scheduled, localized loot drop events with customizable loot tables.
2.  **Capture Points** (`/capturepoint`, `/finalpoint`): Implements team-based spatial capture objective gameplay.
3.  **Combat Timer** (`/kt`): Implements PvP tagging, combat logging prevention, and safety timers.
4.  **Damage Indicator** (`/dmgndctr`): Renders visual markers and damage output tracking.
5.  **Immortality** (`/immortality`): Temporary invulnerability, safety, or spawn protection logic.
6.  **Item Modifiers** (`/ie`): Command suite to dynamically modify or scale item properties.
7.  **Kill Reward** (`/killreward`, `/kr`): Grants custom items or currency configurations on player eliminations.
8.  **Knockdown Mod**: Implements a downed state system for players, allowing revives, self-revive injectors, and surrender mechanics. Includes a premium squircle HUD.
9.  **Minimap**: A custom client-side spatial radar and global map with team marker synchronization, fallback local scanning, spatial dithering, height scanning, and offline-persistent caching.
10. **Quarry Mod** (`/quarry`): Automates localized resource extraction.
11. **Sell Mod** (`/sell`): Integrated shop commands for item liquidations.
12. **BattleCraft** (`/battlecraft`): Implements the complete game lifecycle of the "Battlecraft" mini-game. Features lobby phase, dynamic team selection via custom ESC menu, automated mod control (disabling sell, quarry, and killreward mods in lobby), capture point HUD filtering for spectators, late-joiners warning system, IP-based anti-abuse protection with UUID-only session reconnects, team surrender voting, execution commands on start/stop/surrender, and asynchronous Discord RPC presence integration.
13. **Shared Utilities** (`com.persiki84.shared`): Single source of cross-cutting helpers (`CommandHelper`, `ConfigHelper`, `ModMessage`, `LangHelper`). All modules consume these directly; per-module copies are not permitted.

## Conventions

*   **Localization**: All player-facing text is emitted via `Component.translatable` against per-module `assets/<modid>/lang/{en_us,ru_ru}.json`. Raw `Component.literal` with embedded display strings is disallowed. Config-file comments, log records, and Discord Rich Presence are exempt as they bypass the in-game language pipeline.
*   **Render colors**: Repeated, semantically meaningful ARGB values live in per-module color holders (e.g. `CaptureColors`, `KnockdownHudColors`). One-off render values remain inline.
*   **Configuration**: Static settings use `ForgeConfigSpec`; dynamic runtime-mutable state (e.g. SellMod price maps) is persisted as JSON.

## Installation and Development

To compile and build the server pack:

```bash
# Clone the repository and navigate to the project directory
cd project/megamod

# Compile the codebase
./gradlew compileJava

# Build the final JAR
./gradlew build
```

The output JAR will be located under `build/libs/`.
