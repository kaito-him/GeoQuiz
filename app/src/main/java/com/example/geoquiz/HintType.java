package com.example.geoquiz;

import android.content.Context;

/**
 * Centralised catalogue of hint types available in Daily Country mode.
 *
 * Each constant carries:
 *   - a stable persistence key  (used as JSON key — never rename)
 *   - a string resource ID      (resolved at runtime for the current locale)
 *   - a coin price
 */
public enum HintType {

    REVEAL_CONTINENT  ("continent",  R.string.hint_reveal_continent,  20),
    REVEAL_FLAG_COLOR ("flag_color", R.string.hint_reveal_flag_color, 25),
    REVEAL_LANGUAGE   ("language",   R.string.hint_reveal_language,   50),
    REVEAL_CURRENCY   ("currency",   R.string.hint_reveal_currency,   30),
    REVEAL_CAPITAL    ("capital",    R.string.hint_reveal_capital,    60),
    REVEAL_NUM_CLUE   ("num_clue",   R.string.hint_reveal_num_clue,   25),
    REVEAL_FUN_FACT   ("fun_fact",   R.string.hint_reveal_fun_fact,   40);

    /** Stable key used when persisting which hints have been purchased. */
    public final String key;

    /** String resource ID for the label (locale-aware). */
    public final int labelResId;

    /** Coin cost. */
    public final int price;

    HintType(String key, int labelResId, int price) {
        this.key        = key;
        this.labelResId = labelResId;
        this.price      = price;
    }

    /** Return the localised label for the current locale. */
    public String getLabel(Context context) {
        return context.getString(labelResId);
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
