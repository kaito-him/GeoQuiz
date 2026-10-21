package com.example.geoquiz;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Manages language selection and applies locale changes across the app.
 *
 * Supported locales:
 *   "en" → English (default, LTR)
 *   "ar" → Arabic (RTL)
 *
 * Usage:
 *   1. Call {@link #applyLanguage(Context)} in every Activity's attachBaseContext()
 *      override before super.attachBaseContext().
 *   2. Call {@link #setLanguage(Context, String)} + activity.recreate() when
 *      the user picks a language from the menu.
 */
public class LanguageManager {

    private static final String PREFS_NAME  = "lang_prefs";
    private static final String KEY_LANG    = "selected_language";
    public  static final String LANG_EN     = "en";
    public  static final String LANG_AR     = "ar";

    // ------------------------------------------------------------------
    // Save / load
    // ------------------------------------------------------------------

    /** Persist the user's language choice. */
    public static void setLanguage(Context context, String langCode) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, langCode).apply();
    }

    /** Return the saved language code, defaulting to English. */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANG, LANG_EN);
    }

    public static boolean isArabic(Context context) {
        return LANG_AR.equals(getLanguage(context));
    }

    // ------------------------------------------------------------------
    // Apply locale to a Context (call from attachBaseContext)
    // ------------------------------------------------------------------

    /**
     * Wrap the given base context so that all string resources are resolved
     * in the user's chosen language. Call this from every Activity:
     *
     * <pre>
     * {@literal @}Override
     * protected void attachBaseContext(Context base) {
     *     super.attachBaseContext(LanguageManager.applyLanguage(base));
     * }
     * </pre>
     */
    public static Context applyLanguage(Context context) {
        String lang = getLanguage(context);
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.setLocale(locale);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return context.createConfigurationContext(config);
        } else {
            context.getResources().updateConfiguration(config,
                    context.getResources().getDisplayMetrics());
            return context;
        }
    }
}
