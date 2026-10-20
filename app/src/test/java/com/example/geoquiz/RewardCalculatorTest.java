package com.example.geoquiz;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for RewardCalculator covering all cases from the prompt spec.
 *
 * These are pure JVM tests — no Android context required.
 */
public class RewardCalculatorTest {

    // -------------------------------------------------------------------------
    // No-hint completions — performance bonus cases
    // -------------------------------------------------------------------------

    @Test
    public void win_in_1_guess_no_hints_earns_150() {
        assertEquals(150, RewardCalculator.compute(1, 0));
    }

    @Test
    public void win_in_2_guesses_no_hints_earns_140() {
        assertEquals(140, RewardCalculator.compute(2, 0));
    }

    @Test
    public void win_in_3_guesses_no_hints_earns_130() {
        // Spec example: "Win in 3 guesses without hints: 130 coins"
        assertEquals(130, RewardCalculator.compute(3, 0));
    }

    @Test
    public void win_in_4_guesses_no_hints_earns_120() {
        assertEquals(120, RewardCalculator.compute(4, 0));
    }

    @Test
    public void win_in_5_guesses_no_hints_earns_115() {
        assertEquals(115, RewardCalculator.compute(5, 0));
    }

    @Test
    public void win_in_6_guesses_no_hints_earns_110() {
        assertEquals(110, RewardCalculator.compute(6, 0));
    }

    @Test
    public void win_in_7_guesses_no_hints_earns_105() {
        // Spec example: "Win in 7 guesses without hints: 105 coins"
        assertEquals(105, RewardCalculator.compute(7, 0));
    }

    @Test
    public void win_in_8_guesses_no_hints_earns_100() {
        // Spec example: "Complete a new level in 8 guesses without hints: earn 100 coins"
        assertEquals(100, RewardCalculator.compute(8, 0));
    }

    @Test
    public void win_in_9_guesses_no_hints_earns_100() {
        assertEquals(100, RewardCalculator.compute(9, 0));
    }

    @Test
    public void win_in_10_guesses_no_hints_earns_100() {
        assertEquals(100, RewardCalculator.compute(10, 0));
    }

    // -------------------------------------------------------------------------
    // Hint penalty cases
    // -------------------------------------------------------------------------

    @Test
    public void one_hint_used_win_earns_90() {
        // Spec example: "Use one hint, then win: earn 90 completion coins"
        assertEquals(90, RewardCalculator.compute(5, 1));
    }

    @Test
    public void two_hints_used_win_earns_80() {
        // Spec example: "Use two hints, then win: earn 80 completion coins"
        assertEquals(80, RewardCalculator.compute(9, 2));
    }

    @Test
    public void two_hints_no_performance_bonus() {
        // Spec: "Complete a level after using two hints: do not award a performance bonus"
        // Win in 1 guess with 2 hints should still be 80, not 150
        int reward = RewardCalculator.compute(1, 2);
        assertEquals(80, reward);
        // And it should not include a performance bonus (i.e., 80, not 80+50)
        assertNotEquals(130, reward);
    }

    @Test
    public void one_hint_no_performance_bonus() {
        // Win in 3 guesses with 1 hint → 90, not 130
        int reward = RewardCalculator.compute(3, 1);
        assertEquals(90, reward);
    }

    // -------------------------------------------------------------------------
    // Floor at zero — cannot go negative
    // -------------------------------------------------------------------------

    @Test
    public void reward_never_negative_with_two_hints() {
        // 100 - 10*2 = 80 — normal case
        int reward = RewardCalculator.compute(10, 2);
        assertTrue("Reward must be non-negative", reward >= 0);
        assertEquals(80, reward);
    }

    @Test
    public void reward_floored_at_zero_with_excessive_hints() {
        // Safety: if hintsUsed > 10 (should never happen but guard anyway)
        int reward = RewardCalculator.compute(1, 20);
        assertEquals(0, reward);
    }

    // -------------------------------------------------------------------------
    // performanceBonus table exhaustion
    // -------------------------------------------------------------------------

    @Test
    public void performance_bonus_table_boundaries() {
        assertEquals(50, RewardCalculator.performanceBonus(1));
        assertEquals(40, RewardCalculator.performanceBonus(2));
        assertEquals(30, RewardCalculator.performanceBonus(3));
        assertEquals(20, RewardCalculator.performanceBonus(4));
        assertEquals(15, RewardCalculator.performanceBonus(5));
        assertEquals(10, RewardCalculator.performanceBonus(6));
        assertEquals(5,  RewardCalculator.performanceBonus(7));
        assertEquals(0,  RewardCalculator.performanceBonus(8));
        assertEquals(0,  RewardCalculator.performanceBonus(9));
        assertEquals(0,  RewardCalculator.performanceBonus(10));
    }
}
