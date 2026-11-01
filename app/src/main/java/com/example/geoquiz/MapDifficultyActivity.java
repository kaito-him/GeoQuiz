package com.example.geoquiz;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Difficulty selection screen for the Guess by the Map game mode.
 *
 * Displays four difficulty options (Easy / Intermediate / Hard / Expert).
 * Each option shows the difficulty name and the player's current progress
 * (completed / total levels) for that tier.
 *
 * Tapping a difficulty option opens {@link MapLevelSelectActivity} for that tier.
 *
 * Progress for each difficulty is loaded from {@link MapProgressManager} and
 * is entirely independent of Country Quest progress.
 */
public class MapDifficultyActivity extends AppCompatActivity {

    private MapProgressManager  progressManager;
    private CountryRepository   repo;

    // -------------------------------------------------------------------------
    // Locale
    // -------------------------------------------------------------------------

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageManager.applyLanguage(base));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_difficulty);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        // Load countries (repository is a singleton; safe if already loaded)
        repo = CountryRepository.getInstance();
        if (!repo.loadCountries(this)) {
            Toast.makeText(this, R.string.error_load_countries, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressManager = new MapProgressManager(this);

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Background blur
        blurBackground(findViewById(R.id.backgroundImage));

        // Wire up the four difficulty cards
        setupDifficultyCard(R.id.cardEasy,         MapDifficulty.EASY);
        setupDifficultyCard(R.id.cardIntermediate, MapDifficulty.INTERMEDIATE);
        setupDifficultyCard(R.id.cardHard,         MapDifficulty.HARD);
        setupDifficultyCard(R.id.cardExpert,       MapDifficulty.EXPERT);
    }

    /** Refresh progress badges every time we return from the level-select screen. */
    @Override
    protected void onResume() {
        super.onResume();
        updateProgressBadges();
    }

    // -------------------------------------------------------------------------
    // Difficulty card setup
    // -------------------------------------------------------------------------

    private void setupDifficultyCard(int cardViewId, MapDifficulty difficulty) {
        View card = findViewById(cardViewId);
        if (card == null) return;

        // Difficulty label
        TextView tvLabel = card.findViewById(R.id.tvDifficultyLabel);
        if (tvLabel != null) tvLabel.setText(difficulty.getLabel(this));

        // Progress badge
        updateProgressBadge(card, difficulty);

        // Navigate to level select
        card.setOnClickListener(v -> openLevelSelect(difficulty));
    }

    private void updateProgressBadge(View card, MapDifficulty difficulty) {
        TextView tvProgress = card.findViewById(R.id.tvDifficultyProgress);
        if (tvProgress == null) return;

        int total     = countForDifficulty(difficulty);
        int completed = progressManager.completedCount(difficulty);
        tvProgress.setText(getString(R.string.progress_format, completed, total));
    }

    private void updateProgressBadges() {
        updateProgressBadge(findViewById(R.id.cardEasy),         MapDifficulty.EASY);
        updateProgressBadge(findViewById(R.id.cardIntermediate), MapDifficulty.INTERMEDIATE);
        updateProgressBadge(findViewById(R.id.cardHard),         MapDifficulty.HARD);
        updateProgressBadge(findViewById(R.id.cardExpert),       MapDifficulty.EXPERT);
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void openLevelSelect(MapDifficulty difficulty) {
        Intent intent = new Intent(this, MapLevelSelectActivity.class);
        intent.putExtra(MapLevelSelectActivity.EXTRA_DIFFICULTY, difficulty.ordinal());
        startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Count how many countries belong to the given difficulty tier.
     * We iterate the full list to mirror exactly what MapLevelSelectActivity sees.
     */
    private int countForDifficulty(MapDifficulty difficulty) {
        int count = 0;
        for (Country c : repo.getAllCountries()) {
            if (difficulty.dataKey.equalsIgnoreCase(
                    c.getDifficultyCategory() == null ? "" : c.getDifficultyCategory().trim())) {
                count++;
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Background blur (identical helper as other activities)
    // -------------------------------------------------------------------------

    private void blurBackground(ImageView imageView) {
        try {
            BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();
            if (drawable == null) return;
            Bitmap bitmap  = drawable.getBitmap();
            Bitmap blurred = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            RenderScript        rs   = RenderScript.create(this);
            Allocation          in   = Allocation.createFromBitmap(rs, blurred);
            Allocation          out  = Allocation.createTyped(rs, in.getType());
            ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blur.setRadius(8f);
            blur.setInput(in);
            blur.forEach(out);
            out.copyTo(blurred);
            imageView.setImageBitmap(blurred);
            rs.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
