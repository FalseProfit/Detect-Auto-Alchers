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

![Highlight Overlay](docs/images/Player%20Highlighting%20Example.png)
- The plugin side panel lists current suspects, confidence, score, casts, hiscore evidence, reductions, and time since last seen.

![Info Panel Example](docs/images/Suspect%20Info%20Panel%20Example.png)
- When right-click inspect is enabled, the player menu includes an Inspect Alch Activity option that shows the latest manually examined player score in a separate side-panel section without changing detection, watchlist, override, reported, or mobile suppression state.

![Right Click Menu Example](docs/images/Player%20Menu%20Example.png)
- The debug menu-score setting adds each right-clicked player's current detection score to the Report option.
- The side panel can be switched to compact mode for shorter suspect rows.
- Right-click menu entries for suspects are colored by confidence when menu coloring is enabled.
- Right-click player menu entries are sorted by confidence when menu sorting is enabled: high confidence first, then moderate confidence, then unflagged entries.
- When you click RuneLite's normal Report option, the plugin suppresses that player from future suspect highlighting. If reported-player persistence is enabled, the player is also saved locally across restarts.
- Previously reported players can be outlined and menu-colored with a separate configurable reported-player color.
- The side panel can import, export, and clear local reported-player history.
- The side panel includes a visual watchlist. Watched players can be outlined and shown in the panel when seen, but watchlist entries do not change detection score or confidence.
- The watchlist can remove entries already present in reported-player history, or entries whose normal OSRS hiscore lookup returns not found. The hiscore cleanup is a heuristic and is not proof of a ban, because renamed or missing-name accounts can also fail name-based lookup.
- The side panel includes an Override list. Override-listed players are suppressed from suspect highlighting until removed from the list.
- The side panel includes one-shot Conservative, Balanced, and Aggressive preset buttons. Presets write selected detection/scoring settings once; they do not create managed profiles.
- Mobile-client players can be suppressed after their mobile icon is observed in the right-click menu.

The plugin never submits reports automatically.

## Default settings

- Detection radius: 15 tiles
- Observation window: 60 seconds
- Cast threshold: 10 observations. Set to `0` to allow fire-staff hiscore lookups and score-only detection without requiring recent casts.
- Moderate confidence threshold: 90
- High confidence margin: +30, for an effective high confidence threshold of 120
- Require fire staff: enabled
- Broad fire-rune staff matching: disabled
- Ignore mobile players: enabled
- Hiscore lookup retry cooldown: 3 minutes
- Alchemy animation IDs: `713`
- Alchemy spot-animation IDs: `112,113`
- Scene overlay: enabled
- Menu coloring: enabled
- Menu sorting by confidence: disabled
- Right-click inspect players: disabled
- Persistent reported-player history: enabled
- Reported-player highlighting: enabled
- Reported-player highlight color: RGB `144,238,144`
- Compact panel mode: disabled
- Hiscore scoring: enabled
- Minimum Magic threshold: Magic level 53. Set to 21 to include Low Alchemy capable accounts.
- Other-skill threshold: level 50
- Allowed other skills above threshold: 1
- High-Magic scoring: enabled at Magic level 99
- High-Magic score: +100
- Non-Magic total reduction: enabled
- Non-Magic total reduction threshold: 150
- Non-Magic total reduction penalty: -150
- Clue/collection-log reduction threshold: at least 1 combined entry
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

The reported-player CSV stores `normalized_name`, `display_name`, and `date_reported`. The watchlist CSV stores `normalized_name`, `display_name`, and `date_watched`. The Override list CSV stores `normalized_name`, `display_name`, and `date_allowlisted`. Reported players and Override list players are suppressed from future suspect highlighting. Watchlist players are visual only.

Preset buttons apply these intent-level profiles:

- Conservative: current default detection/scoring settings, with stricter cast and score thresholds, narrow staff matching, and stronger played-account reductions.
- Balanced: middle-ground detection/scoring settings.
- Aggressive: lower cast and score thresholds, broad fire-rune staff matching, Magic threshold 21, and lighter played-account reductions.

## Sideload install

This plugin is not distributed through RuneLite Plugin Hub. The supported end-user artifact is the plain sideload jar named `detect-auto-alchers.jar`.

