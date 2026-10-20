package com.example.geoquiz;

/**
 * Pure stateless utility that computes the completion coin reward for a
 * Daily Country attempt.
 *
 * Rules
 * -----
 * Base reward:            100 coins (always, for a first-time completion)
 * Hint penalty:           -10 coins per hint slot used
 * Performance bonus:      added only when ZERO hints were used
 *
 * Performance bonus table (no-hint attempts):
 *   1 guess  → +50   (total 150)
 *   2 guesses → +40  (total 140)
 *   3 guesses → +30  (total 130)
 *   4 guesses → +20  (total 120)
 *   5 guesses → +15  (total 115)
 *   6 guesses → +10  (total 110)
 *   7 guesses → +5   (total 105)
 *   8+ guesses → +0  (total 100)
 *
 * When hints are used:
 *   reward = max(0, 100 - 10 * hintsUsed)
 *   No performance bonus is applied.
 *
 * This class has no Android dependencies and is fully unit-testable on the JVM.
 */
public final class RewardCalculator {

    /** Base reward for any first-time completion, before bonuses or penalties. */
    public static final int BASE_REWARD = 100;

    /** Coin deduction per hint slot consumed. */
    public static final int HINT_PENALTY = 10;

    private RewardCalculator() { /* static utility */ }

    /**
     * Compute the completion reward.
     *
     * @param guessesUsed number of guesses submitted in the winning attempt (≥ 1)
     * @param hintsUsed   number of hint slots purchased (0, 1, or 2)
     * @return coins to award; never negative
     */
    public static int compute(int guessesUsed, int hintsUsed) {
        if (hintsUsed < 0) hintsUsed = 0;
        if (guessesUsed < 1) guessesUsed = 1;

        int reward;
        if (hintsUsed > 0) {
            // Hint penalty — no performance bonus
            reward = Math.max(0, BASE_REWARD - HINT_PENALTY * hintsUsed);
        } else {
            // No hints — apply performance bonus
            reward = BASE_REWARD + performanceBonus(guessesUsed);
        }
        return reward;
    }

    /**
     * Performance bonus awarded when no hints were used.
     *
     * @param guessesUsed winning guess number (1-indexed)
     * @return bonus coins (0 or positive)
     */
    public static int performanceBonus(int guessesUsed) {
        if (guessesUsed <= 1) return 50;
        if (guessesUsed == 2) return 40;
        if (guessesUsed == 3) return 30;
        if (guessesUsed == 4) return 20;
        if (guessesUsed == 5) return 15;
        if (guessesUsed == 6) return 10;
        if (guessesUsed == 7) return 5;
        return 0; // 8, 9, 10
    }
}
