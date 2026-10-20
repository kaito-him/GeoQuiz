package com.example.geoquiz;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for HintManager covering slot unlock, eligibility, purchase recording,
 * and serialization/deserialization.
 *
 * All tests are pure JVM — no Android context required.
 * Country objects are created directly using setters.
 */
public class HintManagerTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build a minimal Country for testing. */
    private Country makeCountry(String iso, String name, String continent,
                                String currencyCode, String currencyName,
                                String capital, String funFact,
                                List<String> languages, List<String> flagColors,
                                long pop, double area) {
        Country c = new Country();
        c.setIsoCode(iso);
        c.setCountry(name);
        c.setContinent(continent);
        c.setCurrencyCode(currencyCode);
        c.setCurrencyName(currencyName);
        c.setCapitalName(capital);
        c.setFunFact(funFact);
        c.setMainLanguages(languages);
        c.setFlagColors(flagColors);
        c.setPopulation(pop);
        c.setArea(area);
        c.setFlag(iso.toLowerCase() + ".png");
        return c;
    }

    /** Build a CountryRepository pre-populated with the provided list. */
    private CountryRepository makeRepo(List<Country> countries) {
        // Reset singleton so each test starts fresh
        CountryRepository repo = CountryRepository.getInstance();
        // Populate via the internal list using reflection-free injection:
        // We cannot call loadCountries() without a Context, so we use the
        // package-private injection path exposed by the singleton.
        // Since getAllCountries() returns a copy, we instead stub at a higher
        // level: return an anonymous subclass that delegates to our list.
        // Because CountryRepository is a concrete class we cannot subclass it
        // easily; instead we load the real countries if already loaded, or we
        // work directly with HintManager's stub for candidates.
        // For these tests the repository is only used for elimination candidate
        // listing, which we exercise separately.
        return repo;
    }

    // Shared test fixtures
    private Country target;
    private Country guessedSameContinent;
    private Country guessedDifferentContinent;

    @Before
    public void setUp() {
        target = makeCountry(
                "DE", "Germany", "Europe",
                "EUR", "Euro",
                "Berlin", "Germany has more than 1,500 types of beer.",
                Arrays.asList("German"),
                Arrays.asList("Black", "Red", "Gold"),
                84000000L, 357114.0
        );

        guessedSameContinent = makeCountry(
                "FR", "France", "Europe",
                "EUR", "Euro",
                "Paris", "France fact.",
                Arrays.asList("French"),
                Arrays.asList("Blue", "White", "Red"),
                68000000L, 551695.0
        );

        guessedDifferentContinent = makeCountry(
                "JP", "Japan", "Asia",
                "JPY", "Japanese Yen",
                "Tokyo", "Japan fact.",
                Arrays.asList("Japanese"),
                Arrays.asList("White", "Red"),
                126000000L, 377975.0
        );
    }

    // -------------------------------------------------------------------------
    // Slot unlock timing
    // -------------------------------------------------------------------------

    @Test
    public void slot1_locked_before_4_guesses() {
        HintManager hm = new HintManager(target, null);
        assertFalse(hm.isSlot1Unlocked(0));
        assertFalse(hm.isSlot1Unlocked(3));
    }

    @Test
    public void slot1_unlocks_at_4_guesses() {
        HintManager hm = new HintManager(target, null);
        assertTrue(hm.isSlot1Unlocked(4));
        assertTrue(hm.isSlot1Unlocked(5));
    }

    @Test
    public void slot2_locked_before_8_guesses() {
        HintManager hm = new HintManager(target, null);
        assertFalse(hm.isSlot2Unlocked(7));
    }

    @Test
    public void slot2_unlocks_at_8_guesses() {
        HintManager hm = new HintManager(target, null);
        assertTrue(hm.isSlot2Unlocked(8));
    }

    @Test
    public void purchase_before_slot1_unlock_is_rejected() {
        HintManager hm = new HintManager(target, null);
        // Only 3 guesses submitted — slot 1 not yet unlocked
        HintManager.EligibilityStatus status =
                hm.checkEligibility(HintType.REVEAL_CAPITAL, 3);
        assertEquals(HintManager.EligibilityStatus.SLOT_NOT_UNLOCKED, status);
    }

    // -------------------------------------------------------------------------
    // Continent hint — disabled when already known from a green tile
    // -------------------------------------------------------------------------

    @Test
    public void continent_hint_available_when_unknown() {
        HintManager hm = new HintManager(target, null);
        HintManager.EligibilityStatus status =
                hm.checkEligibility(HintType.REVEAL_CONTINENT, 4);
        assertEquals(HintManager.EligibilityStatus.AVAILABLE, status);
    }

    @Test
    public void continent_hint_disabled_when_already_green() {
        HintManager hm = new HintManager(target, null);
        // Feed a guess from a country with the same continent (green tile)
        CountryGuess guess = CountryComparator.compare(guessedSameContinent, target);
        hm.onGuessMade(guess);

        HintManager.EligibilityStatus status =
                hm.checkEligibility(HintType.REVEAL_CONTINENT, 4);
        assertEquals(HintManager.EligibilityStatus.ALREADY_KNOWN, status);
    }

    @Test
    public void continent_hint_available_when_different_continent_guessed() {
        HintManager hm = new HintManager(target, null);
        // Japan is Asia — continent tile will be grey
        CountryGuess guess = CountryComparator.compare(guessedDifferentContinent, target);
        hm.onGuessMade(guess);

        HintManager.EligibilityStatus status =
                hm.checkEligibility(HintType.REVEAL_CONTINENT, 4);
        assertEquals(HintManager.EligibilityStatus.AVAILABLE, status);
    }

    // -------------------------------------------------------------------------
    // Currency hint — disabled once currency code matches
    // -------------------------------------------------------------------------

    @Test
    public void currency_hint_disabled_when_already_known_from_guess() {
        HintManager hm = new HintManager(target, null);
        // France also uses EUR — currency tile is green
        CountryGuess guess = CountryComparator.compare(guessedSameContinent, target);
        hm.onGuessMade(guess);

        HintManager.EligibilityStatus status =
                hm.checkEligibility(HintType.REVEAL_CURRENCY, 4);
        assertEquals(HintManager.EligibilityStatus.ALREADY_KNOWN, status);
    }

    // -------------------------------------------------------------------------
    // Slot exhaustion — no slots remaining
    // -------------------------------------------------------------------------

    @Test
    public void no_slots_remaining_after_both_used() {
        HintManager hm = new HintManager(target, null);
        // Use slot 1
        hm.recordHintPurchase(HintType.REVEAL_CAPITAL, "Berlin", 1);
        // Use slot 2
        hm.recordHintPurchase(HintType.REVEAL_FUN_FACT, "Beer fact.", 2);

        assertTrue(hm.isSlot1Used());
        assertTrue(hm.isSlot2Used());

        HintManager.EligibilityStatus status =
                hm.checkEligibility(HintType.REVEAL_CONTINENT, 8);
        assertEquals(HintManager.EligibilityStatus.NO_SLOTS_REMAINING, status);
    }

    // -------------------------------------------------------------------------
    // Purchase recording — coin deduction is the caller's responsibility
    // -------------------------------------------------------------------------

    @Test
    public void recordHintPurchase_sets_slot_used() {
        HintManager hm = new HintManager(target, null);
        assertFalse(hm.isSlot1Used());
        hm.recordHintPurchase(HintType.REVEAL_CAPITAL, "Berlin", 1);
        assertTrue(hm.isSlot1Used());
    }

    @Test
    public void getPurchasedHint_returns_correct_data() {
        HintManager hm = new HintManager(target, null);
        hm.recordHintPurchase(HintType.REVEAL_CAPITAL, "Berlin", 1);
        HintManager.PurchasedHint ph = hm.getPurchasedHint(1);
        assertNotNull(ph);
        assertEquals(HintType.REVEAL_CAPITAL, ph.type);
        assertEquals("Berlin", ph.revealedText);
        assertEquals(1, ph.slotIndex);
    }

    @Test
    public void cancel_does_not_record_purchase() {
        // Cancellation is handled at the UI layer — if recordHintPurchase is never
        // called, the slot remains unused.
        HintManager hm = new HintManager(target, null);
        // Simulate: user clicks Cancel — recordHintPurchase NOT called
        assertFalse(hm.isSlot1Used());
        assertNull(hm.getPurchasedHint(1));
        // Balance check: no coins spent because recordHintPurchase was not called
    }

    // -------------------------------------------------------------------------
    // Already-known hint — capital revealed by earlier hint
    // -------------------------------------------------------------------------

    @Test
    public void capital_hint_disabled_after_being_revealed() {
        HintManager hm = new HintManager(target, null);
        hm.recordHintPurchase(HintType.REVEAL_CAPITAL, "Berlin", 1);

        HintManager.EligibilityStatus status =
                hm.checkEligibility(HintType.REVEAL_CAPITAL, 8);
        assertEquals(HintManager.EligibilityStatus.ALREADY_KNOWN, status);
    }

    @Test
    public void fun_fact_hint_disabled_after_being_revealed() {
        HintManager hm = new HintManager(target, null);
        hm.recordHintPurchase(HintType.REVEAL_FUN_FACT, "Beer fact.", 1);

        HintManager.EligibilityStatus status =
                hm.checkEligibility(HintType.REVEAL_FUN_FACT, 8);
        assertEquals(HintManager.EligibilityStatus.ALREADY_KNOWN, status);
    }

    @Test
    public void num_clue_hint_disabled_after_being_revealed() {
        HintManager hm = new HintManager(target, null);
        hm.recordHintPurchase(HintType.REVEAL_NUM_CLUE, "Pop: 50M–100M\nArea: 100K–500K", 1);

        HintManager.EligibilityStatus status =
                hm.checkEligibility(HintType.REVEAL_NUM_CLUE, 8);
        assertEquals(HintManager.EligibilityStatus.ALREADY_KNOWN, status);
    }

    // -------------------------------------------------------------------------
    // Serialization / deserialization — app restart preservation
    // -------------------------------------------------------------------------

    @Test
    public void toJson_and_fromJson_round_trips_slot1_purchase() {
        HintManager hm = new HintManager(target, null);
        hm.recordHintPurchase(HintType.REVEAL_CAPITAL, "Berlin", 1);

        String json = hm.toJson();
        assertNotNull(json);
        assertFalse(json.isEmpty());

        // Restore into a fresh HintManager
        HintManager restored = new HintManager(target, null);
        restored.fromJson(json);

        assertTrue(restored.isSlot1Used());
        assertFalse(restored.isSlot2Used());

        HintManager.PurchasedHint ph = restored.getPurchasedHint(1);
        assertNotNull(ph);
        assertEquals(HintType.REVEAL_CAPITAL, ph.type);
        assertEquals("Berlin", ph.revealedText);
    }

    @Test
    public void toJson_and_fromJson_round_trips_both_slots() {
        HintManager hm = new HintManager(target, null);
        hm.recordHintPurchase(HintType.REVEAL_CAPITAL, "Berlin", 1);
        hm.recordHintPurchase(HintType.REVEAL_FUN_FACT, "Beer fact.", 2);

        String json = hm.toJson();
        HintManager restored = new HintManager(target, null);
        restored.fromJson(json);

        assertTrue(restored.isSlot1Used());
        assertTrue(restored.isSlot2Used());
        assertEquals("Berlin",     restored.getPurchasedHint(1).revealedText);
        assertEquals("Beer fact.", restored.getPurchasedHint(2).revealedText);
    }

    @Test
    public void fromJson_restores_known_clues_so_capital_is_disabled_after_restart() {
        // Simulate: player bought capital hint, killed app, relaunched
        HintManager hm = new HintManager(target, null);
        hm.recordHintPurchase(HintType.REVEAL_CAPITAL, "Berlin", 1);
        String json = hm.toJson();

        // After restart
        HintManager restored = new HintManager(target, null);
        restored.fromJson(json);

        // Capital hint should now be ALREADY_KNOWN (not re-purchasable)
        HintManager.EligibilityStatus status =
                restored.checkEligibility(HintType.REVEAL_CAPITAL, 8);
        assertEquals(HintManager.EligibilityStatus.ALREADY_KNOWN, status);
    }

    @Test
    public void fromJson_empty_string_leaves_slots_clean() {
        HintManager hm = new HintManager(target, null);
        hm.fromJson("{}");
        assertFalse(hm.isSlot1Used());
        assertFalse(hm.isSlot2Used());
    }

    // -------------------------------------------------------------------------
    // nextAvailableSlot
    // -------------------------------------------------------------------------

    @Test
    public void nextAvailableSlot_returns_1_when_slot1_unlocked_and_unused() {
        HintManager hm = new HintManager(target, null);
        assertEquals(1, hm.nextAvailableSlot(4));
    }

    @Test
    public void nextAvailableSlot_returns_2_when_slot1_used_and_slot2_unlocked() {
        HintManager hm = new HintManager(target, null);
        hm.recordHintPurchase(HintType.REVEAL_CAPITAL, "Berlin", 1);
        assertEquals(2, hm.nextAvailableSlot(8));
    }

    @Test
    public void nextAvailableSlot_returns_minus1_when_both_used() {
        HintManager hm = new HintManager(target, null);
        hm.recordHintPurchase(HintType.REVEAL_CAPITAL,  "Berlin",     1);
        hm.recordHintPurchase(HintType.REVEAL_FUN_FACT, "Beer fact.", 2);
        assertEquals(-1, hm.nextAvailableSlot(10));
    }

    @Test
    public void nextAvailableSlot_returns_minus1_when_slot1_not_yet_unlocked() {
        HintManager hm = new HintManager(target, null);
        assertEquals(-1, hm.nextAvailableSlot(3));
    }

    // -------------------------------------------------------------------------
    // generateRevealText — spot checks for safety
    // -------------------------------------------------------------------------

    @Test
    public void generateRevealText_continent_returns_target_continent() {
        HintManager hm = new HintManager(target, null);
        String text = hm.generateRevealText(HintType.REVEAL_CONTINENT);
        assertEquals("Europe", text);
    }

    @Test
    public void generateRevealText_capital_returns_target_capital() {
        HintManager hm = new HintManager(target, null);
        String text = hm.generateRevealText(HintType.REVEAL_CAPITAL);
        assertEquals("Berlin", text);
    }

    @Test
    public void generateRevealText_currency_returns_currency_name() {
        HintManager hm = new HintManager(target, null);
        String text = hm.generateRevealText(HintType.REVEAL_CURRENCY);
        assertEquals("Euro", text);
    }

    @Test
    public void generateRevealText_fun_fact_returns_fact() {
        HintManager hm = new HintManager(target, null);
        String text = hm.generateRevealText(HintType.REVEAL_FUN_FACT);
        assertEquals("Germany has more than 1,500 types of beer.", text);
    }

    @Test
    public void generateRevealText_flag_color_returns_an_unknown_color() {
        HintManager hm = new HintManager(target, null);
        String text = hm.generateRevealText(HintType.REVEAL_FLAG_COLOR);
        assertNotNull(text);
        // Must be one of the target's flag colors
        List<String> targetColors = target.getFlagColors();
        boolean found = false;
        for (String c : targetColors) {
            if (c.equalsIgnoreCase(text)) { found = true; break; }
        }
        assertTrue("Revealed color must be one of target's flag colors", found);
    }

    @Test
    public void generateRevealText_language_returns_an_unknown_language() {
        HintManager hm = new HintManager(target, null);
        String text = hm.generateRevealText(HintType.REVEAL_LANGUAGE);
        assertNotNull(text);
        List<String> targetLangs = target.getMainLanguages();
        boolean found = false;
        for (String l : targetLangs) {
            if (l.equalsIgnoreCase(text)) { found = true; break; }
        }
        assertTrue("Revealed language must be one of target's languages", found);
    }

    @Test
    public void generateRevealText_num_clue_contains_population_and_area_lines() {
        HintManager hm = new HintManager(target, null);
        String text = hm.generateRevealText(HintType.REVEAL_NUM_CLUE);
        assertNotNull(text);
        assertTrue("Clue should mention Population", text.contains("Population"));
        assertTrue("Clue should mention Area", text.contains("Area"));
    }
}
