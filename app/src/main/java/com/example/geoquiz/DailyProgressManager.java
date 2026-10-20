package com.example.geoquiz;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists Daily Country progress to SharedPreferences.
 *
 * Schema
 * ------
 * KEY_CURRENT_LEVEL   int    – 1-based level the player is on  (default 1)
 * KEY_LEVEL_DONE      String – JSON array of completed level numbers, e.g. [1,3,5]
 * KEY_GUESS_HISTORY   String – JSON array of guess objects for the current level
 *
 * Each guess object stored in KEY_GUESS_HISTORY:
 * {
 *   "guessedIso":  "FR",
 *   "targetIso":   "DE"
 * }
 * The full CountryGuess (with all tile results) is rebuilt from the two ISO
 * codes and the live dataset when the activity is restored, so we never store
 * derived comparison data — that stays a pure function of the two Country objects.
 */
public class DailyProgressManager {

    private static final String TAG = "DailyProgressManager";
    private static final String PREFS_NAME  = "daily_country_prefs";
    private static final String KEY_CURRENT_LEVEL = "current_level";
    private static final String KEY_LEVEL_DONE    = "completed_levels";
    private static final String KEY_GUESS_HISTORY = "guess_history";
    /**
     * Persists hint purchase state for the active (frontier) attempt.
     * Stored as a JSON object produced by HintManager.toJson().
     * Key includes the target ISO to guard against stale data, same as guess history.
     */
    private static final String KEY_HINT_STATE    = "hint_state";

    /**
     * Persists the set of level numbers for which the completion reward has
     * already been issued.  Stored as a JSON array of ints identical in format
     * to KEY_LEVEL_DONE.  This is the idempotency guard: if a level appears
     * in this set, the completion reward MUST NOT be granted again regardless
     * of how many times the success dialog fires.
     */
    private static final String KEY_REWARD_ISSUED = "reward_issued_levels";

    private static final int DEFAULT_LEVEL = 1;

    private final SharedPreferences prefs;

    public DailyProgressManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------------------------------
    // Current level
    // -------------------------------------------------------------------------

    /** Returns the saved level (1-based). Falls back to 1 if missing or corrupt.
     *  Also self-heals stale data: if the stored currentLevel is already completed,
     *  it means an older build never advanced it — compute the correct next level. */
    public int loadCurrentLevel() {
        int level = prefs.getInt(KEY_CURRENT_LEVEL, DEFAULT_LEVEL);
        if (level < 1 || level > 195) {
            Log.w(TAG, "Saved level out of range (" + level + "), resetting to 1");
            level = DEFAULT_LEVEL;
        }

        // Self-heal: if every level up to `level` is completed, the real current
        // level is the first incomplete one (or 195 if all done).
        List<Integer> done = loadCompletedLevels();
        while (level <= 195 && done.contains(level)) {
            level++;
        }
        if (level > 195) level = 195;

        // Persist the corrected value so future reads are fast
        int stored = prefs.getInt(KEY_CURRENT_LEVEL, DEFAULT_LEVEL);
        if (level != stored) {
            saveCurrentLevel(level);
        }

        return level;
    }

    public void saveCurrentLevel(int level) {
        prefs.edit().putInt(KEY_CURRENT_LEVEL, level).apply();
    }

    // -------------------------------------------------------------------------
    // Completed levels
    // -------------------------------------------------------------------------

