# Country Quest — Full Mode Documentation

## Overview

Country Quest is the main game mode of GeoQuiz. The player is presented with a hidden target country and must identify it by submitting country guesses. Each guess reveals how closely it matches the target across six attributes, narrowing down the answer through elimination and deduction. The mode spans **195 countries**, each playable as a separate level.

---

## Level System

### Total Levels
The game contains **195 levels**, one per country in `countries.json`. Levels are played in a fixed order defined by that file.

### Level Selection Screen
A grid of cells shows all 195 levels. Each cell displays:
- The level number ("Level X")
- A status icon (✓, ▶, or 🔒)
- The difficulty label of the country for that level

#### Cell States
| State | Background | Icon | Clickable |
|---|---|---|---|
| Completed | Green | ✓ | Yes — replay allowed |
| Current | Wheat/tan | ▶ | Yes |
| Locked | Grey, 55% opacity | 🔒 | No |

#### Difficulty Label Colors
| Tier | Color |
|---|---|
| Easy | White |
| Intermediate | Brown (`#6D4C41`) |
| Hard | Black |
| Expert | Deep purple (`#4A148C`) |

### Progression
Levels unlock sequentially. Completing a level unlocks the next one. The player can replay any already-completed level at any time (replay mode — no coins are awarded again).

---

## Country Data

Each country entry in `countries.json` contains the following fields used during gameplay:

| Field | Description |
|---|---|
| `ISO Code` | Two-letter country code, used as the unique key |
| `Country` | Full display name |
| `Difficulty Category` | Easy / Intermediate / Hard / Expert |
| `Capital Name` | Capital city (used in hint) |
| `Main Languages` | Array of official/main languages |
| `Currency Code` | ISO 4217 code (e.g. USD, EUR) |
| `Currency Name` | Full currency name (e.g. "Euro") |
| `Continent` | One of: Africa, Asia, Europe, North America, South America, Oceania |
| `Population (2026)` | Integer population figure |
| `Area (in km2)` | Floating-point land area |
| `Map` | Filename of the country's map image in `assets/country_maps/` |
| `Fun Fact` | One-sentence interesting fact (used in hint) |
| `Flag Colors` | Array of color name strings |
| `Flag` | Filename of the flag image in `assets/flags/` (lowercase ISO code `.png`) |

---

## Gameplay Loop

### Making a Guess
1. The player types a country name into the autocomplete field. Suggestions appear after the first character.
2. Selecting a suggestion or pressing the search key submits the guess immediately.
3. A country can only be guessed once per attempt — duplicates are silently rejected.
4. The game allows a maximum of **10 guesses** per level.

### Guess Card
Each submitted guess appears as a card at the **top** of the list (newest first). The card shows:
- The country's **flag** and **name** in the header row
- Six **comparison tiles** in a 2×3 grid

#### Tile Animation
When a new guess card is added, the six tiles flip in one by one with a staggered animation:
- Each tile rotates from 90° (face-down) to 0° while fading from invisible to fully opaque
- Duration: **520 ms** per tile
- Stagger: **220 ms** between consecutive tiles
- Total animation time: ~**1620 ms** (last tile finishes)
- A tile that has already been animated (e.g. on screen rotation or restore) skips animation

---

## Comparison Tiles

### 1. Language
Compares the language sets of the guessed and target countries.

| Result | Condition | Tile Color |
|---|---|---|
| 🟢 Green | Both countries have exactly the same language set | Green |
| 🟠 Orange | At least one language in common, but sets differ | Orange |
| ⬜ Grey | No language in common | Grey |

### 2. Currency
Compares the ISO currency codes (case-insensitive).

| Result | Condition | Tile Color |
|---|---|---|
| 🟢 Green | Same currency code | Green |
| ⬜ Grey | Different currency code | Grey |

No "close" state for currency — it's binary.

### 3. Population
Compares the two populations numerically. Shows a directional arrow (↑ or ↓) indicating whether the target's population is higher or lower.

