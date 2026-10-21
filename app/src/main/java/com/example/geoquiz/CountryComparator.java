package com.example.geoquiz;

import java.text.NumberFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles comparison logic between guessed and target countries
 */
public class CountryComparator {

    private static final long POPULATION_THRESHOLD = 1_000_000;
    private static final double AREA_THRESHOLD = 1_000_000.0;

    /**
     * Compare a guessed country against the target country
     */
    public static CountryGuess compare(Country guessedCountry, Country targetCountry) {
        CountryGuess guess = new CountryGuess(guessedCountry, targetCountry);

        guess.setLanguageResult(compareLanguages(guessedCountry, targetCountry));
        guess.setCurrencyResult(compareCurrency(guessedCountry, targetCountry));
        guess.setContinentResult(compareContinent(guessedCountry, targetCountry));
        guess.setPopulationResult(comparePopulation(guessedCountry, targetCountry));
        guess.setAreaResult(compareArea(guessedCountry, targetCountry));
        guess.setFlagColorsResult(compareFlagColors(guessedCountry, targetCountry));

        return guess;
    }

    /**
     * Compare languages.
     *
     * GREEN  – both arrays contain exactly the same languages (same set, case-insensitive,
     *          count doesn't matter — e.g. [Spanish] vs [Spanish] or [French, Spanish] vs [Spanish, French])
     * ORANGE – at least one language in common but the sets are not identical
     * GREY   – no language in common at all
     */
    private static CountryGuess.ComparisonResult compareLanguages(Country guessed, Country target) {
        List<String> guessedLangs = guessed.getMainLanguages();
        List<String> targetLangs  = target.getMainLanguages();

        if (guessedLangs == null || guessedLangs.isEmpty()
                || targetLangs == null || targetLangs.isEmpty()) {
            return new CountryGuess.ComparisonResult(
                    CountryGuess.ComparisonResult.Status.WRONG,
                    CountryGuess.ComparisonResult.Direction.NONE,
                    formatLanguages(guessedLangs)
            );
        }

        // Build lower-case sets for comparison
        Set<String> guessedSet = new HashSet<>();
        for (String l : guessedLangs) guessedSet.add(l.toLowerCase());

        Set<String> targetSet = new HashSet<>();
        for (String l : targetLangs) targetSet.add(l.toLowerCase());

        // Perfect match: both sets are identical
        boolean perfectMatch = guessedSet.equals(targetSet);

        // Partial match: at least one language in common
        boolean anyMatch = false;
        for (String g : guessedSet) {
            if (targetSet.contains(g)) { anyMatch = true; break; }
        }

        CountryGuess.ComparisonResult.Status status;
        if (perfectMatch) {
            status = CountryGuess.ComparisonResult.Status.CORRECT;
        } else if (anyMatch) {
            status = CountryGuess.ComparisonResult.Status.CLOSE;
        } else {
            status = CountryGuess.ComparisonResult.Status.WRONG;
        }

        return new CountryGuess.ComparisonResult(
                status,
                CountryGuess.ComparisonResult.Direction.NONE,
                formatLanguages(guessedLangs)
        );
    }

    /**
     * Compare currency codes
     * Green if exact match (case-insensitive)
     */
    private static CountryGuess.ComparisonResult compareCurrency(Country guessed, Country target) {
        String guessedCurrency = guessed.getCurrencyCode();
        String targetCurrency = target.getCurrencyCode();

        if (guessedCurrency == null || targetCurrency == null) {
            return new CountryGuess.ComparisonResult(
                    CountryGuess.ComparisonResult.Status.WRONG,
                    CountryGuess.ComparisonResult.Direction.NONE,
                    guessedCurrency != null ? guessedCurrency : "N/A"
            );
        }

        boolean matches = guessedCurrency.equalsIgnoreCase(targetCurrency);
        CountryGuess.ComparisonResult.Status status = matches ?
                CountryGuess.ComparisonResult.Status.CORRECT :
                CountryGuess.ComparisonResult.Status.WRONG;

        return new CountryGuess.ComparisonResult(
                status,
                CountryGuess.ComparisonResult.Direction.NONE,
                guessedCurrency
        );
    }

    /**
     * Compare continents
     * Green if exact match (case-insensitive)
     */
    private static CountryGuess.ComparisonResult compareContinent(Country guessed, Country target) {
        String guessedContinent = guessed.getContinent();
        String targetContinent = target.getContinent();

        if (guessedContinent == null || targetContinent == null) {
            return new CountryGuess.ComparisonResult(
                    CountryGuess.ComparisonResult.Status.WRONG,
                    CountryGuess.ComparisonResult.Direction.NONE,
                    guessedContinent != null ? guessedContinent : "N/A"
            );
        }

        boolean matches = guessedContinent.equalsIgnoreCase(targetContinent);
        CountryGuess.ComparisonResult.Status status = matches ?
                CountryGuess.ComparisonResult.Status.CORRECT :
                CountryGuess.ComparisonResult.Status.WRONG;

        return new CountryGuess.ComparisonResult(
                status,
                CountryGuess.ComparisonResult.Direction.NONE,
                guessedContinent
        );
    }

