package com.example.geoquiz;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the player's coin balance across the whole app.
 */
public class CoinManager {

    private static final String PREFS_NAME = "coin_prefs";
    private static final String KEY_COINS  = "coins";

    private final SharedPreferences prefs;

    public CoinManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getCoins() {
        return prefs.getInt(KEY_COINS, 0);
    }

    public void addCoins(int amount) {
        prefs.edit().putInt(KEY_COINS, getCoins() + amount).apply();
    }

    public void setCoins(int amount) {
        prefs.edit().putInt(KEY_COINS, Math.max(0, amount)).apply();
    }

    /**
     * Deduct coins if the balance is sufficient.
     *
     * @param amount positive number to deduct
     * @return true if the deduction succeeded; false if the balance was insufficient
     */
    public boolean deductCoins(int amount) {
        int current = getCoins();
        if (current < amount) return false;
        prefs.edit().putInt(KEY_COINS, current - amount).apply();
        return true;
    }
}
