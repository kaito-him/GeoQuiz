package com.example.geoquiz;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DailyLevelSelectActivity extends AppCompatActivity {

    /** Key used when launching DailyCountryActivity with a specific level. */
    static final String EXTRA_LEVEL = "extra_level";

    private static final int COLUMNS = 4;

    private RecyclerView rvLevels;
    private TextView     tvProgress;
    private CountryRepository    repo;
    private DailyProgressManager progress;

    // Prevents refreshGrid() being called before onCreate() has finished.
    private boolean readyToRefresh = false;

    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_level_select);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Background blur
        blurBackground(findViewById(R.id.backgroundImage));

        // Dependencies
        repo = CountryRepository.getInstance();
        if (!repo.loadCountries(this)) {
            Toast.makeText(this, R.string.error_load_countries, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        progress = new DailyProgressManager(this);

        // Views
        tvProgress = findViewById(R.id.tvProgress);
        rvLevels   = findViewById(R.id.rvLevels);
        rvLevels.setLayoutManager(new GridLayoutManager(this, COLUMNS));

        // Initial grid build
        refreshGrid();
        readyToRefresh = true;
    }

    /**
     * When the user returns from DailyCountryActivity (e.g. after completing a level),
     * rebuild the grid so the new state (green tick) is immediately visible.
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
        int currentLevel    = progress.loadCurrentLevel();
        List<Integer> doneList = progress.loadCompletedLevels();
        Set<Integer> completedSet = new HashSet<>(doneList);
        int totalLevels     = repo.getTotalCountries();

        tvProgress.setText(doneList.size() + " / " + totalLevels + " completed");

        rvLevels.setAdapter(new LevelSelectAdapter(
                this,
                totalLevels,
                currentLevel,
                completedSet,
                this::openLevel
        ));

        // Scroll so the current level cell is near the top
        int targetRow     = (currentLevel - 1) / COLUMNS;
        int scrollToRow   = Math.max(0, targetRow - 1);
        rvLevels.scrollToPosition(scrollToRow * COLUMNS);
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void openLevel(int level) {
        Intent intent = new Intent(this, DailyCountryActivity.class);
        intent.putExtra(EXTRA_LEVEL, level);
        startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Background blur
    // -------------------------------------------------------------------------

    private void blurBackground(ImageView imageView) {
        try {
            BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();
            if (drawable == null) return;
            Bitmap bitmap  = drawable.getBitmap();
            Bitmap blurred = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            RenderScript         rs   = RenderScript.create(this);
            Allocation           in   = Allocation.createFromBitmap(rs, blurred);
            Allocation           out  = Allocation.createTyped(rs, in.getType());
            ScriptIntrinsicBlur  blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
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
