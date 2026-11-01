package com.example.geoquiz;

/**
 * The four hint types available in the Guess by the Map game mode.
 *
 * Prices are centralised here — change a price in one place and the entire
 * hint system picks it up automatically.
 *
 * Each constant carries:
 *   key         – stable persistence key (never rename; used in SharedPreferences)
 *   labelResId  – string resource for the localised label shown in the UI
 *   descResId   – string resource for the one-line description shown in the panel
 *   price       – coin cost (all difficulties pay the same price)
 */
public enum MapHintType {

    REVEAL_CONTINENT  ("map_hint_continent",   R.string.map_hint_continent_label,   R.string.map_hint_continent_desc,   20),
    REVEAL_FUN_FACT   ("map_hint_fun_fact",    R.string.map_hint_fun_fact_label,    R.string.map_hint_fun_fact_desc,    35),
    REVEAL_FIRST_LETTER("map_hint_first_letter", R.string.map_hint_first_letter_label, R.string.map_hint_first_letter_desc, 50),
    REVEAL_FLAG_COLORS("map_hint_flag_colors", R.string.map_hint_flag_colors_label, R.string.map_hint_flag_colors_desc, 35);

    /** Stable key for SharedPreferences persistence. */
    public final String key;

    /** String resource ID for the hint label. */
    public final int labelResId;

    /** String resource ID for the short description. */
    public final int descResId;

    /** Coin cost — configure prices here only. */
    public final int price;

    MapHintType(String key, int labelResId, int descResId, int price) {
        this.key        = key;
        this.labelResId = labelResId;
        this.descResId  = descResId;
        this.price      = price;
    }

    public String getLabel(android.content.Context ctx) {
        return ctx.getString(labelResId);
    }

    public String getDesc(android.content.Context ctx) {
        return ctx.getString(descResId);
    }

    /** Look up by persistence key; returns null if not found. */
    public static MapHintType fromKey(String key) {
        if (key == null) return null;
        for (MapHintType t : values()) {
            if (t.key.equals(key)) return t;
        }
        return null;
    }
}