    /**
     * Compare population
     * Green if exact match
     * Orange if difference > 0 and <= 1,000,000
     * Grey otherwise
     * Shows arrow for direction
     */
    private static CountryGuess.ComparisonResult comparePopulation(Country guessed, Country target) {
        long guessedPop = guessed.getPopulation();
        long targetPop = target.getPopulation();

        long difference = Math.abs(targetPop - guessedPop);
        
        CountryGuess.ComparisonResult.Status status;
        CountryGuess.ComparisonResult.Direction direction;

        if (difference == 0) {
            status = CountryGuess.ComparisonResult.Status.CORRECT;
            direction = CountryGuess.ComparisonResult.Direction.NONE;
        } else if (difference <= POPULATION_THRESHOLD) {
            status = CountryGuess.ComparisonResult.Status.CLOSE;
            direction = targetPop > guessedPop ?
                    CountryGuess.ComparisonResult.Direction.UP :
                    CountryGuess.ComparisonResult.Direction.DOWN;
        } else {
            status = CountryGuess.ComparisonResult.Status.WRONG;
            direction = targetPop > guessedPop ?
                    CountryGuess.ComparisonResult.Direction.UP :
                    CountryGuess.ComparisonResult.Direction.DOWN;
        }

        String displayValue = formatNumber(guessedPop);

        return new CountryGuess.ComparisonResult(status, direction, displayValue);
    }

    /**
     * Compare area
     * Green if exact match
     * Orange if difference > 0 and <= 1,000,000 km²
     * Grey otherwise
     * Shows arrow for direction
     */
    private static CountryGuess.ComparisonResult compareArea(Country guessed, Country target) {
        double guessedArea = guessed.getArea();
        double targetArea = target.getArea();

        double difference = Math.abs(targetArea - guessedArea);
        
        CountryGuess.ComparisonResult.Status status;
        CountryGuess.ComparisonResult.Direction direction;

        if (difference == 0) {
            status = CountryGuess.ComparisonResult.Status.CORRECT;
            direction = CountryGuess.ComparisonResult.Direction.NONE;
        } else if (difference <= AREA_THRESHOLD) {
            status = CountryGuess.ComparisonResult.Status.CLOSE;
            direction = targetArea > guessedArea ?
                    CountryGuess.ComparisonResult.Direction.UP :
                    CountryGuess.ComparisonResult.Direction.DOWN;
        } else {
            status = CountryGuess.ComparisonResult.Status.WRONG;
            direction = targetArea > guessedArea ?
                    CountryGuess.ComparisonResult.Direction.UP :
                    CountryGuess.ComparisonResult.Direction.DOWN;
        }

        String displayValue = formatNumber((long) guessedArea) + " km²";

        return new CountryGuess.ComparisonResult(status, direction, displayValue);
    }

    /**
     * Compare flag colors.
     *
     * GREEN  – guessed and target color sets are exactly the same (case-insensitive)
     * ORANGE – at least one color in common but sets differ
     * GREY   – no color in common
     *
     * Per-color text on grey/orange tile: matching colors in green, others in white.
     * On a green tile: plain white text (no inline coloring needed).
     */
    private static CountryGuess.FlagColorsResult compareFlagColors(Country guessed, Country target) {
        List<String> guessedColors = guessed.getFlagColors();
        List<String> targetColors  = target.getFlagColors();

        if (guessedColors == null || guessedColors.isEmpty()) {
            return new CountryGuess.FlagColorsResult(
                    "No colors", CountryGuess.FlagColorsResult.Status.NO_MATCH);
        }

        // Lower-case sets for comparison
        Set<String> guessedSet = new HashSet<>();
        for (String c : guessedColors) guessedSet.add(c.toLowerCase());

        Set<String> targetSet = new HashSet<>();
        if (targetColors != null) {
            for (String c : targetColors) targetSet.add(c.toLowerCase());
        }

        // Determine status
        boolean perfectMatch = guessedSet.equals(targetSet);
        boolean anyMatch = false;
        if (!perfectMatch) {
            for (String g : guessedSet) {
                if (targetSet.contains(g)) { anyMatch = true; break; }
            }
        }

        CountryGuess.FlagColorsResult.Status status;
        if (perfectMatch)    status = CountryGuess.FlagColorsResult.Status.FULL_MATCH;
        else if (anyMatch)   status = CountryGuess.FlagColorsResult.Status.PARTIAL_MATCH;
        else                 status = CountryGuess.FlagColorsResult.Status.NO_MATCH;

        // Build display text — plain white text on all tile states; only the tile background colour differs
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < guessedColors.size(); i++) {
            sb.append(guessedColors.get(i));
            if (i < guessedColors.size() - 1) sb.append(", ");
        }
        String displayText = sb.toString();

        return new CountryGuess.FlagColorsResult(displayText, status);
    }

    /**
     * Format a list of languages for display
     */
    private static String formatLanguages(List<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return "N/A";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < languages.size(); i++) {
            sb.append(languages.get(i));
            if (i < languages.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * Format large numbers with commas
     */
    private static String formatNumber(long number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }
}