    public List<Integer> loadCompletedLevels() {
        List<Integer> result = new ArrayList<>();
        String json = prefs.getString(KEY_LEVEL_DONE, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getInt(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse completed levels, starting fresh", e);
        }
        return result;
    }

    public void markLevelCompleted(int level) {
        List<Integer> done = loadCompletedLevels();
        if (!done.contains(level)) {
            done.add(level);
            JSONArray arr = new JSONArray();
            for (int l : done) arr.put(l);
            prefs.edit().putString(KEY_LEVEL_DONE, arr.toString()).apply();
        }
        // Advance currentLevel so the next level becomes unlocked in the grid.
        // Only move forward — never overwrite a higher saved value.
        int saved = loadCurrentLevel();
        if (level >= saved && level < 195) {
            saveCurrentLevel(level + 1);
        }
    }

    public boolean isLevelCompleted(int level) {
        return loadCompletedLevels().contains(level);
    }

    // -------------------------------------------------------------------------
    // Guess history (current level only)
    // -------------------------------------------------------------------------

    /**
     * Save the ordered list of ISO codes guessed so far.
     * The list is stored in submission order (oldest first); the activity
     * reverses display order itself.
     */
    public void saveGuessHistory(List<String> guessedIsoCodes, String targetIsoCode) {
        try {
            JSONArray arr = new JSONArray();
            for (String isoCode : guessedIsoCodes) {
                JSONObject obj = new JSONObject();
                obj.put("guessedIso", isoCode);
                obj.put("targetIso",  targetIsoCode);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_GUESS_HISTORY, arr.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Could not save guess history", e);
        }
    }

    /**
     * Returns the list of guessed ISO codes for the saved level.
     * Returns an empty list if the data is missing, corrupt, or belongs to a
     * different target (safety check via targetIsoCode).
     */
    public List<String> loadGuessHistory(String expectedTargetIso) {
        List<String> result = new ArrayList<>();
        String json = prefs.getString(KEY_GUESS_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String savedTarget = obj.optString("targetIso", "");
                // Reject the entire history if target doesn't match (stale data)
                if (!savedTarget.equalsIgnoreCase(expectedTargetIso)) {
                    Log.w(TAG, "Stale guess history (expected target=" + expectedTargetIso
                            + ", found=" + savedTarget + "), discarding");
                    return new ArrayList<>();
                }
                result.add(obj.getString("guessedIso"));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse guess history, starting fresh", e);
            return new ArrayList<>();
        }
        return result;
    }

    /** Clear the current level's guess history (called when advancing to the next level). */
    public void clearGuessHistory() {
        prefs.edit().putString(KEY_GUESS_HISTORY, "[]").apply();
    }

    // -------------------------------------------------------------------------
    // Hint state (current active attempt only)
    // -------------------------------------------------------------------------

    /**
     * Persist the hint state for the current attempt.
     * A target ISO tag is prepended so stale data can be detected on restore.
     *
     * @param hintJson       JSON string produced by HintManager.toJson()
     * @param targetIsoCode  ISO of the country being guessed (e.g. "FR")
     */
    public void saveHintState(String hintJson, String targetIsoCode) {
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put("targetIso", targetIsoCode);
            wrapper.put("hints",     hintJson);
            prefs.edit().putString(KEY_HINT_STATE, wrapper.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Could not save hint state", e);
        }
    }

    /**
     * Load the hint state JSON for the current attempt.
     *
     * @param expectedTargetIso  ISO of the country currently being guessed
     * @return the inner hints JSON string, or "{}" if missing/stale
     */
    public String loadHintState(String expectedTargetIso) {
        String stored = prefs.getString(KEY_HINT_STATE, null);
        if (stored == null || stored.isEmpty()) return "{}";
        try {
            JSONObject wrapper = new JSONObject(stored);
            String savedIso = wrapper.optString("targetIso", "");
            if (!savedIso.equalsIgnoreCase(expectedTargetIso)) {
                Log.w(TAG, "Stale hint state (expected " + expectedTargetIso
                        + ", found " + savedIso + "), discarding");
                return "{}";
            }
            return wrapper.optString("hints", "{}");
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse hint state", e);
            return "{}";
        }
    }

    /** Clear hint state (called when the attempt is reset via Try Again). */
    public void clearHintState() {
        prefs.edit().remove(KEY_HINT_STATE).apply();
    }

    // -------------------------------------------------------------------------
    // Reward issued — idempotency guard
    // -------------------------------------------------------------------------

    /**
     * Record that the completion reward for the given level has been issued.
     * Once recorded, {@link #hasRewardBeenIssued(int)} will return true forever
     * for this level, preventing duplicate grants.
     *
     * @param level 1-based level number
     */
    public void markRewardIssued(int level) {
        List<Integer> issued = loadRewardIssuedLevels();
        if (!issued.contains(level)) {
            issued.add(level);
            JSONArray arr = new JSONArray();
            for (int l : issued) arr.put(l);
            prefs.edit().putString(KEY_REWARD_ISSUED, arr.toString()).apply();
        }
    }

    /**
     * Returns true if the completion reward has already been granted for the
     * given level.  Use this as the sole gate before awarding coins on win.
     *
     * @param level 1-based level number
     */
    public boolean hasRewardBeenIssued(int level) {
        return loadRewardIssuedLevels().contains(level);
    }

    private List<Integer> loadRewardIssuedLevels() {
        List<Integer> result = new ArrayList<>();
        String json = prefs.getString(KEY_REWARD_ISSUED, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getInt(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse reward-issued levels", e);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Full reset
    // -------------------------------------------------------------------------

    /** Full reset — useful for debugging or a "restart all" feature. */
    public void resetAllProgress() {
        prefs.edit().clear().apply();
    }
}
