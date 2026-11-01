package com.example.geoquiz;

/**
 * The four difficulty tiers for the Guess by the Map game mode.
 *
 * Each constant carries:
 *  - the String key used in {@link Country#getDifficultyCategory()} (case-insensitive match)
 *  - a string resource ID for the localised label
 *  - the SharedPreferences prefix used by {@link MapProgressManager}
 */
public enum MapDifficulty {

    EASY         ("Easy",         R.string.difficulty_easy,         "map_easy"),
    INTERMEDIATE ("Intermediate", R.string.difficulty_intermediate, "map_intermediate"),
    HARD         ("Hard",         R.string.difficulty_hard,         "map_hard"),
    EXPERT       ("Expert",       R.string.difficulty_expert,       "map_expert");

    /** Matches the value of {@code "Difficulty Category"} in countries.json. */
    public final String dataKey;

    /** String resource for the localised label. */
    public final int labelResId;

    /** Prefix used in SharedPreferences keys for this difficulty. */
    public final String prefsPrefix;

    MapDifficulty(String dataKey, int labelResId, String prefsPrefix) {
        this.dataKey     = dataKey;
        this.labelResId  = labelResId;
        this.prefsPrefix = prefsPrefix;
    }

    /** Return the localised label. */
    public String getLabel(android.content.Context context) {
        return context.getString(labelResId);
    }

    /**
     * Look up a MapDifficulty whose {@link #dataKey} matches the given string
     * (case-insensitive). Returns {@code null} if not found.
     */
    public static MapDifficulty fromDataKey(String key) {
        if (key == null) return null;
        for (MapDifficulty d : values()) {
            if (d.dataKey.equalsIgnoreCase(key.trim())) return d;
        }
        return null;
    }
}
