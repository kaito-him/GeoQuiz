package com.example.geoquiz;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Manages hint availability, eligibility, purchase tracking, and persistence
 * for a single Daily Country attempt.
 *
 * Two hint SLOTS are available per attempt:
 *   Slot 1 — unlocks after the 4th submitted guess (available before guess 5)
 *   Slot 2 — unlocks after the 8th submitted guess (available before guess 9)
 *
 * Each slot may be used at most once. Once used, the purchased hint's revealed
 * value is stored so it can be restored after an app restart.
 *
 * Eligibility rules prevent offering hints whose information is already known
 * from the submitted guesses.  The "known clues" model is updated after every
 * submitted guess and after every purchased hint.
 *
 * This class does NOT touch coins — the caller (DailyCountryActivity) handles
 * the CoinManager transaction and calls recordHintPurchase() only after the
 * coins have been successfully deducted.
 */
public class HintManager {

    private static final String TAG = "HintManager";

    // Slot unlock thresholds: slot unlocks AFTER this many guesses have been submitted.
    public static final int SLOT1_UNLOCK_AFTER = 4;
    public static final int SLOT2_UNLOCK_AFTER = 8;

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Describes whether a hint can be offered right now.
     */
    public enum EligibilityStatus {
        /** Hint can be purchased. */
        AVAILABLE,
        /** The information this hint would reveal is already known from guesses/prior hints. */
        ALREADY_KNOWN,
        /** Not enough valid candidates for an elimination hint. */
        CANNOT_ELIMINATE,
        /** The hint slot for this purchase has not unlocked yet. */
        SLOT_NOT_UNLOCKED,
        /** Both hint slots have already been used. */
        NO_SLOTS_REMAINING,
        /** The assigned slot for this hint has already been consumed. */
        SLOT_USED
    }

    /**
     * Holds the result text of a purchased hint, plus metadata.
     */
    public static class PurchasedHint {
        public final HintType type;
        public final String   revealedText;
        public final int      slotIndex; // 1 or 2

        public PurchasedHint(HintType type, String revealedText, int slotIndex) {
            this.type         = type;
            this.revealedText = revealedText;
            this.slotIndex    = slotIndex;
        }
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final Country           target;
    private final CountryRepository repository;

    /** Purchased hints indexed by slot (1 or 2). Null = slot not yet used. */
    private final PurchasedHint[]   purchasedBySlot = new PurchasedHint[3]; // index 1 & 2 used

    // Known clues derived from all submitted guesses (and purchased hints)
    private String          knownContinent   = null;   // non-null once confirmed green
    private String          knownCurrencyCode = null;  // non-null once confirmed green
    private final Set<String> knownLanguages  = new HashSet<>(); // all confirmed/revealed
    private final Set<String> knownFlagColors = new HashSet<>(); // all confirmed/revealed
    private boolean         capitalRevealed  = false;
    private boolean         funFactRevealed  = false;
    private boolean         numClueRevealed  = false;

    /** ISO codes that have already been guessed (lower-case). */
    private final Set<String> guessedIsos = new HashSet<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public HintManager(Country target, CountryRepository repository) {
        this.target     = target;
        this.repository = repository;
    }

    // -------------------------------------------------------------------------
    // Slot status
    // -------------------------------------------------------------------------

    /** @return true if hint slot 1 is unlocked (≥ SLOT1_UNLOCK_AFTER guesses submitted). */
    public boolean isSlot1Unlocked(int submittedGuessCount) {
        return submittedGuessCount >= SLOT1_UNLOCK_AFTER;
    }

    /** @return true if hint slot 2 is unlocked (≥ SLOT2_UNLOCK_AFTER guesses submitted). */
    public boolean isSlot2Unlocked(int submittedGuessCount) {
        return submittedGuessCount >= SLOT2_UNLOCK_AFTER;
    }

    /** @return true if slot 1 has already been consumed. */
    public boolean isSlot1Used() {
        return purchasedBySlot[1] != null;
    }

