package com.example.geoquiz;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Set;

/**
 * Grid adapter for the Country Quest level-select screen.
 *
 * Cell states
 * -----------
 * COMPLETED  – level is in the completed set      → green cell, ✓ icon, clickable
 * CURRENT    – level == currentLevel              → wheat cell, ▶ icon, clickable
 * LOCKED     – level > currentLevel               → grey cell, 🔒 icon, NOT clickable
 *
 * Each cell also shows the difficulty label (Easy / Intermediate / Hard / Expert)
 * with the following text colours:
 *   Easy         → white        (#FFFFFF)
 *   Intermediate → tan          (#D2B48C)
 *   Hard         → black        (#000000)
 *   Expert       → deep purple  (#4A148C)
 */
public class LevelSelectAdapter extends RecyclerView.Adapter<LevelSelectAdapter.LevelViewHolder> {

    public interface OnLevelClickListener {
        void onLevelClick(int level);
    }

    private final int                  totalLevels;
    private final int                  currentLevel;
    private final Set<Integer>         completedLevels;
    private final OnLevelClickListener listener;
    private final Context              context;
    private final CountryRepository    repo;

    public LevelSelectAdapter(Context context,
                              int totalLevels,
                              int currentLevel,
                              Set<Integer> completedLevels,
                              CountryRepository repo,
                              OnLevelClickListener listener) {
        this.context         = context;
        this.totalLevels     = totalLevels;
        this.currentLevel    = currentLevel;
        this.completedLevels = completedLevels;
        this.repo            = repo;
        this.listener        = listener;
    }

    @NonNull
    @Override
    public LevelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_level_cell, parent, false);
        return new LevelViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LevelViewHolder holder, int position) {
        int level = position + 1; // 1-based
        holder.bind(level);
    }

    @Override
    public int getItemCount() {
        return totalLevels;
    }

    // =========================================================================

    class LevelViewHolder extends RecyclerView.ViewHolder {

        final LinearLayout cellContainer;
        final TextView     tvDayLabel;
        final TextView     tvStatusIcon;
        final TextView     tvDifficulty;

        LevelViewHolder(@NonNull View itemView) {
            super(itemView);
            cellContainer = itemView.findViewById(R.id.cellContainer);
            tvDayLabel    = itemView.findViewById(R.id.tvDayLabel);
            tvStatusIcon  = itemView.findViewById(R.id.tvStatusIcon);
            tvDifficulty  = itemView.findViewById(R.id.tvDifficulty);
        }

        void bind(int level) {
            tvDayLabel.setText(context.getString(R.string.level_label_format, level));

            boolean completed = completedLevels.contains(level);
            boolean isCurrent = (level == currentLevel);

            // --- Difficulty label ---
            Country country = repo.getCountryByLevel(level);
            String difficulty = (country != null) ? country.getDifficultyCategory() : "";
            String difficultyLabel = localizedDifficulty(difficulty);
            if (difficultyLabel != null && !difficultyLabel.isEmpty()) {
                tvDifficulty.setText(difficultyLabel);
                tvDifficulty.setVisibility(View.VISIBLE);
            } else {
                tvDifficulty.setVisibility(View.GONE);
            }

            // Difficulty label colour is always tier-based regardless of cell state
            if (difficultyLabel != null && !difficultyLabel.isEmpty()) {
                tvDifficulty.setTextColor(difficultyColor(difficulty));
                tvDifficulty.setAlpha(1f);
            }

            // --- Cell state ---
            if (completed) {
                cellContainer.setBackgroundResource(R.drawable.cell_completed);
                tvStatusIcon.setText("✓");
                tvStatusIcon.setTextColor(Color.WHITE);
                tvDayLabel.setTextColor(Color.WHITE);
                cellContainer.setAlpha(1f);
                cellContainer.setClickable(true);
                cellContainer.setFocusable(true);
                cellContainer.setOnClickListener(v -> listener.onLevelClick(level));

            } else if (isCurrent) {
                cellContainer.setBackgroundResource(R.drawable.cell_current);
                tvStatusIcon.setText("▶");
                tvStatusIcon.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark, null));
                tvDayLabel.setTextColor(0xFF3E2723);
                cellContainer.setAlpha(1f);
                cellContainer.setClickable(true);
                cellContainer.setFocusable(true);
                cellContainer.setOnClickListener(v -> listener.onLevelClick(level));

            } else {
                // Locked
                cellContainer.setBackgroundResource(R.drawable.cell_locked);
                tvStatusIcon.setText("🔒");
                tvStatusIcon.setTextColor(Color.WHITE);
                tvDayLabel.setTextColor(Color.WHITE);
                cellContainer.setAlpha(0.55f);
                cellContainer.setClickable(false);
                cellContainer.setFocusable(false);
                cellContainer.setOnClickListener(null);
            }
        }

        /** Returns the text colour for each difficulty tier. */
        private int difficultyColor(String difficulty) {
            switch (difficulty.trim().toLowerCase()) {
                case "easy":         return Color.WHITE;           // #FFFFFF
                case "intermediate": return Color.parseColor("#6D4C41"); // brown
                case "hard":         return Color.BLACK;           // #000000
                case "expert":       return Color.parseColor("#4A148C"); // deep purple
                default:             return Color.WHITE;
            }
        }

        /** Returns a localised difficulty label string. */
        private String localizedDifficulty(String difficulty) {
            if (difficulty == null || difficulty.isEmpty()) return "";
            switch (difficulty.trim().toLowerCase()) {
                case "easy":         return context.getString(R.string.difficulty_easy);
                case "intermediate": return context.getString(R.string.difficulty_intermediate);
                case "hard":         return context.getString(R.string.difficulty_hard);
                case "expert":       return context.getString(R.string.difficulty_expert);
                default:             return difficulty;
            }
        }
    }
}
