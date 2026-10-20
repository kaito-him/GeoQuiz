package com.example.geoquiz;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Set;

/**
 * Grid adapter for the Daily Country level-select screen.
 *
 * Cell states
 * -----------
 * COMPLETED  – level is in the completed set      → green cell, ✓ icon, clickable
 * CURRENT    – level == currentLevel              → wheat cell, ▶ icon, clickable
 * LOCKED     – level > currentLevel               → grey cell, 🔒 icon, NOT clickable
 */
public class LevelSelectAdapter extends RecyclerView.Adapter<LevelSelectAdapter.LevelViewHolder> {

    public interface OnLevelClickListener {
        void onLevelClick(int level);
    }

    private final int totalLevels;
    private final int currentLevel;
    private final Set<Integer> completedLevels;
    private final OnLevelClickListener listener;
    private final Context context;

    public LevelSelectAdapter(Context context,
                              int totalLevels,
                              int currentLevel,
                              Set<Integer> completedLevels,
                              OnLevelClickListener listener) {
        this.context         = context;
        this.totalLevels     = totalLevels;
        this.currentLevel    = currentLevel;
        this.completedLevels = completedLevels;
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
        final TextView tvDayLabel;
        final TextView tvStatusIcon;

        LevelViewHolder(@NonNull View itemView) {
            super(itemView);
            cellContainer = itemView.findViewById(R.id.cellContainer);
            tvDayLabel    = itemView.findViewById(R.id.tvDayLabel);
            tvStatusIcon  = itemView.findViewById(R.id.tvStatusIcon);
        }

        void bind(int level) {
            tvDayLabel.setText("Day " + level);

            boolean completed = completedLevels.contains(level);
            boolean isCurrent = (level == currentLevel);

            if (completed) {
                // Green — already beaten
                cellContainer.setBackgroundResource(R.drawable.cell_completed);
                tvStatusIcon.setText("✓");
                tvStatusIcon.setTextColor(context.getResources().getColor(android.R.color.white, null));
                tvDayLabel.setTextColor(context.getResources().getColor(android.R.color.white, null));
                cellContainer.setAlpha(1f);
                cellContainer.setClickable(true);
                cellContainer.setFocusable(true);
                cellContainer.setOnClickListener(v -> listener.onLevelClick(level));

            } else if (isCurrent) {
                // Wheat — current playable level
                cellContainer.setBackgroundResource(R.drawable.cell_current);
                tvStatusIcon.setText("▶");
                tvStatusIcon.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark, null));
                tvDayLabel.setTextColor(0xFF3E2723); // dark brown
                cellContainer.setAlpha(1f);
                cellContainer.setClickable(true);
                cellContainer.setFocusable(true);
                cellContainer.setOnClickListener(v -> listener.onLevelClick(level));

            } else {
                // Grey — locked
                cellContainer.setBackgroundResource(R.drawable.cell_locked);
                tvStatusIcon.setText("🔒");
                tvStatusIcon.setTextColor(context.getResources().getColor(android.R.color.white, null));
                tvDayLabel.setTextColor(context.getResources().getColor(android.R.color.white, null));
                cellContainer.setAlpha(0.55f);
                cellContainer.setClickable(false);
                cellContainer.setFocusable(false);
                cellContainer.setOnClickListener(null);
            }
        }
    }
}
