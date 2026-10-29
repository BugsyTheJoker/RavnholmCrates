# RavnholmCrates

A custom crate and reward plugin for Minecraft Paper, developed for **Ravnholm SMP**.

RavnholmCrates is a Java project built around configurable crates, physical keys, weighted rewards, inventory-based interfaces, and administrative tools. The plugin is designed for the server's medieval-inspired gameplay and progression systems.

## Features

- **Three configured crate tiers:** `vote` (Vote Crate), `epic` (Ravnholm/Epic Crate), and `legendary` (Royal/Legendary Crate).
- **Custom keys** with item metadata used to identify the matching crate.
- **Weighted random rewards**, configured per crate in YAML.
- **Reward types:** items, economy payouts, and additional crate keys.
- **Interactive GUIs:** preview available rewards and watch an opening animation.
- **Optional holograms** through FancyHolograms.
- **Statistics:** total openings, openings per crate type, and per-player totals.
- **Crate location management:** register and remove crate blocks in the world.
- **Crate protection:** prevents breaking registered crates and certain conflicting chest placements.
- **Reloadable configuration** and administrator commands.

## Tech stack

| Component | Version / role |
| --- | --- |
| Language | Java 25 |
| Server API | Paper API `26.2.build.+` |
| Build tool | Gradle 9.6.0 (Kotlin DSL) |
| FancyHolograms | Optional runtime integration; 2.12.0 API used for compilation |
| Vault + economy provider | Required at runtime **only if** crates contain `MONEY` rewards |

> This project targets the specific Paper API version configured in `build.gradle.kts`. Compatibility with other Minecraft versions has not been verified.

## Build from source

**Requirements:** JDK 25, internet access for Gradle dependencies, and a local copy of `FancyHolograms-2.12.0.jar`.

The FancyHolograms JAR is a third-party dependency and is **not included** in this source repository. Obtain version 2.12.0 from its official distribution and place it at:

```text
libs/FancyHolograms-2.12.0.jar
```

Then run:

```powershell
.\gradlew.bat build
```

On Linux/macOS:

```bash
./gradlew build
```

The compiled plugin JAR is generated in `build/libs/`.

**Note:** The build has not been independently verified as part of this public-source preparation. The repository contains no automated tests yet.

## Installation

1. Build the plugin using the instructions above.
2. Copy the generated RavnholmCrates JAR to the Paper server's `plugins/` directory.
3. Optionally install FancyHolograms to enable crate holograms.
4. If using `MONEY` rewards (included in the provided default configuration), install Vault and a compatible economy provider. Without a working economy provider, crates with money rewards cannot be opened.
5. Restart the server.
6. Edit `plugins/RavnholmCrates/crate-types.yml` to configure crates and rewards, then use the reload command.

## Commands

All commands require the `ravnholmcrates.admin` permission (granted to operators by default).

| Command | Purpose |
| --- | --- |
| `/ravnholmcrates` | Show the admin help menu |
| `/ravnholmcrates list` | List configured crate types |
| `/ravnholmcrates info <crate>` | View crate rewards and probabilities |
| `/ravnholmcrates stats [player]` | View crate opening statistics |
| `/ravnholmcrates set <crate>` | Register the chest you're looking at as a crate |
| `/ravnholmcrates remove` | Unregister the crate you're looking at |
| `/ravnholmcrates give <player> <crate> [amount]` | Give crate keys |
| `/ravnholmcrates reload` | Reload crate configuration |

**Permission:** `ravnholmcrates.admin` (default: `op`).

## Player interaction

- **Left-click** a registered crate to preview its rewards.
- **Right-click** a registered crate with the matching key to open it.
- Opening a crate uses a custom inventory animation before revealing the reward.

## Configuration

The main crate definitions live in `src/main/resources/crate-types.yml`. The default configuration defines `vote`, `epic`, and `legendary` crates.

For example, a basic item reward has the following structure:

```yaml
crates:
  vote:
    name: "&b&lVote Crate"
    rewards:
      diamonds:
        type: ITEM
        name: "&b2x Diamonds"
        material: DIAMOND
        amount: 2
        weight: 6.0
```

**Weights are relative**, not fixed percentages. Each reward's probability is its weight divided by the sum of the weights of all rewards in that crate.

The full default configuration also demonstrates key definitions, hologram lines, and the `MONEY` and `KEY` reward types.

## Project structure

```text
src/main/java/dk/ravnholm/crates/
├── commands/       # Administrator commands
├── crates/         # Crate types, registration and locations
├── gui/            # Preview and opening animation
├── holograms/      # Optional FancyHolograms integration
├── keys/           # Key creation and validation
├── listeners/      # Player interaction and crate protection
├── rewards/        # Reward definitions and weighted selection
├── statistics/     # Persistent crate statistics
└── RavnholmCrates.java

src/main/resources/
├── config.yml
├── crate-types.yml
└── plugin.yml
```

## Project status

This repository contains the source code for a server-specific plugin. Configuration values and rewards are tailored to Ravnholm SMP and may need adjustments before use elsewhere. No automated test suite or compatibility matrix is currently provided.

## Author

Developed by **BugsyTheJoker** for Ravnholm SMP.

## License

No open-source license has been granted. The source is shared for viewing and portfolio purposes; redistribution or reuse requires the author's permission.
