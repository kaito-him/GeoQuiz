# Daily Country Mode

## Overview

Daily Country is a Wordle-style country-guessing game. Each **level** has one hidden target country. The player types country names into an autocomplete field and receives color-coded feedback across 6 attributes. The goal is to identify the correct country within **10 guesses**.

There are **195 levels** in total — one per country in the dataset.

---

## Level Grid (`DailyLevelSelectActivity`)

The entry point is a 4-column scrollable grid where each cell represents one level ("Day 1" through "Day 195").

Each cell has one of three states:

| State | Color | Icon | Clickable |
|---|---|---|---|
| Completed | Green | ✓ | Yes (replay) |
| Current | Wheat/orange | ▶ | Yes |
| Locked | Grey | 🔒 | No |

The grid auto-scrolls to bring the current level near the top when opened. Progress is displayed as `X / 195 completed` at the top of the screen.

---

## Gameplay (`DailyCountryActivity`)

### Guessing

- The player types a country name into an **autocomplete input** (suggestions appear after 1 character).
- Already-guessed countries are filtered out of suggestions.
- On submit, each guess is compared against the target across **6 attributes**, shown as colored tiles.

### Comparison Attributes

| Attribute | 🟢 Green (Correct) | 🟠 Orange (Close) | ⬜ Grey (Wrong) |
|---|---|---|---|
| **Language** | Exact same language set | At least one language in common | No overlap |
| **Currency** | Same currency code | — | Different |
| **Continent** | Same continent | — | Different |
| **Population** | Exact match | Within ±1,000,000 | Further off |
| **Area** | Exact match | Within ±1,000,000 km² | Further off |
| **Flag Colors** | Identical color sets | At least one color in common | No overlap |

- Population and area tiles also show a **↑ or ↓ arrow** indicating whether the target value is higher or lower.
- On flag color tiles, matching colors are highlighted in green text; non-matching ones appear in white.

### Outcomes

- **Correct guess** — input is locked, a success dialog appears showing the answer, number of guesses used, and coins earned.
- **10 guesses exhausted** — a failure dialog reveals the target country and offers:
  - **Try Again** — resets the level so the player can start over.
  - **Give Up** — exits back to the level grid.

After a successful non-replay completion, the player can tap **Next Level** to continue immediately or **Stay Here** to review.

---

## Coins & Rewards

- **100 coins** are awarded for completing a level for the first time.
- Coins are **not** awarded in replay mode.
- The coin balance is persisted globally across the entire app and can never go below 0.

---

## Replay Mode

When a player taps a previously completed level from the grid:

- The **10-guess limit is lifted** — they can guess as many times as they want.
- **No coins are awarded** on re-completion.
- The guess history of the active frontier level is **not overwritten**.
- The success dialog shows **Finish** instead of **Next Level**.

---

## Progress Persistence (`DailyProgressManager`)

All progress is saved to `SharedPreferences` under the key `"daily_country_prefs"`.

| Key | Type | Description |
|---|---|---|
| `current_level` | int | The next unlocked level (1-based, default 1) |
| `completed_levels` | JSON array | All completed level numbers, e.g. `[1, 2, 5]` |
| `guess_history` | JSON array | Guesses made so far on the current active level |

**Guess history** stores `{ "guessedIso": "...", "targetIso": "..." }` pairs. The full comparison result is never stored — it's recomputed from the two ISO codes when the activity is restored. History is discarded automatically if the `targetIso` doesn't match the current level's country (stale data protection).

**Self-healing:** If the stored `current_level` is already in the completed set (possible from an older build), the manager automatically walks forward to find the first incomplete level and persists the corrected value.

**Level advancement:** When a level is marked complete, `current_level` advances to `level + 1`. It never moves backward.

**Session persistence:** Guess history is saved on `onStop()`, so progress survives process death. On restore, if 10 guesses are found and the level isn't complete, the input is immediately locked and the failure state is restored correctly.
