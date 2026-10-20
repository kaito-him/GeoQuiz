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
 * Autocomplete adapter that visually disables and blocks selection of
 * country names that have already been guessed this level.
 */
public class CountrySuggestionAdapter extends ArrayAdapter<String> {

    private final LayoutInflater inflater;
    private final Set<String> guessedNames; // lower-cased names for fast lookup

    public CountrySuggestionAdapter(Context context, List<String> countryNames,
                                    Set<String> guessedNames) {
        super(context, R.layout.item_suggestion, countryNames);
        this.inflater = LayoutInflater.from(context);
        this.guessedNames = guessedNames;
    }

    /**
     * Returning false makes the dropdown framework skip the item entirely —
     * it cannot be highlighted or selected by touch or keyboard navigation.
     */
    @Override
    public boolean isEnabled(int position) {
        String name = getItem(position);
        return name == null || !guessedNames.contains(name.toLowerCase());
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        TextView tv = (TextView) inflater.inflate(R.layout.item_suggestion, parent, false);

        String name = getItem(position);
        tv.setText(name);

        if (!isEnabled(position)) {
            // Already guessed: grey + strikethrough
            tv.setTextColor(Color.parseColor("#9E9E9E"));
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            // Normal: dark brown on light background
            tv.setTextColor(Color.parseColor("#3E2723"));
            tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }

        return tv;
    }
}
