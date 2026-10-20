package com.example.geoquiz;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DailyCountryActivity extends AppCompatActivity {

    private static final int MAX_GUESSES = 10;

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------
    private TextView             tvLevelIndicator;
    private TextView             tvGuessCounter;
    private AutoCompleteTextView etCountryInput;
    /** Hint button — replaces the old Search button. */
    private TextView             btnHint;
    private TextView             btnCloseHints;
    private RecyclerView         rvGuessList;
    private ImageButton          btnBack;

    // Hints panel views
    private LinearLayout layoutHintsPanel;
    private LinearLayout layoutHintRows;
    private TextView     tvHintCoinBalance;
    private TextView     tvSlot1Status;
    private TextView     tvSlot2Status;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private CountryRepository    repository;
    private DailyProgressManager progressManager;
    private CoinManager          coinManager;
    private GuessAdapter         guessAdapter;
    private HintManager          hintManager;

    private int     currentLevel   = 1;
    private Country targetCountry  = null;
    private boolean levelCompleted = false;
    /** True when replaying an already-completed level from the grid. */
    private boolean isReplayMode   = false;

    private final List<String> guessedCountryNames = new ArrayList<>();
    private final Set<String>  guessedNamesLower   = new HashSet<>();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_country);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        repository = CountryRepository.getInstance();
        if (!repository.loadCountries(this)) {
            Toast.makeText(this, R.string.error_load_countries, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressManager = new DailyProgressManager(this);
        coinManager     = new CoinManager(this);

        // Level from Intent (level-select) or saved progress
        int intentLevel = getIntent().getIntExtra(DailyLevelSelectActivity.EXTRA_LEVEL, -1);
        if (intentLevel >= 1 && intentLevel <= repository.getTotalCountries()) {
            currentLevel = intentLevel;
        } else {
            currentLevel = progressManager.loadCurrentLevel();
        }
        if (currentLevel < 1 || currentLevel > repository.getTotalCountries()) {
            currentLevel = 1;
        }

        // Detect replay mode
        int unlockedLevel = progressManager.loadCurrentLevel();
        isReplayMode = progressManager.isLevelCompleted(currentLevel)
                       && currentLevel < unlockedLevel;

        targetCountry = repository.getCountryByLevel(currentLevel);
        if (targetCountry == null) {
            Toast.makeText(this, "Invalid level", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        hintManager = new HintManager(targetCountry, repository);

        initViews();
        blurBackground(findViewById(R.id.backgroundImage));

        guessAdapter = new GuessAdapter(this);
        rvGuessList.setLayoutManager(new LinearLayoutManager(this));
        rvGuessList.setAdapter(guessAdapter);

        setupAutocomplete();
        restoreGuessHistory();
        restoreHintState();
        updateLevelIndicator();
        updateGuessCounter();
        updateHintButton();       // set initial enabled/alpha state
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveGuessHistory();
        saveHintState();
    }

    // -------------------------------------------------------------------------
    // View init
    // -------------------------------------------------------------------------

    private void initViews() {
        btnBack           = findViewById(R.id.btnBack);
        tvLevelIndicator  = findViewById(R.id.tvLevelIndicator);
        tvGuessCounter    = findViewById(R.id.tvGuessCounter);
        etCountryInput    = findViewById(R.id.etCountryInput);
        btnHint           = findViewById(R.id.btnHint);
        btnCloseHints     = findViewById(R.id.btnCloseHints);
        rvGuessList       = findViewById(R.id.rvGuessList);
        layoutHintsPanel  = findViewById(R.id.layoutHintsPanel);
        layoutHintRows    = findViewById(R.id.layoutHintRows);
        tvHintCoinBalance = findViewById(R.id.tvHintCoinBalance);
        tvSlot1Status     = findViewById(R.id.tvSlot1Status);
        tvSlot2Status     = findViewById(R.id.tvSlot2Status);

        btnBack.setOnClickListener(v -> finish());

        // Hint button opens the hints panel
        btnHint.setOnClickListener(v -> {
            if (btnHint.isEnabled()) showHintsPanel();
        });

        // Close button collapses the hints panel
        btnCloseHints.setOnClickListener(v -> layoutHintsPanel.setVisibility(View.GONE));

        // IME "search" action still submits the current text
        etCountryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                submitCurrentInput();
            }
            return true;
        });
    }

    private void setupAutocomplete() {
        List<String> allCountries = new ArrayList<>();
        for (Country c : repository.getAllCountries()) allCountries.add(c.getCountry());

        CountrySuggestionAdapter adapter =
                new CountrySuggestionAdapter(this, allCountries, guessedNamesLower);
        etCountryInput.setAdapter(adapter);
        etCountryInput.setThreshold(1);

        // Auto-submit when the user taps a suggestion from the dropdown
        etCountryInput.setOnItemClickListener((parent, view, position, id) -> {
            // The AutoCompleteTextView fills its own text on item click; we
            // post the submission so the text is fully set before we read it.
            etCountryInput.post(() -> submitCurrentInput());
        });
    }

    // -------------------------------------------------------------------------
    // Hint button state
    // -------------------------------------------------------------------------

    /**
     * Enable the hint button when there is at least one available hint slot
     * (unlocked and not yet used) AND the level is not finished.
     * Disabled states get alpha 0.4 for clear visual feedback.
     */
    private void updateHintButton() {
        int guessCount = guessedCountryNames.size();
        boolean slotAvailable = hintManager.nextAvailableSlot(guessCount) != -1;
        boolean active = slotAvailable && !levelCompleted;

        btnHint.setEnabled(active);
        btnHint.setAlpha(active ? 1f : 0.4f);
    }

    // -------------------------------------------------------------------------
    // Hints panel — opened by tapping the hint button
    // -------------------------------------------------------------------------

    /** Populate and reveal the hints panel. */
    private void showHintsPanel() {
        int guessCount = guessedCountryNames.size();

        tvHintCoinBalance.setText("🪙 " + coinManager.getCoins());

        boolean slot1Unlocked = hintManager.isSlot1Unlocked(guessCount);
        boolean slot2Unlocked = hintManager.isSlot2Unlocked(guessCount);
        updateSlotStatusBadge(tvSlot1Status, 1, slot1Unlocked, hintManager.isSlot1Used());
        updateSlotStatusBadge(tvSlot2Status, 2, slot2Unlocked, hintManager.isSlot2Used());

        layoutHintRows.removeAllViews();
        for (HintType type : HintType.values()) {
            addHintRow(type, guessCount);
        }

        layoutHintsPanel.setVisibility(View.VISIBLE);
    }

    /**
     * Refresh the contents of the panel if it is currently visible
     * (called after a hint is purchased so the bought row turns green).
     */
    private void refreshHintsPanelIfVisible() {
        if (layoutHintsPanel.getVisibility() == View.VISIBLE) {
            showHintsPanel();
        }
    }

    private void updateSlotStatusBadge(TextView badge, int slotIndex,
                                       boolean unlocked, boolean used) {
        if (used) {
            badge.setText("Slot " + slotIndex + ": ✅ Used");
            badge.setTextColor(Color.parseColor("#1B5E20"));
        } else if (unlocked) {
            badge.setText("Slot " + slotIndex + ": 🔓 Unlocked");
            badge.setTextColor(Color.parseColor("#1B5E20"));
        } else {
            int threshold = (slotIndex == 1)
                    ? HintManager.SLOT1_UNLOCK_AFTER
                    : HintManager.SLOT2_UNLOCK_AFTER;
            badge.setText("Slot " + slotIndex + ": 🔒 After guess " + threshold);
            badge.setTextColor(Color.parseColor("#5D4037"));
        }
    }

    private void addHintRow(HintType type, int guessCount) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View row = inflater.inflate(R.layout.item_hint_row, layoutHintRows, false);

        TextView tvLabel   = row.findViewById(R.id.tvHintLabel);
        TextView tvSubtext = row.findViewById(R.id.tvHintSubtext);
        TextView tvPrice   = row.findViewById(R.id.tvHintPrice);
        View     rowRoot   = row.findViewById(R.id.hintRowRoot);

        tvLabel.setText(type.label);

        // Case 1: already purchased
        HintManager.PurchasedHint ph = findPurchasedHintByType(type);
        if (ph != null) {
            rowRoot.setBackgroundResource(R.drawable.tile_green);
            tvLabel.setTextColor(Color.WHITE);
            tvSubtext.setVisibility(View.VISIBLE);
            tvSubtext.setText(ph.revealedText);
            tvSubtext.setTextColor(Color.WHITE);
            tvPrice.setText("✅ Slot " + ph.slotIndex);
            tvPrice.setTextColor(Color.WHITE);
            tvPrice.setBackgroundResource(R.drawable.tile_green);
            rowRoot.setClickable(false);
            rowRoot.setFocusable(false);
            layoutHintRows.addView(row);
            return;
        }

        // Case 2: evaluate eligibility
        HintManager.EligibilityStatus status = hintManager.checkEligibility(type, guessCount);

        switch (status) {
            case AVAILABLE:
                rowRoot.setBackgroundResource(R.drawable.button_background);
                tvLabel.setTextColor(Color.parseColor("#3E2723"));
                tvSubtext.setVisibility(View.GONE);
                tvPrice.setText(type.price + " 🪙");
                tvPrice.setTextColor(Color.parseColor("#3E2723"));
                tvPrice.setBackgroundResource(R.drawable.coin_background);
                rowRoot.setClickable(true);
                rowRoot.setFocusable(true);
                rowRoot.setOnClickListener(v -> onHintRowClicked(type, guessCount));
                break;

            case ALREADY_KNOWN:
                styleDisabledRow(rowRoot, tvLabel, tvSubtext, tvPrice,
                        "Already known from guesses", type.price);
                break;

            case CANNOT_ELIMINATE:
                styleDisabledRow(rowRoot, tvLabel, tvSubtext, tvPrice,
                        "Not enough candidates", type.price);
                break;

            case SLOT_NOT_UNLOCKED: {
                int threshold = !hintManager.isSlot1Used()
                        ? HintManager.SLOT1_UNLOCK_AFTER
                        : HintManager.SLOT2_UNLOCK_AFTER;
                styleDisabledRow(rowRoot, tvLabel, tvSubtext, tvPrice,
                        "Unlocks after guess " + threshold, type.price);
                break;
            }

            case NO_SLOTS_REMAINING:
            case SLOT_USED:
                styleDisabledRow(rowRoot, tvLabel, tvSubtext, tvPrice,
                        "No hint slots remaining", type.price);
                break;
        }

        layoutHintRows.addView(row);
    }

    private void styleDisabledRow(View rowRoot, TextView label, TextView subtext,
                                  TextView price, String reason, int hintPrice) {
        rowRoot.setBackgroundResource(R.drawable.tile_grey);
        label.setTextColor(Color.WHITE);
        subtext.setVisibility(View.VISIBLE);
        subtext.setText(reason);
        subtext.setTextColor(Color.parseColor("#BDBDBD"));
        price.setText(hintPrice + " 🪙");
        price.setTextColor(Color.WHITE);
        price.setBackgroundResource(R.drawable.tile_grey);
        rowRoot.setClickable(false);
        rowRoot.setFocusable(false);
    }

    private HintManager.PurchasedHint findPurchasedHintByType(HintType type) {
        for (int slot = 1; slot <= 2; slot++) {
            HintManager.PurchasedHint ph = hintManager.getPurchasedHint(slot);
            if (ph != null && ph.type == type) return ph;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Hint purchase flow
    // -------------------------------------------------------------------------

    private void onHintRowClicked(HintType type, int guessCount) {
        HintManager.EligibilityStatus status = hintManager.checkEligibility(type, guessCount);
        if (status != HintManager.EligibilityStatus.AVAILABLE) {
            Toast.makeText(this, "This hint is no longer available.", Toast.LENGTH_SHORT).show();
            refreshHintsPanelIfVisible();
            return;
        }

        int nextSlot = hintManager.nextAvailableSlot(guessCount);
        if (nextSlot == -1) {
            Toast.makeText(this, "No hint slots available right now.", Toast.LENGTH_SHORT).show();
            return;
        }

        int balance = coinManager.getCoins();
        if (balance < type.price) {
            new AlertDialog.Builder(this)
                    .setTitle("Insufficient Coins")
                    .setMessage("You need " + type.price + " 🪙 for this hint, "
                            + "but you only have " + balance + " 🪙.\n\n"
                            + "Earn more coins by completing levels!")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String revealText = hintManager.generateRevealText(type);
        if (revealText == null || revealText.isEmpty()) {
            Toast.makeText(this, "This hint is not available right now.", Toast.LENGTH_SHORT).show();
            refreshHintsPanelIfVisible();
            return;
        }

        String confirmMsg = "Purchase \"" + type.label + "\" for " + type.price + " 🪙?\n\n"
                + "Your balance: " + balance + " 🪙\n"
                + "After purchase: " + (balance - type.price) + " 🪙\n\n"
                + "This will use hint slot " + nextSlot + ".";

        new AlertDialog.Builder(this)
                .setTitle("Buy Hint?")
                .setMessage(confirmMsg)
                .setCancelable(true)
                .setPositiveButton("Buy", (dialog, which) -> {
                    HintManager.EligibilityStatus reCheck =
                            hintManager.checkEligibility(type, guessedCountryNames.size());
                    if (reCheck != HintManager.EligibilityStatus.AVAILABLE) {
                        Toast.makeText(this, "Hint no longer available.", Toast.LENGTH_SHORT).show();
                        refreshHintsPanelIfVisible();
                        return;
                    }
                    int slot = hintManager.nextAvailableSlot(guessedCountryNames.size());
                    if (slot == -1) {
                        Toast.makeText(this, "No hint slot available.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean deducted = coinManager.deductCoins(type.price);
                    if (!deducted) {
                        Toast.makeText(this, "Insufficient coins.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    hintManager.recordHintPurchase(type, revealText, slot);
                    saveHintState();
                    // Update hint button (both slots may now be used)
                    updateHintButton();
                    refreshHintsPanelIfVisible();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Input / guess handling
    // -------------------------------------------------------------------------

    private void setInputEnabled(boolean enabled) {
        etCountryInput.setEnabled(enabled);
        // When input is locked (game over) the hint button is also locked
        if (!enabled) {
            btnHint.setEnabled(false);
            btnHint.setAlpha(0.4f);
            layoutHintsPanel.setVisibility(View.GONE);
        }
    }

    /** Submit whatever is currently typed in the autocomplete field. */
    private void submitCurrentInput() {
        if (levelCompleted) return;
        if (!isReplayMode && guessedCountryNames.size() >= MAX_GUESSES) return;

        String input = etCountryInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_input, Toast.LENGTH_SHORT).show();
            return;
        }
        if (guessedNamesLower.contains(input.toLowerCase())) {
            Toast.makeText(this, R.string.error_already_guessed, Toast.LENGTH_SHORT).show();
            etCountryInput.setText("");
            return;
        }
        Country guessedCountry = repository.findCountryByName(input);
        if (guessedCountry == null) {
            Toast.makeText(this, R.string.error_invalid_country, Toast.LENGTH_SHORT).show();
            return;
        }

        // Record
        guessedCountryNames.add(guessedCountry.getCountry());
        guessedNamesLower.add(guessedCountry.getCountry().toLowerCase());

        CountryGuess guess = CountryComparator.compare(guessedCountry, targetCountry);
        guessAdapter.addGuess(guess);
        rvGuessList.scrollToPosition(0);
        etCountryInput.setText("");
        etCountryInput.dismissDropDown();

        hintManager.onGuessMade(guess);

        saveGuessHistory();
        saveHintState();
        updateGuessCounter();

        // Collapse the hints panel after a guess (keeps the UI clean) and
        // refresh the hint button — a new slot may have just unlocked.
        layoutHintsPanel.setVisibility(View.GONE);
        updateHintButton();

        if (guess.isCorrect()) {
            setInputEnabled(false);
            levelCompleted = true;
            int coinsAwarded = 0;
            if (!isReplayMode) {
                progressManager.markLevelCompleted(currentLevel);
                coinsAwarded = awardCompletionReward();
            }
            final int finalCoinsAwarded = coinsAwarded;
            rvGuessList.post(() -> showSuccessDialog(finalCoinsAwarded));

        } else if (!isReplayMode && guessedCountryNames.size() >= MAX_GUESSES) {
            setInputEnabled(false);
            rvGuessList.post(this::showFailureDialog);
        }
    }

    private void updateLevelIndicator() {
        tvLevelIndicator.setText(currentLevel + "/" + repository.getTotalCountries());
    }

    private void updateGuessCounter() {
        int used = guessedCountryNames.size();
        tvGuessCounter.setText(used + " / " + MAX_GUESSES);
        if (!isReplayMode && used >= MAX_GUESSES - 2) {
            tvGuessCounter.setTextColor(0xFFD32F2F);
        } else {
            tvGuessCounter.setTextColor(0xFF3E2723);
        }
    }

    // -------------------------------------------------------------------------
    // Completion reward
    // -------------------------------------------------------------------------

    private int awardCompletionReward() {
        if (isReplayMode) return 0;
        if (progressManager.hasRewardBeenIssued(currentLevel)) return 0;

        int hintsUsed = (hintManager.isSlot1Used() ? 1 : 0)
                      + (hintManager.isSlot2Used() ? 1 : 0);
        int reward = RewardCalculator.compute(guessedCountryNames.size(), hintsUsed);

        if (reward > 0) coinManager.addCoins(reward);
        progressManager.markRewardIssued(currentLevel);
        return reward;
    }

    // -------------------------------------------------------------------------
    // Dialogs
    // -------------------------------------------------------------------------

    private void showSuccessDialog(int coinsAwarded) {
        int totalLevels = repository.getTotalCountries();
        boolean lastLevel = (currentLevel >= totalLevels);

        String coinsLine;
        if (isReplayMode) {
            coinsLine = "";
        } else {
            int hintsUsed = (hintManager.isSlot1Used() ? 1 : 0)
                          + (hintManager.isSlot2Used() ? 1 : 0);
            if (hintsUsed > 0) {
                int penalty = RewardCalculator.HINT_PENALTY * hintsUsed;
                coinsLine = "\n+" + coinsAwarded + " 🪙 coins earned!"
                        + "\n(Base " + RewardCalculator.BASE_REWARD
                        + " − " + penalty + " hint penalty)";
            } else {
                int bonus = RewardCalculator.performanceBonus(guessedCountryNames.size());
                if (bonus > 0) {
                    coinsLine = "\n+" + coinsAwarded + " 🪙 coins earned!"
                            + "\n(Base " + RewardCalculator.BASE_REWARD
                            + " + " + bonus + " speed bonus)";
                } else {
                    coinsLine = "\n+" + coinsAwarded + " 🪙 coins earned!";
                }
            }
        }

        String body = "You found it!\n\n"
                + "Country: " + targetCountry.getCountry() + "\n"
                + "Guesses: " + guessedCountryNames.size()
                + coinsLine;

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Congratulations! \uD83C\uDF89")
                .setCancelable(false);

        if (isReplayMode || lastLevel) {
            String msg = (lastLevel && !isReplayMode)
                    ? "You've completed all " + totalLevels + " levels! \uD83C\uDF0D\n\n"
                      + "Country: " + targetCountry.getCountry() + "\n"
                      + "Guesses: " + guessedCountryNames.size() + coinsLine
                    : body;
            builder.setMessage(msg).setPositiveButton("Finish", (d, w) -> finish());
        } else {
            builder.setMessage(body)
                   .setPositiveButton("Next Level", (d, w) -> loadNextLevel())
                   .setNegativeButton("Stay Here", null);
        }
        builder.show();
    }

    private void showFailureDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Out of guesses! \uD83D\uDE14")
                .setMessage("The country was: " + targetCountry.getCountry()
                        + "\n\nYou've used all " + MAX_GUESSES + " guesses."
                        + "\nTap 'Try Again' to reset this level and start over.")
                .setCancelable(false)
                .setPositiveButton("Try Again", (d, w) -> resetLevel())
                .setNegativeButton("Give Up",   (d, w) -> finish())
                .show();
    }

    // -------------------------------------------------------------------------
    // Level management
    // -------------------------------------------------------------------------

    private void resetLevel() {
        levelCompleted = false;
        guessedCountryNames.clear();
        guessedNamesLower.clear();
        guessAdapter.clearGuesses();
        progressManager.clearGuessHistory();
        progressManager.clearHintState();
        hintManager = new HintManager(targetCountry, repository);

        etCountryInput.setEnabled(true);
        etCountryInput.setText("");
        layoutHintsPanel.setVisibility(View.GONE);
        updateGuessCounter();
        updateHintButton();
    }

    private void loadNextLevel() {
        int nextLevel = currentLevel + 1;
        if (nextLevel > repository.getTotalCountries()) {
            Toast.makeText(this, "No more levels!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Country nextTarget = repository.getCountryByLevel(nextLevel);
        if (nextTarget == null) {
            Toast.makeText(this, "Error loading level", Toast.LENGTH_SHORT).show();
            return;
        }

        currentLevel  = nextLevel;
        targetCountry = nextTarget;
        isReplayMode  = false;
        levelCompleted = false;

        guessedCountryNames.clear();
        guessedNamesLower.clear();
        guessAdapter.clearGuesses();
        progressManager.clearGuessHistory();
        progressManager.clearHintState();
        hintManager = new HintManager(targetCountry, repository);

        etCountryInput.setEnabled(true);
        etCountryInput.setText("");
        layoutHintsPanel.setVisibility(View.GONE);
        updateLevelIndicator();
        updateGuessCounter();
        updateHintButton();
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void saveGuessHistory() {
        int unlockedLevel = progressManager.loadCurrentLevel();
        if (progressManager.isLevelCompleted(currentLevel) && currentLevel < unlockedLevel) return;

        List<String> isoList = new ArrayList<>();
        for (String name : guessedCountryNames) {
            Country c = repository.findCountryByName(name);
            if (c != null) isoList.add(c.getIsoCode());
        }
        progressManager.saveGuessHistory(isoList, targetCountry.getIsoCode());
        progressManager.saveCurrentLevel(currentLevel);
    }

    private void saveHintState() {
        int unlockedLevel = progressManager.loadCurrentLevel();
        if (progressManager.isLevelCompleted(currentLevel) && currentLevel < unlockedLevel) return;
        progressManager.saveHintState(hintManager.toJson(), targetCountry.getIsoCode());
    }

    private void restoreGuessHistory() {
        List<String> savedIsos = progressManager.loadGuessHistory(targetCountry.getIsoCode());
        if (savedIsos.isEmpty()) return;

        for (String iso : savedIsos) {
            Country guessed = findCountryByIso(iso);
            if (guessed == null) continue;
            if (guessedNamesLower.contains(guessed.getCountry().toLowerCase())) continue;

            guessedCountryNames.add(guessed.getCountry());
            guessedNamesLower.add(guessed.getCountry().toLowerCase());
            CountryGuess guess = CountryComparator.compare(guessed, targetCountry);
            guessAdapter.addGuess(guess);
            hintManager.onGuessMade(guess);
        }

        if (!guessedCountryNames.isEmpty()) rvGuessList.scrollToPosition(0);

        if (!isReplayMode && guessedCountryNames.size() >= MAX_GUESSES && !levelCompleted) {
            setInputEnabled(false);
        }
    }

    private void restoreHintState() {
        int unlockedLevel = progressManager.loadCurrentLevel();
        if (progressManager.isLevelCompleted(currentLevel) && currentLevel < unlockedLevel) return;
        hintManager.fromJson(progressManager.loadHintState(targetCountry.getIsoCode()));
    }

    private Country findCountryByIso(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        for (Country c : repository.getAllCountries()) {
            if (c.getIsoCode().equalsIgnoreCase(iso)) return c;
        }
        return null;
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
            RenderScript rs   = RenderScript.create(this);
            Allocation   in   = Allocation.createFromBitmap(rs, blurred);
            Allocation   out  = Allocation.createTyped(rs, in.getType());
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
