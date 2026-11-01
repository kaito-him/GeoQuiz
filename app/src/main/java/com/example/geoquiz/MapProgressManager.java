package com.example.geoquiz;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists progress for the Guess by the Map game mode.
 *
 * Each {@link MapDifficulty} has its own independent progress in SharedPreferences.
 * The four difficulties never share or influence each other.
 *
 * SharedPreferences name: {@code "map_mode_prefs"}
 *
 * Per-difficulty keys (prefix = {@link MapDifficulty#prefsPrefix}):
 *
 *   "<prefix>_current_level"   int    – 1-based, first level not yet attempted
 *   "<prefix>_completed"       String – JSON int array of SUCCESSFULLY completed levels
 *   "<prefix>_failed"          String – JSON int array of FAILED levels
 *
 * Level outcome semantics
 * ──────────────────────
 *   COMPLETED  → present in completed set  (green ✓)
 *   FAILED     → present in failed set,    NOT in completed  (red ✗)
 *   UNLOCKED   → level == currentLevel      (wheat ▶, never attempted yet)
 *   LOCKED     → level > currentLevel       (grey 🔒)
 *
 * A level solved after a previous failure moves from failed → completed.
 * A completed level can never become failed.
 *
 * Unlock rule
 * ───────────
 * The "current level" pointer advances whenever a level is concluded (won OR
 * lost) for the first time, so the player can always continue forward even
 * after failing a level.
 */
public class MapProgressManager {

    private static final String TAG        = "MapProgressManager";
    private static final String PREFS_NAME = "map_mode_prefs";

    private static final String SUFFIX_CURRENT       = "_current_level";
    private static final String SUFFIX_COMPLETED     = "_completed";
    private static final String SUFFIX_FAILED        = "_failed";
    private static final String SUFFIX_REWARD_ISSUED = "_reward_issued";

    /** The three possible permanent outcomes for a single level. */
    public enum LevelOutcome {
        /** Never concluded — first attempt not yet finished. */
        UNLOCKED,
        /** Player guessed correctly at least once. */
        COMPLETED,
        /** Player exhausted all guesses without a correct answer (and has not since solved it). */
        FAILED,
        /** Level number is beyond the current unlock pointer. */
        LOCKED
    }

    private final SharedPreferences prefs;

    public MapProgressManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------------------------------
    // Current level pointer
    // -------------------------------------------------------------------------

    /**
     * The first level the player has not yet successfully concluded (won or lost).
     * Always ≥ 1.
     */
    public int loadCurrentLevel(MapDifficulty difficulty) {
        String key   = difficulty.prefsPrefix + SUFFIX_CURRENT;
        int    level = prefs.getInt(key, 1);
        return Math.max(level, 1);
    }

    public void saveCurrentLevel(MapDifficulty difficulty, int level) {
        prefs.edit().putInt(difficulty.prefsPrefix + SUFFIX_CURRENT, level).apply();
    }

    // -------------------------------------------------------------------------
    // Completed levels (won at least once)
    // -------------------------------------------------------------------------

    public List<Integer> loadCompletedLevels(MapDifficulty difficulty) {
        return loadIntList(difficulty.prefsPrefix + SUFFIX_COMPLETED);
    }

    // -------------------------------------------------------------------------
    // Failed levels (lost, never subsequently won)
    // -------------------------------------------------------------------------

    public List<Integer> loadFailedLevels(MapDifficulty difficulty) {
        return loadIntList(difficulty.prefsPrefix + SUFFIX_FAILED);
    }

    // -------------------------------------------------------------------------
    // Permanent outcome query
    // -------------------------------------------------------------------------

    /**
     * Returns the permanent {@link LevelOutcome} for a given level number.
     *
     * Priority: COMPLETED > FAILED > UNLOCKED > LOCKED.
     */
    public LevelOutcome getOutcome(MapDifficulty difficulty, int level) {
        int current = loadCurrentLevel(difficulty);

        if (loadCompletedLevels(difficulty).contains(level)) return LevelOutcome.COMPLETED;
        if (loadFailedLevels(difficulty).contains(level))    return LevelOutcome.FAILED;
        if (level == current)                                return LevelOutcome.UNLOCKED;
        if (level < current)                                 return LevelOutcome.FAILED; // safety: should be in list
        return LevelOutcome.LOCKED;
    }

    // -------------------------------------------------------------------------
    // Recording outcomes
    // -------------------------------------------------------------------------

    /**
     * Record that the player guessed correctly on this level.
     *
     * • Adds to completed set (idempotent).
     * • Removes from failed set (a solved level is no longer failed).
     * • Advances the current-level pointer so the next level is unlocked.
     *
     * @param difficulty  the difficulty tier
     * @param level       1-based level within the difficulty
     * @param totalLevels total number of levels in this difficulty
     */
    public void markLevelCompleted(MapDifficulty difficulty, int level, int totalLevels) {
        // Add to completed
        addToSet(difficulty.prefsPrefix + SUFFIX_COMPLETED, level);

        // Remove from failed (if this level was previously failed)
        removeFromSet(difficulty.prefsPrefix + SUFFIX_FAILED, level);

        // Advance the unlock pointer
        advanceCurrentLevel(difficulty, level, totalLevels);
    }

    /**
     * Record that the player exhausted all guesses on this level without solving it.
     *
     * • Adds to failed set only if the level is NOT already completed.
     * • Advances the current-level pointer so the player can move on.
     *
     * @param difficulty  the difficulty tier
     * @param level       1-based level within the difficulty
     * @param totalLevels total number of levels in this difficulty
     */
    public void markLevelFailed(MapDifficulty difficulty, int level, int totalLevels) {
        // Never overwrite a completed status with failed
        if (!loadCompletedLevels(difficulty).contains(level)) {
            addToSet(difficulty.prefsPrefix + SUFFIX_FAILED, level);
        }

        // Advance the unlock pointer regardless of outcome
        advanceCurrentLevel(difficulty, level, totalLevels);
    }

    // -------------------------------------------------------------------------
    // Convenience queries used by the adapter
    // -------------------------------------------------------------------------

    public boolean isLevelCompleted(MapDifficulty difficulty, int level) {
        return loadCompletedLevels(difficulty).contains(level);
    }

    public boolean isLevelFailed(MapDifficulty difficulty, int level) {
        return loadFailedLevels(difficulty).contains(level)
                && !loadCompletedLevels(difficulty).contains(level);
    }

    /** Count of levels that have been SUCCESSFULLY completed. */
    public int completedCount(MapDifficulty difficulty) {
        return loadCompletedLevels(difficulty).size();
    }

    // -------------------------------------------------------------------------
    // Reward-issued idempotency guard
    // -------------------------------------------------------------------------

    /**
     * Record that the first-completion coin reward has been issued for this level.
     * Once recorded, {@link #hasRewardBeenIssued} returns true forever for this
     * level, preventing duplicate coin grants across replays and activity restarts.
     */
    public void markRewardIssued(MapDifficulty difficulty, int level) {
        addToSet(difficulty.prefsPrefix + SUFFIX_REWARD_ISSUED, level);
    }

    /**
     * Returns true if the first-completion reward has already been granted for
     * the given level. Use this as the sole gate before awarding coins on win.
     */
    public boolean hasRewardBeenIssued(MapDifficulty difficulty, int level) {
        return loadIntList(difficulty.prefsPrefix + SUFFIX_REWARD_ISSUED).contains(level);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Advance currentLevel past {@code level} if it is currently at or behind it. */
    private void advanceCurrentLevel(MapDifficulty difficulty, int level, int totalLevels) {
        int current = loadCurrentLevel(difficulty);
        if (level >= current && level < totalLevels) {
            saveCurrentLevel(difficulty, level + 1);
        }
    }

    private List<Integer> loadIntList(String key) {
        List<Integer> result = new ArrayList<>();
        String json = prefs.getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) result.add(arr.getInt(i));
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse int list for key=" + key, e);
        }
        return result;
    }

    private void addToSet(String key, int value) {
        List<Integer> list = loadIntList(key);
        if (!list.contains(value)) {
            list.add(value);
            saveIntList(key, list);
        }
    }

    private void removeFromSet(String key, int value) {
        List<Integer> list = loadIntList(key);
        if (list.remove(Integer.valueOf(value))) {
            saveIntList(key, list);
        }
    }

    private void saveIntList(String key, List<Integer> list) {
        JSONArray arr = new JSONArray();
        for (int v : list) arr.put(v);
        prefs.edit().putString(key, arr.toString()).apply();
    }
}
