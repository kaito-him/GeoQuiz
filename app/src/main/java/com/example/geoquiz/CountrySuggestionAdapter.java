package com.example.geoquiz;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Autocomplete adapter that visually disables already-guessed country entries.
 *
 * Works in both English and Arabic mode:
 *   - displayNames  : list of names shown in the dropdown (English OR Arabic)
 *   - canonicalNames: parallel list of English names used for "already guessed" lookups
 *   - guessedNames  : lower-cased English names that have been guessed
 */
public class CountrySuggestionAdapter extends ArrayAdapter<String> {

    private final LayoutInflater inflater;
    private final Set<String>    guessedNames;     // lower-cased English names
    private final List<String>   canonicalNames;   // English names, same order as display list
    private final List<String>   displayNames;     // Store original display list for lookups

    /**
     * @param context        activity context
     * @param displayNames   names to show in dropdown (may be Arabic)
     * @param canonicalNames parallel list of English names for lookup
     * @param guessedNames   already-guessed English names (lower-cased)
     */
    public CountrySuggestionAdapter(Context context,
                                    List<String> displayNames,
                                    List<String> canonicalNames,
                                    Set<String>  guessedNames) {
        super(context, R.layout.item_suggestion, displayNames);
        this.inflater       = LayoutInflater.from(context);
        this.displayNames   = displayNames;
        this.canonicalNames = canonicalNames;
        this.guessedNames   = guessedNames;
    }

    /**
     * Legacy constructor used when display names == English names
     * (English mode — no separate canonical list needed).
     */
    public CountrySuggestionAdapter(Context context,
                                    List<String> countryNames,
                                    Set<String>  guessedNames) {
        this(context, countryNames, countryNames, guessedNames);
    }

    /** False → the item cannot be highlighted or selected. */
    @Override
    public boolean isEnabled(int position) {
        if (position < 0 || position >= canonicalNames.size()) return true;
        String canonical = canonicalNames.get(position);
        return canonical == null || !guessedNames.contains(canonical.toLowerCase());
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        TextView tv = (TextView) inflater.inflate(R.layout.item_suggestion, parent, false);
        String currentDisplayText = getItem(position); // from the filtered list
        tv.setText(currentDisplayText);

        // Find this display text in the original full list to get the matching canonical name
        String canonical = null;
        for (int i = 0; i < displayNames.size(); i++) {
            if (displayNames.get(i).equals(currentDisplayText)) {
                canonical = canonicalNames.get(i);
                break;
            }
        }

        // Check if already guessed
        boolean alreadyGuessed = canonical != null 
                && guessedNames.contains(canonical.toLowerCase());

        if (alreadyGuessed) {
            tv.setTextColor(Color.parseColor("#9E9E9E"));
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setBackground(null);          // remove ripple — looks and feels unclickable
            tv.setClickable(false);
            tv.setFocusable(false);
        } else {
            tv.setTextColor(Color.parseColor("#3E2723"));
            tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
        return tv;
    }
}