1. Download `detect-auto-alchers.jar` from the latest [GitHub Release](https://github.com/FalseProfit/Detect-Auto-Alchers/releases).
2. Close RuneLite.
3. Create RuneLite's sideload folder if it does not exist.
4. Copy `detect-auto-alchers.jar` into that sideload folder.
5. Start the standalone RuneLite client with `--developer-mode`.
6. Open RuneLite's plugin panel, search for `Detect Auto Alchers`, and enable it.

The sideload folder is:

```text
macOS/Linux: ~/.runelite/sideloaded-plugins/
Windows:     %USERPROFILE%\.runelite\sideloaded-plugins\
```

macOS/Linux install example:

```sh
mkdir -p ~/.runelite/sideloaded-plugins
cp detect-auto-alchers.jar ~/.runelite/sideloaded-plugins/
```

Windows PowerShell install example:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.runelite\sideloaded-plugins"
Copy-Item .\detect-auto-alchers.jar "$env:USERPROFILE\.runelite\sideloaded-plugins\"
```

Standalone RuneLite launch examples:

```sh
# macOS, if RuneLite is installed in /Applications
/Applications/RuneLite.app/Contents/MacOS/RuneLite --developer-mode

# Linux, if RuneLite is on PATH
runelite --developer-mode
```

```bat
REM Windows, adjust the executable path if RuneLite is installed elsewhere
"%LOCALAPPDATA%\RuneLite\RuneLite.exe" --developer-mode
```

To update the plugin, close RuneLite, replace the old jar in `sideloaded-plugins` with the new `detect-auto-alchers.jar`, and start standalone RuneLite with `--developer-mode` again.

### Jagex Launcher and Jagex accounts

Sideloaded plugins require RuneLite developer mode. Current RuneLite source loads jars from `~/.runelite/sideloaded-plugins/` only when developer mode is active, and current developer mode is ignored when RuneLite is started with Jagex Launcher metadata. In practice, use the Jagex Launcher to authenticate a character, then use standalone RuneLite with `--developer-mode` to load the sideloaded jar.

For one Jagex-account character:

1. Open the Jagex Launcher.
2. Select the in-game character, for example `usernameA`.
3. Launch RuneLite normally from the Jagex Launcher.
4. If RuneLite asks to remember or cache credentials, allow it.
5. Let RuneLite reach the login screen, lobby, or game once, then close RuneLite.
6. Start standalone RuneLite with `--developer-mode`.

For multiple in-game characters, repeat those steps per character:

1. Launch `usernameA` from the Jagex Launcher, allow RuneLite to cache credentials, close RuneLite, then launch standalone RuneLite with `--developer-mode`.
2. Launch `usernameB` from the Jagex Launcher, allow RuneLite to cache credentials, close RuneLite, then launch standalone RuneLite with `--developer-mode`.
3. Repeat for each additional character.

If standalone RuneLite does not reuse the cached login, RuneLite also has advanced development flags for explicit session files. The session file must be written during a Jagex Launcher-backed RuneLite start first. If your RuneLite/Jagex Launcher setup supports passing extra RuneLite arguments, launch `usernameA` from the Jagex Launcher with:

```text
--insecure-write-credentials --sessionfile /absolute/path/to/.runelite/session-usernameA --profile usernameA
```

Close RuneLite after it writes the session file, then start standalone RuneLite with the same session file:

```sh
/Applications/RuneLite.app/Contents/MacOS/RuneLite --developer-mode --sessionfile "$HOME/.runelite/session-usernameA" --profile usernameA
```

```bat
"%LOCALAPPDATA%\RuneLite\RuneLite.exe" --developer-mode --sessionfile "%USERPROFILE%\.runelite\session-usernameA" --profile usernameA
```

Repeat with a separate file and profile for each additional character, such as `session-usernameB` with `--profile usernameB`. RuneLite's `--insecure-write-credentials` flag can dump Jagex Launcher authentication tokens to a session file for development. Treat any session file as a password: keep it private, do not upload it, do not share it, and use a separate session file per character if running multiple accounts. This is an advanced local workflow, not an official Plugin Hub or Jagex Launcher distribution path.

## Development

Install JDK 11 for RuneLite plugin development. This project compiles Java 11-compatible bytecode via Gradle's `options.release.set(11)` setting.

Gradle 8.10 can currently run on JDK 11, but Gradle 9 will require launching Gradle with JDK 17+. If the Gradle wrapper is upgraded later, keep the project compiling with Java 11 compatibility.

Then run:

```sh
./gradlew test
./gradlew run
```

The `run` task starts RuneLite in developer mode with this external plugin loaded.

To build the end-user sideload jar locally:

```sh
./gradlew clean test sideloadJar
```

The stable sideload artifact is written to:

```text
build/sideloaded-plugins/detect-auto-alchers.jar
```

Do not distribute the `shadowJar` output to end users. It is a development fat jar for launcher/debug flows, not the RuneLite sideload artifact.
