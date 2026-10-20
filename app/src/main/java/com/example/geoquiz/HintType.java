package com.example.geoquiz;

/**
 * Centralised catalogue of hint types available in Daily Country mode.
 *
 * Each constant carries:
 *   - a stable persistence key (used as JSON key, never rename)
 *   - a human-readable display label
 *   - a coin price
 */
public enum HintType {

    REVEAL_CONTINENT  ("continent",   "Reveal Continent",        20),
    REVEAL_FLAG_COLOR ("flag_color",  "Reveal a Flag Color",     25),
    REVEAL_LANGUAGE   ("language",    "Reveal a Language",       25),
    REVEAL_CURRENCY   ("currency",    "Reveal Currency Name",    30),
    REVEAL_CAPITAL    ("capital",     "Reveal Capital City",     35),
    REVEAL_NUM_CLUE   ("num_clue",    "Population/Area Clue",    25),
    REVEAL_FUN_FACT   ("fun_fact",    "Reveal Fun Fact",         40),
    ELIMINATE_THREE   ("eliminate",   "Eliminate 3 Countries",   35);

    /** Stable key used when persisting which hints have been purchased. */
    public final String key;

    /** Short label shown in the UI. */
    public final String label;

    /** Coin cost. */
    public final int price;

    HintType(String key, String label, int price) {
        this.key   = key;
        this.label = label;
        this.price = price;
    }

    /** Look up a HintType by its persistence key; returns null if not found. */
    public static HintType fromKey(String key) {
        if (key == null) return null;
        for (HintType ht : values()) {
            if (ht.key.equals(key)) return ht;
        }
        return null;
    }
}
