package com.example.geoquiz;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter for country guess cards.
 * New guesses are inserted at position 0 so they appear at the top.
 *
 * Tile animation
 * --------------
 * When a brand-new guess is bound (its ISO key is NOT in animatedKeys), the six
 * comparison tiles are revealed one by one with a flip-in + fade animation,
 * staggered 120 ms apart.  Restored / scrolled-back items are bound instantly.
 */
public class GuessAdapter extends RecyclerView.Adapter<GuessAdapter.GuessViewHolder> {

    // Tiles animate exactly once per unique guess (keyed on guessedIso + targetIso)
    private final Set<String> animatedKeys = new HashSet<>();

    private static final long TILE_STAGGER_MS  = 220;  // delay between consecutive tiles
    private static final long TILE_DURATION_MS = 520;  // flip duration per tile

    private final Context context;
    private final List<CountryGuess> guesses;

    public GuessAdapter(Context context) {
        this.context = context;
        this.guesses = new ArrayList<>();
    }

    /** Insert a new guess at the top of the list. */
    public void addGuess(CountryGuess guess) {
        guesses.add(0, guess);
        notifyItemInserted(0);
    }

    /** Remove all guesses (used when advancing to the next level). */
    public void clearGuesses() {
        int size = guesses.size();
        guesses.clear();
        animatedKeys.clear();
        notifyItemRangeRemoved(0, size);
    }