    /** @return true if slot 2 has already been consumed. */
    public boolean isSlot2Used() {
        return purchasedBySlot[2] != null;
    }

    /** Return the hint purchased in the given slot, or null. */
    public PurchasedHint getPurchasedHint(int slotIndex) {
        if (slotIndex < 1 || slotIndex > 2) return null;
        return purchasedBySlot[slotIndex];
    }

    /**
     * Returns which slot (1 or 2) would be consumed for the next hint purchase,
     * or -1 if both slots are used.
     */
    public int nextAvailableSlot(int submittedGuessCount) {
        if (!isSlot1Used()) {
            if (isSlot1Unlocked(submittedGuessCount)) return 1;
            return -1; // slot 1 not yet unlocked — slot 2 not reachable before 1 is used
        }
        if (!isSlot2Used()) {
            if (isSlot2Unlocked(submittedGuessCount)) return 2;
            return -1;
        }
        return -1; // both used
    }

    // -------------------------------------------------------------------------
    // Eligibility
    // -------------------------------------------------------------------------

    /**
     * Check whether a specific hint can currently be purchased.
     *
     * @param type               the hint to evaluate
     * @param submittedGuessCount how many guesses have been submitted so far
     * @return an EligibilityStatus describing the result
     */
    public EligibilityStatus checkEligibility(HintType type, int submittedGuessCount) {
        // Check slot availability first
        if (!isSlot1Unlocked(submittedGuessCount) && !isSlot2Unlocked(submittedGuessCount)) {
            return EligibilityStatus.SLOT_NOT_UNLOCKED;
        }
        if (isSlot1Used() && isSlot2Used()) {
            return EligibilityStatus.NO_SLOTS_REMAINING;
        }
        // Determine next slot
        int nextSlot = nextAvailableSlot(submittedGuessCount);
        if (nextSlot == -1) {
            // Slot not yet unlocked
            return EligibilityStatus.SLOT_NOT_UNLOCKED;
        }

        // Per-hint information eligibility
        switch (type) {
            case REVEAL_CONTINENT:
                if (knownContinent != null) return EligibilityStatus.ALREADY_KNOWN;
                break;

            case REVEAL_FLAG_COLOR: {
                // Disable if ALL target flag colors are already known
                Set<String> targetColors = lowerSet(target.getFlagColors());
                if (targetColors.isEmpty()) return EligibilityStatus.ALREADY_KNOWN;
                Set<String> unknown = new HashSet<>(targetColors);
                unknown.removeAll(knownFlagColors);
                if (unknown.isEmpty()) return EligibilityStatus.ALREADY_KNOWN;
                break;
            }

            case REVEAL_LANGUAGE: {
                // Disable if ALL target languages are already known
                Set<String> targetLangs = lowerSet(target.getMainLanguages());
                if (targetLangs.isEmpty()) return EligibilityStatus.ALREADY_KNOWN;
                Set<String> unknown = new HashSet<>(targetLangs);
                unknown.removeAll(knownLanguages);
                if (unknown.isEmpty()) return EligibilityStatus.ALREADY_KNOWN;
                break;
            }

            case REVEAL_CURRENCY:
                if (knownCurrencyCode != null &&
                    knownCurrencyCode.equalsIgnoreCase(target.getCurrencyCode())) {
                    return EligibilityStatus.ALREADY_KNOWN;
                }
                break;

            case REVEAL_CAPITAL:
                if (capitalRevealed) return EligibilityStatus.ALREADY_KNOWN;
                break;

            case REVEAL_NUM_CLUE:
                if (numClueRevealed) return EligibilityStatus.ALREADY_KNOWN;
                break;

            case REVEAL_FUN_FACT:
                if (funFactRevealed) return EligibilityStatus.ALREADY_KNOWN;
                break;

            case ELIMINATE_THREE: {
                // Need at least 3 valid candidates to eliminate
                List<Country> candidates = getEliminationCandidates();
                if (candidates.size() < 3) return EligibilityStatus.CANNOT_ELIMINATE;
                break;
            }
        }

        return EligibilityStatus.AVAILABLE;
    }

