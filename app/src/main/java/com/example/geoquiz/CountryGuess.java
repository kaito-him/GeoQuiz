package com.example.geoquiz;

/**
 * Represents a single guess in the Daily Country game
 */
public class CountryGuess {
    private Country guessedCountry;
    private Country targetCountry;
    private ComparisonResult languageResult;
    private ComparisonResult currencyResult;
    private ComparisonResult continentResult;
    private ComparisonResult populationResult;
    private ComparisonResult areaResult;
    private FlagColorsResult flagColorsResult;

    public CountryGuess(Country guessedCountry, Country targetCountry) {
        this.guessedCountry = guessedCountry;
        this.targetCountry = targetCountry;
    }

    public Country getGuessedCountry() {
        return guessedCountry;
    }

    public Country getTargetCountry() {
        return targetCountry;
    }

    public ComparisonResult getLanguageResult() {
        return languageResult;
    }

    public void setLanguageResult(ComparisonResult languageResult) {
        this.languageResult = languageResult;
    }

    public ComparisonResult getCurrencyResult() {
        return currencyResult;
    }

    public void setCurrencyResult(ComparisonResult currencyResult) {
        this.currencyResult = currencyResult;
    }

    public ComparisonResult getContinentResult() {
        return continentResult;
    }

    public void setContinentResult(ComparisonResult continentResult) {
        this.continentResult = continentResult;
    }

    public ComparisonResult getPopulationResult() {
        return populationResult;
    }

    public void setPopulationResult(ComparisonResult populationResult) {
        this.populationResult = populationResult;
    }

    public ComparisonResult getAreaResult() {
        return areaResult;
    }

    public void setAreaResult(ComparisonResult areaResult) {
        this.areaResult = areaResult;
    }

    public FlagColorsResult getFlagColorsResult() {
        return flagColorsResult;
    }

    public void setFlagColorsResult(FlagColorsResult flagColorsResult) {
        this.flagColorsResult = flagColorsResult;
    }

    public boolean isCorrect() {
        return guessedCountry.getCountry().equalsIgnoreCase(targetCountry.getCountry());
    }

    /**
     * Result of comparing a single attribute
     */
    public static class ComparisonResult {
        public enum Status {
            CORRECT,    // Green
            CLOSE,      // Orange
            WRONG       // Grey
        }

        public enum Direction {
            NONE,       // No arrow
            UP,         // ↑ target is higher
            DOWN        // ↓ target is lower
        }

        private Status status;
        private Direction direction;
        private String displayValue;

        public ComparisonResult(Status status, Direction direction, String displayValue) {
            this.status = status;
            this.direction = direction;
            this.displayValue = displayValue;
        }

        public Status getStatus() {
            return status;
        }

        public Direction getDirection() {
            return direction;
        }

        public String getDisplayValue() {
            return displayValue;
        }
    }

    /**
     * Result for flag colors comparison.
     *
     * Status:
     *   FULL_MATCH    – every guessed color is in the target AND sets are identical → green tile
     *   PARTIAL_MATCH – at least one color in common but sets differ              → orange tile
     *   NO_MATCH      – no color in common                                        → grey tile
     */
    public static class FlagColorsResult {
        public enum Status { FULL_MATCH, PARTIAL_MATCH, NO_MATCH }

        private final String displayText;
        private final Status status;

        public FlagColorsResult(String displayText, Status status) {
            this.displayText = displayText;
            this.status      = status;
        }

        public String getDisplayText() { return displayText; }
        public Status getStatus()      { return status; }

        /** Convenience — kept for any callers that only care about full match. */
        public boolean isAllMatch() { return status == Status.FULL_MATCH; }
    }
}