| Result | Condition | Tile Color |
|---|---|---|
| 🟢 Green | Exact match | Green |
| 🟠 Orange | Difference ≤ 1,000,000 | Orange + arrow |
| ⬜ Grey | Difference > 1,000,000 | Grey + arrow |

The tile displays the guessed country's formatted population (e.g. "8,996,745").

### 4. Area
Compares land area in km². Arrow indicates direction.

| Result | Condition | Tile Color |
|---|---|---|
| 🟢 Green | Exact match | Green |
| 🟠 Orange | Difference ≤ 1,000,000 km² | Orange + arrow |
| ⬜ Grey | Difference > 1,000,000 km² | Grey + arrow |

The tile displays the guessed country's area formatted as "X km²".

### 5. Continent
Compares the continent string (case-insensitive).

| Result | Condition | Tile Color |
|---|---|---|
| 🟢 Green | Same continent | Green |
| ⬜ Grey | Different continent | Grey |

No "close" state — binary match.

### 6. Flag Colors
Compares the sets of colors in each country's flag.

| Result | Condition | Tile Color |
|---|---|---|
| 🟢 Green | Both flags share exactly the same color set | Green |
| 🟠 Orange | At least one color in common, but sets differ | Orange |
| ⬜ Grey | No colors in common | Grey |

The tile displays the guessed country's flag color list as plain text.

### Correct Guess (All Green)
When the player guesses the target country, **all six tiles turn green** regardless of their comparison values. Input is disabled and the win flow begins.

---

## Win Flow

### Timing
After the correct guess is submitted, the success dialog is **delayed by 2000 ms** to allow the tile flip animation to finish (~1620 ms) and give the player a moment to see the completed green row.

### Success Dialog
A custom-themed dialog (warm earth-tone card) appears with:
- **Title**: "Congratulations! 🎉" (or "All Done! 🌍" on the final level)
- **"You found it!"** sub-heading
- **Country**: the name of the target country
- **Guesses**: how many guesses were used
- **Coins earned**: amount + coin icon + "coins earned!" + breakdown line
- **Buttons**: "Stay Here" / "Next Level" (or just "Finish" on the last level or in replay)

The dialog uses the same visual style as the Hint Shop: transparent window, 88% screen width, centered, 60% dark scrim behind it, scale+fade entrance animation (220 ms).

### Fail Flow
If the player exhausts all 10 guesses without finding the answer, a failure dialog appears with:
- The correct country name revealed
- Options to **Try Again** (reset the level) or **Give Up** (exit)

---

## Coins & Rewards

### Currency
The coin balance is stored globally (across all game modes) in `SharedPreferences` under the key `coin_prefs / coins`. It persists across sessions and is visible in the top-right coin badge on the home screen.

### Earning Coins
Coins are awarded **once per level** on first-time completion. Replaying a completed level earns no coins.

#### Base Reward
Every first-time completion earns **100 coins** as a base.

#### Performance Bonus (no hints used)
When the player completes the level without buying any hints, a speed bonus is added on top of the base:

| Guesses Used | Bonus | Total |
|---|---|---|
| 1 | +50 | 150 |
| 2 | +40 | 140 |
| 3 | +30 | 130 |
| 4 | +20 | 120 |
| 5 | +15 | 115 |
| 6 | +10 | 110 |
| 7 | +5 | 105 |
| 8–10 | +0 | 100 |

#### Hint Penalty (hints used)
If any hint slot was purchased, **no performance bonus is applied**. Instead, a flat penalty per slot:

- **−10 coins per hint slot used**
- Minimum reward is 0 (never negative)

| Hints Used | Reward |
|---|---|
| 0 | 100 + speed bonus |
| 1 | 90 |
| 2 | 80 |

---

## Hint System

### Overview
The hint system lets players spend coins to reveal pieces of information about the target country when they are stuck. Hints are managed by `HintManager` and purchased through the **Hint Shop** dialog (💡 button).

### Hint Slots
Each attempt has **2 hint slots**, unlocking progressively:

| Slot | Unlocks After |
|---|---|
| Slot 1 | 4 guesses submitted |
| Slot 2 | 8 guesses submitted |