    // -------------------------------------------------------------------------
    // Generating hint reveal text
    // -------------------------------------------------------------------------

    /**
     * Generate the text that should be displayed when a hint is revealed.
     * Call only after checkEligibility() returns AVAILABLE.
     *
     * @param type the hint type to reveal
     * @return a non-null reveal string, or null if the hint cannot be generated safely
     */
    public String generateRevealText(HintType type) {
        switch (type) {
            case REVEAL_CONTINENT:
                return target.getContinent();

            case REVEAL_FLAG_COLOR: {
                Set<String> targetColors = lowerSet(target.getFlagColors());
                Set<String> unknown = new HashSet<>(targetColors);
                unknown.removeAll(knownFlagColors);
                if (unknown.isEmpty()) return null;
                // Pick a random unknown color; use original casing from country data
                for (String orig : target.getFlagColors()) {
                    if (unknown.contains(orig.toLowerCase())) return orig;
                }
                return null;
            }

            case REVEAL_LANGUAGE: {
                Set<String> targetLangs = lowerSet(target.getMainLanguages());
                Set<String> unknown = new HashSet<>(targetLangs);
                unknown.removeAll(knownLanguages);
                if (unknown.isEmpty()) return null;
                for (String orig : target.getMainLanguages()) {
                    if (unknown.contains(orig.toLowerCase())) return orig;
                }
                return null;
            }

            case REVEAL_CURRENCY:
                return target.getCurrencyName();

            case REVEAL_CAPITAL:
                return target.getCapitalName();

            case REVEAL_NUM_CLUE:
                return generateNumericClue();

            case REVEAL_FUN_FACT:
                return target.getFunFact();

            case ELIMINATE_THREE: {
                List<Country> candidates = getEliminationCandidates();
                if (candidates.size() < 3) return null;
                Collections.shuffle(candidates, new Random());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 3; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(candidates.get(i).getCountry());
                }
                return sb.toString();
            }

            default:
                return null;
        }
    }

    /**
     * Builds a useful population/area range clue without revealing the exact value.
     * Picks whichever dimension gives a narrower bracket based on what is already known.
     */
    private String generateNumericClue() {
        long   pop  = target.getPopulation();
        double area = target.getArea();

        // Population bracket: pick a round-number tier
        String popStr;
        if (pop < 100_000) {
            popStr = "under 100,000";
        } else if (pop < 500_000) {
            popStr = "100K – 500K";
        } else if (pop < 1_000_000) {
            popStr = "500K – 1 million";
        } else if (pop < 5_000_000) {
            popStr = "1M – 5M";
        } else if (pop < 10_000_000) {
            popStr = "5M – 10M";
        } else if (pop < 50_000_000) {
            popStr = "10M – 50M";
        } else if (pop < 100_000_000) {
            popStr = "50M – 100M";
        } else if (pop < 500_000_000) {
            popStr = "100M – 500M";
        } else {
            popStr = "over 500M";
        }

        // Area bracket
        String areaStr;
        if (area < 1_000) {
            areaStr = "under 1,000 km²";
        } else if (area < 10_000) {
            areaStr = "1K – 10K km²";
        } else if (area < 100_000) {
            areaStr = "10K – 100K km²";
        } else if (area < 500_000) {
            areaStr = "100K – 500K km²";
        } else if (area < 1_000_000) {
            areaStr = "500K – 1M km²";
        } else if (area < 5_000_000) {
            areaStr = "1M – 5M km²";
        } else {
            areaStr = "over 5M km²";
        }

        return "Population: " + popStr + "\nArea: " + areaStr;
    }

