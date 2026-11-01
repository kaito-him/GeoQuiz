package com.example.geoquiz;

/**
 * Pure stateless reward calculator for the Guess by the Map game mode.
 *
 * Reward formula
 * ──────────────
 *   speedBonus  = floor(maxSpeedBonus × max(0, 1 − elapsedSeconds / targetSeconds))
 *   totalReward = baseReward + speedBonus
 *
 * Target times are reward benchmarks only — NOT countdowns or failure triggers.
 * A player who takes longer than the target simply earns the base reward with
 * no speed bonus. The bonus is strictly clamped to [0, maxSpeedBonus].
 *
 * Per-difficulty configuration (immutable):
 *
 *   Difficulty      Base    MaxBonus   TargetSecs
 *   ──────────────────────────────────────────────
 *   Easy             40       20          20
 *   Intermediate     60       30          35
 *   Hard             80       40          50
 *   Expert          100       50          60
 *
 * This class has no Android dependencies and is fully unit-testable on the JVM.
 */
public final class MapRewardCalculator {

    private MapRewardCalculator() { /* static utility */ }

    // ── Per-difficulty config ─────────────────────────────────────────────

    /** Guaranteed base reward for a first-time successful completion. */
    public static int baseReward(MapDifficulty diff) {
        switch (diff) {
            case EASY:         return  40;
            case INTERMEDIATE: return  60;
            case HARD:         return  80;
            case EXPERT:       return 100;
            default:           return  40;
        }
    }

    /** Maximum possible speed bonus on top of the base reward. */
    public static int maxSpeedBonus(MapDifficulty diff) {
        switch (diff) {
            case EASY:         return 20;
            case INTERMEDIATE: return 30;
            case HARD:         return 40;
            case EXPERT:       return 50;
            default:           return 20;
        }
    }

    /** Target time in seconds — achieving this earns the full speed bonus. */
    public static int targetSeconds(MapDifficulty diff) {
        switch (diff) {
            case EASY:         return  20;
            case INTERMEDIATE: return  35;
            case HARD:         return  50;
            case EXPERT:       return  60;
            default:           return  20;
        }
    }

    // ── Computation ──────────────────────────────────────────────────────

    /**
     * Compute the total coin reward for a first-time successful completion.
     *
     * @param diff      the difficulty tier
     * @param elapsedMs elapsed time in milliseconds recorded by the gameplay stopwatch
     * @return total coins to award (base + speed bonus, never negative)
     */
    public static int compute(MapDifficulty diff, long elapsedMs) {
        int   base        = baseReward(diff);
        int   maxBonus    = maxSpeedBonus(diff);
        int   targetSecs  = targetSeconds(diff);
        float elapsedSecs = elapsedMs / 1000f;

        // Clamp ratio to [0, 1] so long solving times never increase the bonus
        float ratio = Math.max(0f, 1f - elapsedSecs / targetSecs);
        int   bonus = (int) Math.floor(maxBonus * ratio);

        return base + bonus;
    }

    /**
     * Convenience: compute the speed bonus component alone (for display in
     * the result dialog breakdown row).
     */
    public static int speedBonus(MapDifficulty diff, long elapsedMs) {
        return compute(diff, elapsedMs) - baseReward(diff);
    }
}