Slots are consumed in order — slot 2 cannot be used before slot 1. Both slots together mean a maximum of **2 hints per level attempt**.

The 💡 button is enabled only when a slot is unlocked, not yet used, and the level is still active.

### Available Hints & Prices

| Hint | Key | Price |
|---|---|---|
| Reveal Continent | `continent` | 20 coins |
| Reveal a Flag Color | `flag_color` | 25 coins |
| Population/Area Clue | `num_clue` | 25 coins |
| Reveal Currency Name | `currency` | 30 coins |
| Reveal Fun Fact | `fun_fact` | 40 coins |
| Reveal a Language | `language` | 50 coins |
| Reveal Capital City | `capital` | 60 coins |

### Eligibility Rules
A hint is grayed out (ALREADY_KNOWN) if the information it would reveal is already known from submitted guesses or a prior hint purchase:

- **Continent** — already known if any green continent tile was seen
- **Flag Color** — already known if all of the target's flag colors are already known
- **Language** — already known if all of the target's languages are already known
- **Currency** — already known if a green currency tile was seen
- **Capital** — already known if the capital hint was already purchased
- **Population/Area Clue** — already known if this hint was already purchased
- **Fun Fact** — already known if this hint was already purchased

### Population/Area Clue Format
Instead of exact numbers, this hint shows bracket ranges:

**Population brackets:** under 100K · 100K–500K · 500K–1M · 1M–5M · 5M–10M · 10M–50M · 50M–100M · 100M–500M · over 500M

**Area brackets:** under 1,000 km² · 1K–10K · 10K–100K · 100K–500K · 500K–1M · 1M–5M · over 5M km²

### Hint Reveal Card
When a hint is purchased, a card is inserted at the top of the guess list with a **slide-down + fade-in animation** (380 ms, decelerate). The card shows the hint type label and the revealed value.

### Persistence
Hint state is saved to `SharedPreferences` as a JSON object so that purchased hints survive app restarts:
```json
{
  "slot1": { "type": "capital", "text": "Paris" },
  "slot2": { "type": "fun_fact", "text": "..." }
}
```

---

## State Persistence

The following state is saved automatically and restored on app resume:

| Data | Stored In |
|---|---|
| Current level number | `DailyProgressManager` → SharedPreferences |
| Completed levels set | `DailyProgressManager` → SharedPreferences |
| Guess history (ISO codes) | `DailyProgressManager` → SharedPreferences |
| Hint state (JSON) | `DailyProgressManager` → SharedPreferences |
| Reward issued flag | `DailyProgressManager` → SharedPreferences |
| Coin balance | `CoinManager` → SharedPreferences (`coin_prefs`) |

On restore, guess history is replayed without animation. Hint cards are re-inserted without animation. If the player had already completed the level before closing the app, the game enters replay mode on re-open.

---

## Replay Mode

When a player opens a level they have already completed:
- All guess history is shown (no animation)
- The hint button is disabled
- No coins are awarded on finding the answer again
- The success dialog shows only a "Finish" button (no "Next Level")

---

## Architecture Summary

| Class | Responsibility |
|---|---|
| `DailyCountryActivity` | Main game screen, UI wiring, guess submission, dialog management |
| `DailyLevelSelectActivity` | Level grid screen |
| `LevelSelectAdapter` | RecyclerView adapter for the level grid cells |
| `GuessAdapter` | RecyclerView adapter for guess + hint reveal cards |
| `CountryRepository` | Loads and indexes `countries.json` from assets |
| `CountryComparator` | Stateless comparison logic producing `CountryGuess` result objects |
| `CountryGuess` | Data class holding one guess result (6 `ComparisonResult` fields) |
| `HintManager` | Slot tracking, eligibility checks, reveal text generation, persistence |
| `HintType` | Enum of all hint types with stable key and coin price |
| `CoinManager` | Global coin balance read/write via SharedPreferences |
| `DailyProgressManager` | All level progress persistence (completed, history, hints, rewards) |
| `RewardCalculator` | Stateless coin reward computation |