    /**
     * Returns countries that are valid elimination candidates:
     *   - Not the target
     *   - Not already guessed
     *   - Not already in a prior elimination hint reveal
     */
    private List<Country> getEliminationCandidates() {
        List<Country> all = repository.getAllCountries();
        List<Country> candidates = new ArrayList<>();
        String targetIso = target.getIsoCode().toLowerCase();

        // Collect ISOs already used as elimination suggestions so we don't re-suggest them
        Set<String> eliminatedIsos = new HashSet<>();
        for (int slot = 1; slot <= 2; slot++) {
            PurchasedHint ph = purchasedBySlot[slot];
            if (ph != null && ph.type == HintType.ELIMINATE_THREE) {
                // Stored text is comma-separated country names
                for (String name : ph.revealedText.split(",")) {
                    Country c = repository.findCountryByName(name.trim());
                    if (c != null) eliminatedIsos.add(c.getIsoCode().toLowerCase());
                }
            }
        }

        for (Country c : all) {
            String iso = c.getIsoCode().toLowerCase();
            if (iso.equals(targetIso)) continue;
            if (guessedIsos.contains(iso)) continue;
            if (eliminatedIsos.contains(iso)) continue;
            candidates.add(c);
        }
        return candidates;
    }

    // -------------------------------------------------------------------------
    // Updating known clues from submitted guesses
    // -------------------------------------------------------------------------

    /**
     * Call this AFTER every submitted guess so the eligibility model stays current.
     *
     * @param guess the CountryGuess produced by CountryComparator.compare()
     */
    public void onGuessMade(CountryGuess guess) {
        if (guess == null) return;
        String iso = guess.getGuessedCountry().getIsoCode().toLowerCase();
        guessedIsos.add(iso);
        updateKnownCluesFromGuess(guess);
    }

