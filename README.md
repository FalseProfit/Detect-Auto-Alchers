# Detect Auto Alchers

A RuneLite external plugin that helps manually identify nearby accounts that may be repeatedly casting Low Level Alchemy or High Level Alchemy while using fire-rune staff equipment.

The plugin is informational only. It does not report players, click menu options, automate gameplay, or submit any automated actions. It highlights suspects and presents the evidence that caused the flag so the user can investigate manually.

## Detection model

The plugin tracks nearby players inside the configured radius and scores them with configurable evidence:

- Fire staff evidence, defaulting to the basic staff of fire.
- Repeated alchemy-like animation or spot-animation observations within a time window.
- A minimum Magic level gate for found hiscore profiles, plus a Magic-dominant hiscore bonus where Magic is raised and only a configurable number of other skills are above the configured other-skill threshold.
- A high-Magic bonus for accounts still alching at 99 Magic.
- A cadence bonus for repeated alchemy observations at a consistent game-tick interval.
- Score reductions for accounts with enough non-Magic total level or combined clue-scroll completions and collection-log items to look less like fresh alching accounts.

Repeated alchemy behavior is required before a player can be highlighted unless Cast threshold is set to `0`. In zero-cast mode, nearby fire-staff players can receive hiscore lookups and become score-only suspects without recent cast observations. When the staff requirement is enabled, staff evidence is also required before a scored player can become a suspect.

RuneLite does not expose a semantic "other player cast High Alchemy" event. Detection is therefore inferred from observable player state.

## User-facing behavior

- The scene overlay outlines high-confidence suspects in red and moderate-confidence suspects in yellow.
- The plugin side panel lists current suspects, confidence, score, casts, hiscore evidence, reductions, and time since last seen.
- The right-click player menu includes an Examine option that shows the latest manually examined player score in a separate side-panel section without changing detection, watchlist, override, reported, ignore, or mobile suppression state.
- The side panel can be switched to compact mode for shorter suspect rows.
- Right-click menu entries for suspects are colored by confidence when menu coloring is enabled.
- Right-click player menu entries are sorted by confidence when menu sorting is enabled: high confidence first, then moderate confidence, then unflagged entries.
- When you click RuneLite's normal Report option, the plugin suppresses that player from future suspect highlighting. If reported-player persistence is enabled, the player is also saved locally across restarts.
- Previously reported players can be outlined and menu-colored with a separate configurable reported-player color.
- The side panel can import, export, and clear local reported-player history.
- The side panel includes a visual watchlist. Watched players can be outlined and shown in the panel when seen, but watchlist entries do not change detection score or confidence.
- The side panel includes an Override list. Override-listed players are suppressed from suspect highlighting until removed from the list.
- The side panel includes one-shot Conservative, Balanced, and Aggressive preset buttons. Presets write selected detection/scoring settings once; they do not create managed profiles.
- Players on the RuneLite ignore list are suppressed.
- Mobile-client players can be suppressed after their mobile icon is observed in the right-click menu.

The plugin never submits reports automatically.

## Default settings

- Detection radius: 15 tiles
- Observation window: 60 seconds
- Cast threshold: 5 observations. Set to `0` to allow fire-staff hiscore lookups and score-only detection without requiring recent casts.
- Moderate confidence threshold: 80
- High confidence margin: +30, for an effective high confidence threshold of 110
- Require fire staff: enabled
- Broad fire-rune staff matching: disabled
- Ignore mobile players: enabled
- Hiscore lookup retry cooldown: 3 minutes
- Alchemy animation IDs: `713`
- Alchemy spot-animation IDs: `112,113`
- Scene overlay: enabled
- Menu coloring: enabled
- Menu sorting by confidence: enabled
- Persistent reported-player history: enabled
- Reported-player highlighting: enabled
- Reported-player highlight color: RGB `144,238,144`
- Compact panel mode: disabled
- Hiscore scoring: enabled
- Minimum Magic threshold: Magic level 53. Set to 21 to include Low Alchemy bots.
- Other-skill threshold: level 50
- Allowed other skills above threshold: 2
- High-Magic scoring: enabled at Magic level 99
- High-Magic score: +100
- Non-Magic total reduction: enabled
- Non-Magic total reduction threshold: 150
- Non-Magic total reduction penalty: -100
- Clue/collection-log reduction threshold: at least 4 combined entries
- Clue/collection-log reduction penalty: -100

Built-in score values:

- Basic staff evidence: +30
- Repeated alchemy behavior: +50
- Magic-dominant hiscore profile: +30
- Consistent cadence: +10

The animation and spot-animation ID lists are configurable. The defaults are seeded for Low/High Alchemy-style observations and should be validated in a RuneLite developer client.

Reported players are saved locally at:

```text
~/.runelite/detect-auto-alchers/reported-players.csv
~/.runelite/detect-auto-alchers/watchlist.csv
~/.runelite/detect-auto-alchers/override-list.csv
```

Each CSV stores `normalized_name`, `display_name`, and `date_reported`. Reported players and Override list players are suppressed from future suspect highlighting. Watchlist players are visual only.

Preset buttons apply these intent-level profiles:

- Conservative: stricter cast and score thresholds, narrow staff matching, and stronger played-account reductions.
- Balanced: current default detection/scoring settings.
- Aggressive: lower cast and score thresholds, broad fire-rune staff matching, Magic threshold 21, and lighter played-account reductions.

## Development

Install JDK 11 for RuneLite plugin development. This project compiles Java 11-compatible bytecode via Gradle's `options.release.set(11)` setting.

Gradle 8.10 can currently run on JDK 11, but Gradle 9 will require launching Gradle with JDK 17+. If the Gradle wrapper is upgraded later, keep the project compiling with Java 11 compatibility.

Then run:

```sh
./gradlew test
./gradlew run
```

The `run` task starts RuneLite in developer mode with this external plugin loaded.

Two helper scripts are available for local sideload testing:

```sh
scripts/install-sideloaded-plugin.zsh
scripts/run-sideloaded-runelite.zsh
```

`install-sideloaded-plugin.zsh` runs tests, builds the plugin jar, and copies it to `~/.runelite/sideloaded-plugins`. `run-sideloaded-runelite.zsh` launches a locally installed RuneLite client from `~/.runelite/repository2` in developer mode.
