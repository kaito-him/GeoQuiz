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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Level-select screen for a single difficulty tier in the Guess by the Map mode.
 *
 * Receives the chosen {@link MapDifficulty} via the intent extra
 * {@link #EXTRA_DIFFICULTY} (its ordinal).
 *
 * Shows a 4-column grid of level cells ordered within the chosen difficulty,
 * with the same green/wheat/grey cell states as the Country Quest grid.
 *
 * Progress is persisted in {@link MapProgressManager} and is completely
 * independent of Country Quest progress.
 */
public class MapLevelSelectActivity extends AppCompatActivity {

    /** Intent extra: ordinal of the {@link MapDifficulty} to display. */
    public static final String EXTRA_DIFFICULTY = "map_difficulty_ordinal";

    /**
     * Intent extra: 1-based level number within the difficulty tier.
     * Passed when launching the (future) game activity.
     */
    public static final String EXTRA_LEVEL = "map_level";

    private static final int COLUMNS = 4;

    private RecyclerView        rvLevels;
    private TextView            tvTitle;
    private TextView            tvProgress;
    private CountryRepository   repo;
    private MapProgressManager  progress;
    private MapDifficulty       difficulty;

    /** Countries for the selected difficulty, in their repo order. */
    private List<Country> difficultyCountries;

    /** Prevents refreshGrid() being called before onCreate() finishes. */
    private boolean readyToRefresh = false;

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
        setContentView(R.layout.activity_map_level_select);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        // Resolve difficulty from intent
        int ordinal = getIntent().getIntExtra(EXTRA_DIFFICULTY, -1);
        if (ordinal < 0 || ordinal >= MapDifficulty.values().length) {
            Toast.makeText(this, R.string.error_load_countries, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        difficulty = MapDifficulty.values()[ordinal];

        // Load country data
        repo = CountryRepository.getInstance();
        if (!repo.loadCountries(this)) {
            Toast.makeText(this, R.string.error_load_countries, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Filter countries for this difficulty, preserving the sorted order from the repo
        difficultyCountries = getCountriesForDifficulty(difficulty);

        progress = new MapProgressManager(this);

        // Views
        tvTitle    = findViewById(R.id.tvTitle);
        tvProgress = findViewById(R.id.tvProgress);
        rvLevels   = findViewById(R.id.rvLevels);
        rvLevels.setLayoutManager(new GridLayoutManager(this, COLUMNS));

        tvTitle.setText(difficulty.getLabel(this));

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Background blur
        blurBackground(findViewById(R.id.backgroundImage));

        // Initial grid
        refreshGrid();
        readyToRefresh = true;
    }

    /**
     * Rebuild the grid when the user returns from the (future) game activity
     * so newly completed levels appear green immediately.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (readyToRefresh) {
            refreshGrid();
        }
    }

    // -------------------------------------------------------------------------
    // Grid
    // -------------------------------------------------------------------------

    private void refreshGrid() {
        List<Integer> doneList  = progress.loadCompletedLevels(difficulty);
        int totalLevels         = difficultyCountries.size();
        int currentLevel        = progress.loadCurrentLevel(difficulty);

        tvProgress.setText(getString(R.string.progress_format, doneList.size(), totalLevels));

        rvLevels.setAdapter(new MapLevelAdapter(
                this,
                difficultyCountries,
                difficulty,
                progress,
                this::openLevel
        ));

        // Scroll so the current level is near the top
        int targetRow   = (currentLevel - 1) / COLUMNS;
        int scrollToRow = Math.max(0, targetRow - 1);
        rvLevels.scrollToPosition(scrollToRow * COLUMNS);
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void openLevel(int levelWithinDifficulty) {
        if (levelWithinDifficulty < 1 || levelWithinDifficulty > difficultyCountries.size()) return;

        Intent intent = new Intent(this, GuessByMapActivity.class);
        intent.putExtra(EXTRA_DIFFICULTY, difficulty.ordinal());
        intent.putExtra(EXTRA_LEVEL, levelWithinDifficulty);
        startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Country filtering
    // -------------------------------------------------------------------------

    /**
     * Returns a sublist of all countries whose {@code difficultyCategory} matches
     * the given difficulty, in the order they appear in {@link CountryRepository}.
     *
     * The repository already groups and shuffles by tier (Easy → Intermediate →
     * Hard → Expert), so the order returned here is stable for the app session.
     */
    private List<Country> getCountriesForDifficulty(MapDifficulty diff) {
        List<Country> result = new ArrayList<>();
        for (Country c : repo.getAllCountries()) {
            if (diff.dataKey.equalsIgnoreCase(
                    c.getDifficultyCategory() == null ? "" : c.getDifficultyCategory().trim())) {
                result.add(c);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Background blur (same as DailyLevelSelectActivity)
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