    private void updateKnownCluesFromGuess(CountryGuess guess) {
        // Continent — green tile means it's now known
        CountryGuess.ComparisonResult contResult = guess.getContinentResult();
        if (contResult != null &&
            contResult.getStatus() == CountryGuess.ComparisonResult.Status.CORRECT) {
            knownContinent = target.getContinent();
        }

        // Currency — green tile means it's now known
        CountryGuess.ComparisonResult currResult = guess.getCurrencyResult();
        if (currResult != null &&
            currResult.getStatus() == CountryGuess.ComparisonResult.Status.CORRECT) {
            knownCurrencyCode = target.getCurrencyCode();
        }

        // Languages — add any language from the guessed country that is ALSO in the target
        // (shown as green or orange on the language tile means at least one matches)
        CountryGuess.ComparisonResult langResult = guess.getLanguageResult();
        if (langResult != null &&
            langResult.getStatus() != CountryGuess.ComparisonResult.Status.WRONG) {
            // At least one language in common — find out which ones
            List<String> guessedLangs = guess.getGuessedCountry().getMainLanguages();
            Set<String> targetLangsLower = lowerSet(target.getMainLanguages());
            if (guessedLangs != null) {
                for (String lang : guessedLangs) {
                    if (targetLangsLower.contains(lang.toLowerCase())) {
                        knownLanguages.add(lang.toLowerCase());
                    }
                }
            }
            // Green (CORRECT) = sets are identical, so ALL target languages are now known
            if (langResult.getStatus() == CountryGuess.ComparisonResult.Status.CORRECT) {
                if (target.getMainLanguages() != null) {
                    for (String l : target.getMainLanguages()) knownLanguages.add(l.toLowerCase());
                }
            }
        }

        // Flag colors — add any matching color
        CountryGuess.FlagColorsResult flagResult = guess.getFlagColorsResult();
        if (flagResult != null &&
            flagResult.getStatus() != CountryGuess.FlagColorsResult.Status.NO_MATCH) {
            List<String> guessedColors = guess.getGuessedCountry().getFlagColors();
            Set<String> targetColorsLower = lowerSet(target.getFlagColors());
            if (guessedColors != null) {
                for (String color : guessedColors) {
                    if (targetColorsLower.contains(color.toLowerCase())) {
                        knownFlagColors.add(color.toLowerCase());
                    }
                }
            }
            if (flagResult.getStatus() == CountryGuess.FlagColorsResult.Status.FULL_MATCH) {
                if (target.getFlagColors() != null) {
                    for (String c : target.getFlagColors()) knownFlagColors.add(c.toLowerCase());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Recording purchased hints (call after successful coin deduction)
    // -------------------------------------------------------------------------

    /**
     * Record that a hint has been purchased and reveal its value.
     * Call this ONLY after coins have been successfully deducted.
     *
     * @param type         the purchased hint type
     * @param revealedText the text generated by generateRevealText()
     * @param slotIndex    1 or 2
     */
    public void recordHintPurchase(HintType type, String revealedText, int slotIndex) {
        if (slotIndex < 1 || slotIndex > 2) return;
        purchasedBySlot[slotIndex] = new PurchasedHint(type, revealedText, slotIndex);

        // Update known-clues model so subsequent eligibility checks are accurate
        updateKnownCluesFromHint(type, revealedText);
    }

    private void updateKnownCluesFromHint(HintType type, String revealedText) {
        if (revealedText == null) return;
        switch (type) {
            case REVEAL_CONTINENT:
                knownContinent = revealedText;
                break;
            case REVEAL_FLAG_COLOR:
                knownFlagColors.add(revealedText.toLowerCase());
                break;
            case REVEAL_LANGUAGE:
                knownLanguages.add(revealedText.toLowerCase());
                break;
            case REVEAL_CURRENCY:
                // The hint reveals the currency NAME; mark the code as known so
                // the hint can't be re-purchased
                knownCurrencyCode = target.getCurrencyCode();
                break;
            case REVEAL_CAPITAL:
                capitalRevealed = true;
                break;
            case REVEAL_NUM_CLUE:
                numClueRevealed = true;
                break;
            case REVEAL_FUN_FACT:
                funFactRevealed = true;
                break;
            case ELIMINATE_THREE:
                // No clue-model update needed; candidates list handles re-elimination
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Persistence — save to / restore from DailyProgressManager
    // -------------------------------------------------------------------------

    /**
     * Serialize the hint state to a JSON string suitable for SharedPreferences.
     * Format:
     * {
     *   "slot1": { "type": "capital", "text": "Paris" },
     *   "slot2": { "type": "fun_fact", "text": "..." }
     * }
     */
    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            for (int slot = 1; slot <= 2; slot++) {
                PurchasedHint ph = purchasedBySlot[slot];
                if (ph != null) {
                    JSONObject obj = new JSONObject();
                    obj.put("type", ph.type.key);
                    obj.put("text", ph.revealedText);
                    root.put("slot" + slot, obj);
                }
            }
            return root.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize hints", e);
            return "{}";
        }
    }

    /**
     * Restore hint state from a JSON string previously produced by toJson().
     * Also rebuilds the known-clues model from the restored purchases.
     *
     * @param json the JSON string from SharedPreferences
     */
    public void fromJson(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) return;
        try {
            JSONObject root = new JSONObject(json);
            for (int slot = 1; slot <= 2; slot++) {
                String key = "slot" + slot;
                if (root.has(key)) {
                    JSONObject obj = root.getJSONObject(key);
                    String typeKey    = obj.optString("type", null);
                    String revealedText = obj.optString("text", null);
                    HintType type = HintType.fromKey(typeKey);
                    if (type != null && revealedText != null) {
                        purchasedBySlot[slot] = new PurchasedHint(type, revealedText, slot);
                        updateKnownCluesFromHint(type, revealedText);
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to deserialize hints", e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Set<String> lowerSet(List<String> list) {
        Set<String> set = new HashSet<>();
        if (list != null) {
            for (String s : list) set.add(s.toLowerCase());
        }
        return set;
    }
}
