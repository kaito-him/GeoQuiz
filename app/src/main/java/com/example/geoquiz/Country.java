package com.example.geoquiz;

import java.util.List;

public class Country {
    private String isoCode;
    private String country;
    private String difficultyCategory;
    private String capitalName;
    private List<String> mainLanguages;
    private String currencyCode;
    private String currencyName;
    private String continent;
    private long population;
    private double area;
    private String map;
    private String funFact;
    private List<String> flagColors;
    private String flag;

    // Default constructor required for JSON parsing
    public Country() {
    }

    // Getters and setters
    public String getIsoCode() {
        return isoCode;
    }

    public void setIsoCode(String isoCode) {
        this.isoCode = isoCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDifficultyCategory() {
        return difficultyCategory;
    }

    public void setDifficultyCategory(String difficultyCategory) {
        this.difficultyCategory = difficultyCategory;
    }

    public String getCapitalName() {
        return capitalName;
    }

    public void setCapitalName(String capitalName) {
        this.capitalName = capitalName;
    }

    public List<String> getMainLanguages() {
        return mainLanguages;
    }

    public void setMainLanguages(List<String> mainLanguages) {
        this.mainLanguages = mainLanguages;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public void setCurrencyName(String currencyName) {
        this.currencyName = currencyName;
    }

    public String getContinent() {
        return continent;
    }

    public void setContinent(String continent) {
        this.continent = continent;
    }

    public long getPopulation() {
        return population;
    }

    public void setPopulation(long population) {
        this.population = population;
    }

    public double getArea() {
        return area;
    }

    public void setArea(double area) {
        this.area = area;
    }

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
    }

    public String getFunFact() {
        return funFact;
    }

    public void setFunFact(String funFact) {
        this.funFact = funFact;
    }

    public List<String> getFlagColors() {
        return flagColors;
    }

    public void setFlagColors(List<String> flagColors) {
        this.flagColors = flagColors;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    @Override
    public String toString() {
        return country;
    }
}
