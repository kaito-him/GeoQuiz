package com.example.geoquiz;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Grid adapter for the Guess by the Map level-select screen.
 *
 * Four visual states (keyed off {@link MapProgressManager.LevelOutcome}):
 *
 *   COMPLETED  – green  (cell_completed), icon ✓, clickable (replay)
 *   FAILED     – red    (cell_failed),    icon ✗, clickable (replay)
 *   UNLOCKED   – wheat  (cell_current),   icon ▶, clickable (play)
 *   LOCKED     – grey   (cell_locked),    icon 🔒, shows toast on tap
 *
 * The adapter queries {@link MapProgressManager} directly so its state is
 * always current without requiring the host activity to pre-compute sets.
 */
public class MapLevelAdapter extends RecyclerView.Adapter<MapLevelAdapter.LevelViewHolder> {

    public interface OnLevelClickListener {
        /** Called when the player taps a COMPLETED, FAILED, or UNLOCKED cell. */
        void onLevelClick(int levelWithinDifficulty);
    }

    private final Context              context;
    private final List<Country>        countries;      // ordered list for this difficulty
    private final MapDifficulty        difficulty;
    private final MapProgressManager   progressManager;
    private final OnLevelClickListener listener;

    public MapLevelAdapter(Context context,
                           List<Country> countries,
                           MapDifficulty difficulty,
                           MapProgressManager progressManager,
                           OnLevelClickListener listener) {
        this.context         = context;
        this.countries       = countries;
        this.difficulty      = difficulty;
        this.progressManager = progressManager;
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
        holder.bind(position + 1); // 1-based
    }

    @Override
    public int getItemCount() {
        return countries.size();
    }

    // =========================================================================
    // ViewHolder
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
            tvDifficulty.setVisibility(View.GONE); // not needed within a single-difficulty screen

            MapProgressManager.LevelOutcome outcome =
                    progressManager.getOutcome(difficulty, level);

            switch (outcome) {

                case COMPLETED:
                    cellContainer.setBackgroundResource(R.drawable.cell_completed);
                    tvStatusIcon.setText("✓");
                    tvStatusIcon.setTextColor(Color.WHITE);
                    tvDayLabel.setTextColor(Color.WHITE);
                    cellContainer.setAlpha(1f);
                    cellContainer.setClickable(true);
                    cellContainer.setFocusable(true);
                    cellContainer.setOnClickListener(v -> listener.onLevelClick(level));
                    break;

                case FAILED:
                    cellContainer.setBackgroundResource(R.drawable.cell_failed);
                    tvStatusIcon.setText("✗");
                    tvStatusIcon.setTextColor(Color.WHITE);
                    tvDayLabel.setTextColor(Color.WHITE);
                    cellContainer.setAlpha(1f);
                    cellContainer.setClickable(true);
                    cellContainer.setFocusable(true);
                    cellContainer.setOnClickListener(v -> listener.onLevelClick(level));
                    break;

                case UNLOCKED:
                    cellContainer.setBackgroundResource(R.drawable.cell_current);
                    tvStatusIcon.setText("▶");
                    tvStatusIcon.setTextColor(
                            context.getResources().getColor(android.R.color.holo_orange_dark, null));
                    tvDayLabel.setTextColor(0xFF3E2723);
                    cellContainer.setAlpha(1f);
                    cellContainer.setClickable(true);
                    cellContainer.setFocusable(true);
                    cellContainer.setOnClickListener(v -> listener.onLevelClick(level));
                    break;

                case LOCKED:
                default:
                    cellContainer.setBackgroundResource(R.drawable.cell_locked);
                    tvStatusIcon.setText("🔒");
                    tvStatusIcon.setTextColor(Color.WHITE);
                    tvDayLabel.setTextColor(Color.WHITE);
                    cellContainer.setAlpha(0.55f);
                    cellContainer.setClickable(true);
                    cellContainer.setFocusable(true);
                    cellContainer.setOnClickListener(
                            v -> Toast.makeText(context,
                                    context.getString(R.string.map_level_locked_message),
                                    Toast.LENGTH_SHORT).show());
                    break;
            }
        }
    }
}
