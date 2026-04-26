# Detect Auto Alchers

A RuneLite external plugin that helps manually identify nearby accounts that may be repeatedly casting Low Level Alchemy or High Level Alchemy while using fire-rune staff equipment.

The plugin is informational only. It does not report players, click menu options, automate gameplay, or submit any automated actions. It highlights suspects and presents the evidence that caused the flag so the user can investigate manually.

## Detection model

The plugin scores each nearby player with configurable evidence:

- Fire staff evidence, defaulting to the basic staff of fire.
- Repeated alchemy-like animation or spot-animation observations within a time window.
- A Magic-dominant hiscore profile, where Magic is raised and most other skills remain low.
- A cadence bonus for repeated alchemy observations at a consistent game-tick interval.

RuneLite does not expose a semantic "other player cast High Alchemy" event. Detection is therefore inferred from observable player state.

## Default settings

- Radius: 15 tiles
- Observation window: 60 seconds
- Cast threshold: 5 observations
- Suspicion score threshold: 80
- Staff requirement: enabled
- Broad fire-rune staff matching: disabled
- Hiscore scoring: enabled
- High-Magic scoring: enabled for Magic level 99
- Non-Magic mature-account threshold: 125
- Persistent reported-player history: enabled

The animation and spot-animation ID lists are configurable. The defaults are seeded for Low/High Alchemy-style observations and should be validated in a RuneLite developer client.

Reported players are saved locally at:

```text
~/.runelite/detect-auto-alchers/reported-players.csv
```

The CSV stores `normalized_name`, `display_name`, and `date_reported`. Reported players are suppressed from future red suspect highlighting and can optionally be outlined with a separate configurable reported-player color.

## Development

Install a JDK 11+ and run:

```sh
./gradlew test
./gradlew run
```

The `run` task starts RuneLite in developer mode with this external plugin loaded.