    @NonNull
    @Override
    public GuessViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_country_guess, parent, false);
        return new GuessViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GuessViewHolder holder, int position) {
        CountryGuess guess = guesses.get(position);
        String key = animationKey(guess);
        boolean isNew = !animatedKeys.contains(key);
        if (isNew) animatedKeys.add(key);
        holder.bind(guess, isNew);
    }

    @Override
    public int getItemCount() {
        return guesses.size();
    }

    private static String animationKey(CountryGuess g) {
        return g.getGuessedCountry().getIsoCode() + "_" + g.getTargetCountry().getIsoCode();
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    class GuessViewHolder extends RecyclerView.ViewHolder {

        final ImageView ivFlag;
        final TextView  tvCountryName;

        final LinearLayout tileLanguage;
        final LinearLayout tileCurrency;
        final LinearLayout tilePopulation;
        final LinearLayout tileArea;
        final LinearLayout tileContinent;
        final LinearLayout tileFlagColors;

        final TextView tvLanguage;
        final TextView tvCurrency;
        final TextView tvPopulation;
        final TextView tvArea;
        final TextView tvContinent;
        final TextView tvFlagColors;

        GuessViewHolder(@NonNull View itemView) {
            super(itemView);

            ivFlag        = itemView.findViewById(R.id.ivFlag);
            tvCountryName = itemView.findViewById(R.id.tvCountryName);

            tileLanguage   = itemView.findViewById(R.id.tileLanguage);
            tileCurrency   = itemView.findViewById(R.id.tileCurrency);
            tilePopulation = itemView.findViewById(R.id.tilePopulation);
            tileArea       = itemView.findViewById(R.id.tileArea);
            tileContinent  = itemView.findViewById(R.id.tileContinent);
            tileFlagColors = itemView.findViewById(R.id.tileFlagColors);

            tvLanguage   = itemView.findViewById(R.id.tvLanguage);
            tvCurrency   = itemView.findViewById(R.id.tvCurrency);
            tvPopulation = itemView.findViewById(R.id.tvPopulation);
            tvArea       = itemView.findViewById(R.id.tvArea);
            tvContinent  = itemView.findViewById(R.id.tvContinent);
            tvFlagColors = itemView.findViewById(R.id.tvFlagColors);
        }

        // ---------------------------------------------------------------------
        // Bind
        // ---------------------------------------------------------------------

        void bind(CountryGuess guess, boolean animate) {
            Country country = guess.getGuessedCountry();
            boolean correct = guess.isCorrect();

            tvCountryName.setText(country.getCountry());
            loadFlagFromAssets(country.getFlag());

            // Order matches the visual row order (left-to-right, top-to-bottom)
            LinearLayout[] tiles = {
                tileLanguage, tileCurrency,
                tilePopulation, tileArea,
                tileContinent, tileFlagColors
            };

            // Apply colours / text first (always)
            applyTile(tileLanguage,   tvLanguage,   guess.getLanguageResult(),   correct);
            applyTile(tileCurrency,   tvCurrency,   guess.getCurrencyResult(),   correct);
            applyTile(tilePopulation, tvPopulation, guess.getPopulationResult(), correct);
            applyTile(tileArea,       tvArea,       guess.getAreaResult(),       correct);
            applyTile(tileContinent,  tvContinent,  guess.getContinentResult(),  correct);
            applyFlagColorsTile(guess.getFlagColorsResult());

            if (animate) {
                animateTiles(tiles);
            } else {
                // Make sure everything is fully visible (might have been hidden by a previous bind)
                for (LinearLayout tile : tiles) {
                    tile.setAlpha(1f);
                    tile.setRotationX(0f);
                    tile.setVisibility(View.VISIBLE);
                }
            }
        }

        // ---------------------------------------------------------------------
        // Tile animation — flip-in + fade, staggered
        // ---------------------------------------------------------------------

        private void animateTiles(LinearLayout[] tiles) {
            // Camera distance avoids clipping during the 3-D rotation
            float cameraDist = 8000 * context.getResources().getDisplayMetrics().density;

            for (int i = 0; i < tiles.length; i++) {
                final LinearLayout tile = tiles[i];
                tile.setCameraDistance(cameraDist);

                // Start hidden and rotated 90° around X axis (face-down)
                tile.setAlpha(0f);
                tile.setRotationX(90f);

                long startDelay = i * TILE_STAGGER_MS;

                ObjectAnimator flip = ObjectAnimator.ofFloat(tile, "rotationX", 90f, 0f);
                flip.setDuration(TILE_DURATION_MS);
                flip.setStartDelay(startDelay);
                flip.setInterpolator(new DecelerateInterpolator());

                ObjectAnimator fade = ObjectAnimator.ofFloat(tile, "alpha", 0f, 1f);
                fade.setDuration(TILE_DURATION_MS);
                fade.setStartDelay(startDelay);
                fade.setInterpolator(new DecelerateInterpolator());

                AnimatorSet set = new AnimatorSet();
                set.playTogether(flip, fade);
                set.start();
            }
        }

        // ---------------------------------------------------------------------
        // Flag loading
        // ---------------------------------------------------------------------

        private void loadFlagFromAssets(String flagFileName) {
            if (flagFileName == null || flagFileName.isEmpty()) {
                ivFlag.setImageDrawable(null);
                return;
            }
            try {
                InputStream is = context.getAssets().open("flags/" + flagFileName);
                Drawable d = Drawable.createFromStream(is, null);
                ivFlag.setImageDrawable(d);
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
                ivFlag.setImageDrawable(null);
            }
        }

        // ---------------------------------------------------------------------
        // Tile content helpers
        // ---------------------------------------------------------------------

        private void applyTile(LinearLayout tile,
                                TextView textView,
                                CountryGuess.ComparisonResult result,
                                boolean forceGreen) {
            if (result == null) return;

            int bg;
            if (forceGreen) {
                bg = R.drawable.tile_green;
            } else {
                switch (result.getStatus()) {
                    case CORRECT: bg = R.drawable.tile_green;  break;
                    case CLOSE:   bg = R.drawable.tile_orange; break;
                    default:      bg = R.drawable.tile_grey;   break;
                }
            }
            tile.setBackgroundResource(bg);

            String text = result.getDisplayValue();
            if (result.getDirection() == CountryGuess.ComparisonResult.Direction.UP) {
                text = text + " ↑";
            } else if (result.getDirection() == CountryGuess.ComparisonResult.Direction.DOWN) {
                text = text + " ↓";
            }
            textView.setText(text);
        }

        private void applyFlagColorsTile(CountryGuess.FlagColorsResult result) {
            if (result == null) {
                tileFlagColors.setBackgroundResource(R.drawable.tile_grey);
                tvFlagColors.setText("—");
                return;
            }

            // Choose tile background based on status
            switch (result.getStatus()) {
                case FULL_MATCH:
                    tileFlagColors.setBackgroundResource(R.drawable.tile_green);
                    break;
                case PARTIAL_MATCH:
                    tileFlagColors.setBackgroundResource(R.drawable.tile_orange);
                    break;
                default:
                    tileFlagColors.setBackgroundResource(R.drawable.tile_grey);
                    break;
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                tvFlagColors.setText(
                        Html.fromHtml(result.getDisplayText(), Html.FROM_HTML_MODE_LEGACY));
            } else {
                //noinspection deprecation
                tvFlagColors.setText(Html.fromHtml(result.getDisplayText()));
            }
        }
    }
}
