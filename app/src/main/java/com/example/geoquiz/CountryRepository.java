package com.example.geoquiz;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CountryRepository {
    private static final String TAG = "CountryRepository";
    private static CountryRepository instance;
    private List<Country> countries;

    private CountryRepository() {
        countries = new ArrayList<>();
    }

    public static synchronized CountryRepository getInstance() {
        if (instance == null) {
            instance = new CountryRepository();
        }
        return instance;
    }

    /**
     * Load countries from JSON file in assets folder
     * @param context Application context
     * @return true if loaded successfully, false otherwise
     */
    public boolean loadCountries(Context context) {
        if (!countries.isEmpty()) {
            return true; // Already loaded
        }

        try {
            String json = loadJSONFromAsset(context, "countries.json");
            if (json == null) {
                Log.e(TAG, "Failed to load countries.json");
                return false;
            }

            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    Country country = parseCountry(obj);
                    if (country != null) {
                        countries.add(country);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing country at index " + i, e);
                }
            }

            Log.d(TAG, "Loaded " + countries.size() + " countries");

            // Sort by difficulty tier (Easy → Intermediate → Hard → Expert),
            // then shuffle randomly within each tier so the order is never
            // alphabetical or identical to the source file.
            applyDifficultyOrder();

            return true;

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON array", e);
            return false;
        }
    }

    /**
     * Reorders the country list so all Easy levels come first, then
     * Intermediate, then Hard, then Expert.  Within each tier the countries
     * are shuffled randomly — so the grid never looks alphabetical or mirrors
     * the original JSON file order.
     *
     * This is called once, immediately after the initial JSON parse.  Because
     * CountryRepository is a singleton the order is determined once per app
     * process launch and stays stable for the whole session.
     */
    private void applyDifficultyOrder() {
        // Bucket by tier
        List<Country> easy         = new ArrayList<>();
        List<Country> intermediate = new ArrayList<>();
        List<Country> hard         = new ArrayList<>();
        List<Country> expert       = new ArrayList<>();
        List<Country> other        = new ArrayList<>(); // safety net for unknowns

        for (Country c : countries) {
            String d = c.getDifficultyCategory();
            if (d == null) { other.add(c); continue; }
            switch (d.trim().toLowerCase()) {
                case "easy":         easy.add(c);         break;
                case "intermediate": intermediate.add(c); break;
                case "hard":         hard.add(c);         break;
                case "expert":       expert.add(c);       break;
                default:             other.add(c);        break;
            }
        }

        // Shuffle within each tier independently
        Collections.shuffle(easy);
        Collections.shuffle(intermediate);
        Collections.shuffle(hard);
        Collections.shuffle(expert);
        Collections.shuffle(other);

        // Rebuild the master list in tier order
        countries.clear();
        countries.addAll(easy);
        countries.addAll(intermediate);
        countries.addAll(hard);
        countries.addAll(expert);
        countries.addAll(other);

        Log.d(TAG, "Difficulty order applied: Easy=" + easy.size()
                + " Intermediate=" + intermediate.size()
                + " Hard=" + hard.size()
                + " Expert=" + expert.size());
    }

    /**
     * Parse a single country from JSON object
     */
    private Country parseCountry(JSONObject obj) {
        try {
            Country country = new Country();
            
            country.setIsoCode(obj.optString("ISO Code", ""));
            country.setCountry(obj.optString("Country", ""));
            country.setCountryAr(obj.optString("Country_AR", ""));
            country.setDifficultyCategory(obj.optString("Difficulty Category", ""));
            country.setCapitalName(obj.optString("Capital Name", ""));
            country.setCurrencyCode(obj.optString("Currency Code", ""));
            country.setCurrencyName(obj.optString("Currency Name", ""));
            country.setContinent(obj.optString("Continent", ""));
            country.setPopulation(obj.optLong("Population (2026)", 0));
            country.setArea(obj.optDouble("Area (in km2)", 0.0));
            country.setMap(obj.optString("Map", ""));
            country.setFunFact(obj.optString("Fun Fact", ""));
            country.setFlag(obj.optString("Flag", ""));

            // Parse arrays
            country.setMainLanguages(parseStringArray(obj.optJSONArray("Main Languages")));
            country.setFlagColors(parseStringArray(obj.optJSONArray("Flag Colors")));

            return country;

        } catch (Exception e) {
            Log.e(TAG, "Error creating country object", e);
            return null;
        }
    }

    /**
     * Parse JSON array to List of strings
     */
    private List<String> parseStringArray(JSONArray jsonArray) {
        List<String> list = new ArrayList<>();
        if (jsonArray != null) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    list.add(jsonArray.getString(i));
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing string array element", e);
                }
            }
        }
        return list;
    }

    /**
     * Load JSON file from assets folder
     */
    private String loadJSONFromAsset(Context context, String filename) {
        StringBuilder json = new StringBuilder();
        try {
            InputStream is = context.getAssets().open(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            reader.close();
            is.close();
            return json.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading JSON file: " + filename, e);
            return null;
        }
    }

    /**
     * Get all countries in order
     */
    public List<Country> getAllCountries() {
        return new ArrayList<>(countries);
    }

    /**
     * Get country by level (1-based index)
     */
    public Country getCountryByLevel(int level) {
        if (level < 1 || level > countries.size()) {
            return null;
        }
        return countries.get(level - 1);
    }

    /**
     * Get total number of countries
     */
    public int getTotalCountries() {
        return countries.size();
    }

    /**
     * Find country by Arabic name (case-insensitive, trims whitespace).
     */
    public Country findCountryByNameAr(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String search = name.trim();
        for (Country country : countries) {
            String ar = country.getCountryAr();
            if (ar != null && ar.trim().equals(search)) return country;
        }
        return null;
    }

    /**
     * Find country by name (case-insensitive)
     */
    public Country findCountryByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        
        String searchName = name.trim().toLowerCase();
        for (Country country : countries) {
            if (country.getCountry().toLowerCase().equals(searchName)) {
                return country;
            }
        }
        return null;
    }

    /**
     * Get country suggestions for autocomplete
     */
    public List<String> getCountrySuggestions(String query) {
        List<String> suggestions = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return suggestions;
        }

        String searchQuery = query.trim().toLowerCase();
        for (Country country : countries) {
            if (country.getCountry().toLowerCase().startsWith(searchQuery)) {
                suggestions.add(country.getCountry());
            }
        }
        return suggestions;
    }
}
