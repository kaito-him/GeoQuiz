package com.example.geoquiz;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
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
    /** Hint button — opens the hint-shop modal dialog. */
    private TextView             btnHint;
    private RecyclerView         rvGuessList;
    private ImageButton          btnBack;

    /** The currently open hint-shop dialog, or null if not showing. */
    private android.app.Dialog   hintDialog = null;

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
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageManager.applyLanguage(base));
    }

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
            Toast.makeText(this, getString(R.string.error_invalid_level), Toast.LENGTH_SHORT).show();
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
        rvGuessList       = findViewById(R.id.rvGuessList);

        btnBack.setOnClickListener(v -> finish());

        // Hint button opens the custom modal dialog
        btnHint.setOnClickListener(v -> {
            if (btnHint.isEnabled()) showHintShopDialog();
        });

        // IME "search" action still submits the current text
        etCountryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                submitCurrentInput();
            }
            return true;
        });
    }

    private void setupAutocomplete() {
        List<String> displayNames   = new ArrayList<>();
        List<String> canonicalNames = new ArrayList<>();
        boolean arabic = LanguageManager.isArabic(this);
        for (Country c : repository.getAllCountries()) {
            // canonicalNames always stores the English name used for guessed-lookup
            canonicalNames.add(c.getCountry());
            // displayNames shows Arabic when the UI language is Arabic
            String display = (arabic && c.getCountryAr() != null && !c.getCountryAr().isEmpty())
                    ? c.getCountryAr() : c.getCountry();
            displayNames.add(display);
        }

        CountrySuggestionAdapter adapter =
                new CountrySuggestionAdapter(this, displayNames, canonicalNames, guessedNamesLower);
        etCountryInput.setAdapter(adapter);
        etCountryInput.setThreshold(1);

        etCountryInput.setOnItemClickListener((parent, view, position, id) -> {
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
    // Hints modal dialog
    // -------------------------------------------------------------------------

    /**
     * Build and show the hint-shop as a centred square modal with a blurry
     * dimmed background over the game screen.
     *
     * Layout:  the card sits in the vertical centre of the screen with
     * automatic top/bottom space supplied by WRAP_CONTENT + Gravity.CENTER.
     * Horizontal margins keep it from touching the screen edges.
     */
    private void showHintShopDialog() {
        int guessCount = guessedCountryNames.size();

        // Inflate the custom dialog layout
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_hint_shop, null);

        // Wire up coin balance (number only — coin.png image is in the layout)
        TextView tvCoinBalance = dialogView.findViewById(R.id.tvDialogCoinBalance);
        tvCoinBalance.setText(String.valueOf(coinManager.getCoins()));

        // Populate hint rows
        LinearLayout hintRows = dialogView.findViewById(R.id.layoutDialogHintRows);
        hintRows.removeAllViews();
        for (HintType type : HintType.values()) {
            addHintRow(type, guessCount, hintRows);
        }

        // Build the Dialog with a transparent window
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            // Keep window background transparent — card drawable handles its own styling.
            // Setting any drawable here (including a blurred screenshot) causes the window
            // to expand to full screen height, which we explicitly want to avoid.
            window.setBackgroundDrawableResource(android.R.color.transparent);

            // Centre the card on screen; width = 88% of screen so there are side margins.
            // Height is WRAP_CONTENT so the window shrinks to exactly the card's content.
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int cardWidth   = (int) (screenWidth * 0.88f);
            window.setLayout(cardWidth, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);

            // Dim the area behind the card (55% dark scrim)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.6f;
            window.setAttributes(lp);

            // Fade + subtle scale-in animation
            dialogView.setAlpha(0f);
            dialogView.setScaleX(0.92f);
            dialogView.setScaleY(0.92f);
            dialogView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        // Close button inside the dialog
        dialogView.findViewById(R.id.tvDialogClose)
                .setOnClickListener(v -> dialog.dismiss());

        hintDialog = dialog;
        dialog.setOnDismissListener(d -> hintDialog = null);
        dialog.show();
    }

    /**
     * Dismiss the hint dialog and immediately re-open it with fresh data.
     * Called after a successful hint purchase so the row turns green.
     */
    private void refreshHintDialog() {
        if (hintDialog != null) {
            hintDialog.dismiss();
        }
        showHintShopDialog();
    }

    private void addHintRow(HintType type, int guessCount, LinearLayout container) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View row = inflater.inflate(R.layout.item_hint_row, container, false);

        TextView tvLabel      = row.findViewById(R.id.tvHintLabel);
        TextView tvSubtext    = row.findViewById(R.id.tvHintSubtext);
        TextView tvPrice      = row.findViewById(R.id.tvHintPrice);
        View     priceBadge   = row.findViewById(R.id.layoutHintPriceBadge);
        View     coinIcon     = row.findViewById(R.id.ivHintCoinIcon);
        View     rowRoot      = row.findViewById(R.id.hintRowRoot);

        tvLabel.setText(type.getLabel(this));

        // Case 1: already purchased — green row, show revealed text, hide price badge
        HintManager.PurchasedHint ph = findPurchasedHintByType(type);
        if (ph != null) {
            rowRoot.setBackgroundResource(R.drawable.tile_green);
            tvLabel.setTextColor(Color.WHITE);
            tvSubtext.setVisibility(View.VISIBLE);
            tvSubtext.setText(ph.revealedText);
            tvSubtext.setTextColor(Color.WHITE);
            priceBadge.setVisibility(View.GONE);
            tvPrice.setText(getString(R.string.hint_slot_used_format, ph.slotIndex));
            tvPrice.setTextColor(Color.WHITE);
            tvPrice.setVisibility(View.VISIBLE);
            if (coinIcon != null) coinIcon.setVisibility(View.GONE);
            rowRoot.setClickable(false);
            rowRoot.setFocusable(false);
            container.addView(row);
            return;
        }

        // Case 2: evaluate eligibility
        HintManager.EligibilityStatus status = hintManager.checkEligibility(type, guessCount);

        switch (status) {
            case AVAILABLE:
                rowRoot.setBackgroundResource(R.drawable.button_background);
                tvLabel.setTextColor(Color.parseColor("#3E2723"));
                tvSubtext.setVisibility(View.GONE);
                tvPrice.setText(String.valueOf(type.price));
                tvPrice.setTextColor(Color.parseColor("#3E2723"));
                priceBadge.setBackgroundResource(R.drawable.coin_background);
                priceBadge.setVisibility(View.VISIBLE);
                rowRoot.setClickable(true);
                rowRoot.setFocusable(true);
                rowRoot.setOnClickListener(v -> onHintRowClicked(type, guessCount));
                break;

            case ALREADY_KNOWN:
                styleDisabledRow(rowRoot, tvLabel, tvSubtext, tvPrice, priceBadge,
                        getString(R.string.hint_already_known), type.price);
                break;

            case SLOT_NOT_UNLOCKED: {
                int threshold = !hintManager.isSlot1Used()
                        ? HintManager.SLOT1_UNLOCK_AFTER
                        : HintManager.SLOT2_UNLOCK_AFTER;
                styleDisabledRow(rowRoot, tvLabel, tvSubtext, tvPrice, priceBadge,
                        getString(R.string.hint_slot_not_unlocked_format, threshold), type.price);
                break;
            }

            case NO_SLOTS_REMAINING:
            case SLOT_USED:
                styleDisabledRow(rowRoot, tvLabel, tvSubtext, tvPrice, priceBadge,
                        getString(R.string.hint_no_slots), type.price);
                break;
        }

        container.addView(row);
    }

    private void styleDisabledRow(View rowRoot, TextView label, TextView subtext,
                                  TextView price, View priceBadge,
                                  String reason, int hintPrice) {
        rowRoot.setBackgroundResource(R.drawable.tile_grey);
        label.setTextColor(Color.WHITE);
        subtext.setVisibility(View.VISIBLE);
        subtext.setText(reason);
        subtext.setTextColor(Color.parseColor("#BDBDBD"));
        price.setText(String.valueOf(hintPrice));
        price.setTextColor(Color.WHITE);
        if (priceBadge != null) priceBadge.setBackgroundResource(R.drawable.tile_grey);
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
            Toast.makeText(this, getString(R.string.error_hint_not_available), Toast.LENGTH_SHORT).show();
            refreshHintDialog();
            return;
        }

        int nextSlot = hintManager.nextAvailableSlot(guessCount);
        if (nextSlot == -1) {
            Toast.makeText(this, getString(R.string.error_no_slots_now), Toast.LENGTH_SHORT).show();
            return;
        }

        int balance = coinManager.getCoins();
        if (balance < type.price) {
            showInsufficientCoinsDialog(type.price, balance);
            return;
        }

        String revealText = hintManager.generateRevealText(type, this);
        if (revealText == null || revealText.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_hint_unavailable), Toast.LENGTH_SHORT).show();
            refreshHintDialog();
            return;
        }

        showBuyHintConfirmDialog(type, guessCount, nextSlot, balance, revealText);
    }

    /** Themed "not enough coins" dialog. */
    private void showInsufficientCoinsDialog(int price, int balance) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_buy_hint, null);

        ((TextView) v.findViewById(R.id.tvBuyTitle)).setText(getString(R.string.not_enough_coins));
        ((TextView) v.findViewById(R.id.tvBuyHintName)).setText(
                getString(R.string.hint_needs_coins_format, price));
        ((TextView) v.findViewById(R.id.tvBuyPrice)).setText(String.valueOf(price));
        ((TextView) v.findViewById(R.id.tvBuyBalance)).setText(String.valueOf(balance));
        ((TextView) v.findViewById(R.id.tvBuyAfter)).setText("—");
        ((TextView) v.findViewById(R.id.tvBuySlotNote)).setText(getString(R.string.earn_more_coins));
        v.findViewById(R.id.tvBuyConfirm).setVisibility(View.GONE);

        android.app.Dialog d = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(v);
        d.setCanceledOnTouchOutside(true);
        styleConfirmWindow(d);

        ((TextView) v.findViewById(R.id.tvBuyCancel)).setText(getString(R.string.buy_hint_close));
        v.findViewById(R.id.tvBuyCancel).setOnClickListener(x -> d.dismiss());
        d.show();
    }

    /** Themed buy-confirmation dialog. */
    private void showBuyHintConfirmDialog(HintType type, int guessCount,
                                          int nextSlot, int balance, String revealText) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_buy_hint, null);

        ((TextView) v.findViewById(R.id.tvBuyHintName)).setText(type.getLabel(this));
        ((TextView) v.findViewById(R.id.tvBuyPrice)).setText(String.valueOf(type.price));
        ((TextView) v.findViewById(R.id.tvBuyBalance)).setText(String.valueOf(balance));
        ((TextView) v.findViewById(R.id.tvBuyAfter)).setText(String.valueOf(balance - type.price));
        ((TextView) v.findViewById(R.id.tvBuySlotNote)).setText(
                getString(R.string.hint_will_use_slot_format, nextSlot));

        android.app.Dialog d = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(v);
        d.setCanceledOnTouchOutside(true);
        styleConfirmWindow(d);

        ((TextView) v.findViewById(R.id.tvBuyCancel)).setText(getString(R.string.buy_hint_cancel));
        ((TextView) v.findViewById(R.id.tvBuyConfirm)).setText(getString(R.string.buy_hint_buy));

        v.findViewById(R.id.tvBuyCancel).setOnClickListener(x -> d.dismiss());
        v.findViewById(R.id.tvBuyConfirm).setOnClickListener(x -> {
            d.dismiss();
            HintManager.EligibilityStatus reCheck =
                    hintManager.checkEligibility(type, guessedCountryNames.size());
            if (reCheck != HintManager.EligibilityStatus.AVAILABLE) {
                Toast.makeText(this, getString(R.string.error_hint_no_longer), Toast.LENGTH_SHORT).show();
                refreshHintDialog();
                return;
            }
            int slot = hintManager.nextAvailableSlot(guessedCountryNames.size());
            if (slot == -1) {
                Toast.makeText(this, getString(R.string.error_no_slot), Toast.LENGTH_SHORT).show();
                return;
            }
            boolean deducted = coinManager.deductCoins(type.price);
            if (!deducted) {
                Toast.makeText(this, getString(R.string.error_insufficient_coins), Toast.LENGTH_SHORT).show();
                return;
            }
            hintManager.recordHintPurchase(type, revealText, slot);
            saveHintState();

            if (hintDialog != null) {
                hintDialog.dismiss();
                hintDialog = null;
            }

            HintManager.PurchasedHint purchased = hintManager.getPurchasedHint(slot);
            if (purchased != null) {
                rvGuessList.scrollToPosition(0);
                guessAdapter.addHintReveal(purchased, rvGuessList);
            }
            updateHintButton();
        });
        d.show();
    }

    /** Apply the same centred-modal window params used by the hint shop. */
    private void styleConfirmWindow(android.app.Dialog d) {
        Window window = d.getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        int cardWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
        window.setLayout(cardWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.CENTER);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.dimAmount = 0.6f;
        window.setAttributes(lp);
        // Fade + scale-in
        View content = window.getDecorView().findViewById(android.R.id.content);
        if (content != null) {
            content.setAlpha(0f);
            content.setScaleX(0.92f);
            content.setScaleY(0.92f);
            content.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
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
            // Dismiss the hint dialog if it's open
            if (hintDialog != null) {
                hintDialog.dismiss();
                hintDialog = null;
            }
        }
    }

    /** Submit whatever is currently typed in the autocomplete field. */
    private void submitCurrentInput() {
        // Guard: level already completed or input already disabled
        if (levelCompleted) return;
        
        // Guard: already at max guesses (check BEFORE adding new guess)
        if (!isReplayMode && guessedCountryNames.size() >= MAX_GUESSES) {
            Toast.makeText(this, R.string.error_max_guesses_reached, Toast.LENGTH_SHORT).show();
            return;
        }

        String input = etCountryInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_input, Toast.LENGTH_SHORT).show();
            return;
        }

        // Resolve typed name → Country (supports both English and Arabic names)
        Country guessedCountry = repository.findCountryByName(input);
        if (guessedCountry == null && LanguageManager.isArabic(this)) {
            guessedCountry = repository.findCountryByNameAr(input);
        }

        // Normalise the display name to the English one for internal tracking
        String trackName = (guessedCountry != null) ? guessedCountry.getCountry() : input;

        if (guessedNamesLower.contains(trackName.toLowerCase())) {
            Toast.makeText(this, R.string.error_already_guessed, Toast.LENGTH_SHORT).show();
            etCountryInput.setText("");
            return;
        }
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

        if (hintDialog != null) {
            hintDialog.dismiss();
            hintDialog = null;
        }
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
            new Handler(getMainLooper()).postDelayed(
                    () -> showSuccessDialog(finalCoinsAwarded), 2000);

        } else if (!isReplayMode && guessedCountryNames.size() >= MAX_GUESSES) {
            // Reached max guesses without finding the answer
            setInputEnabled(false);
            levelCompleted = true; // Mark as completed to prevent any further guesses
            rvGuessList.post(this::showFailureDialog);
        }
    }

    private void updateLevelIndicator() {
        tvLevelIndicator.setText(getString(R.string.guess_counter_format,
                currentLevel, repository.getTotalCountries()));
    }

    private void updateGuessCounter() {
        int used = guessedCountryNames.size();
        tvGuessCounter.setText(getString(R.string.guess_counter_format, used, MAX_GUESSES));
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
        // Guard: activity might have been finished before the delayed call fires
        if (isFinishing() || isDestroyed()) return;

        int totalLevels = repository.getTotalCountries();
        boolean lastLevel = (currentLevel >= totalLevels);

        // ---- Inflate the custom layout ----
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_success, null);

        TextView tvTitle     = dialogView.findViewById(R.id.tvSuccessTitle);
        TextView tvFoundIt   = dialogView.findViewById(R.id.tvSuccessFoundIt);
        TextView tvCountry   = dialogView.findViewById(R.id.tvSuccessCountry);
        TextView tvGuesses   = dialogView.findViewById(R.id.tvSuccessGuesses);
        LinearLayout layoutCoins   = dialogView.findViewById(R.id.layoutCoinsRow);
        TextView tvCoinsPrefix     = dialogView.findViewById(R.id.tvSuccessCoinsPrefix);
        TextView tvCoinsSuffix     = dialogView.findViewById(R.id.tvSuccessCoinsSuffix);
        TextView tvBreakdown       = dialogView.findViewById(R.id.tvSuccessCoinsBreakdown);
        Button   btnStayHere       = dialogView.findViewById(R.id.btnStayHere);
        Button   btnNextLevel      = dialogView.findViewById(R.id.btnNextLevel);

        // ---- "All levels done" title variant ----
        if (lastLevel && !isReplayMode) {
            tvTitle.setText(getString(R.string.success_all_done_title));
            tvFoundIt.setText(getString(R.string.success_all_done_format, totalLevels));
        } else {
            tvTitle.setText(getString(R.string.success_title));
            tvFoundIt.setText(getString(R.string.success_found_it));
        }

        // Use localized country name if Arabic
        String displayName = (LanguageManager.isArabic(this)
                && targetCountry.getCountryAr() != null
                && !targetCountry.getCountryAr().isEmpty())
                ? targetCountry.getCountryAr() : targetCountry.getCountry();
        tvCountry.setText(getString(R.string.success_country_format, displayName));
        tvGuesses.setText(getString(R.string.success_guesses_format, guessedCountryNames.size()));

        // ---- Coins row ----
        if (isReplayMode || coinsAwarded == 0) {
            layoutCoins.setVisibility(View.GONE);
            tvBreakdown.setVisibility(View.GONE);
        } else {
            tvCoinsPrefix.setText("+" + coinsAwarded);
            tvCoinsSuffix.setText(getString(R.string.success_coins_suffix));

            int hintsUsed = (hintManager.isSlot1Used() ? 1 : 0)
                          + (hintManager.isSlot2Used() ? 1 : 0);
            if (hintsUsed > 0) {
                int penalty = RewardCalculator.HINT_PENALTY * hintsUsed;
                tvBreakdown.setText(getString(R.string.success_base_penalty_format,
                        RewardCalculator.BASE_REWARD, penalty));
                tvBreakdown.setVisibility(View.VISIBLE);
            } else {
                int bonus = RewardCalculator.performanceBonus(guessedCountryNames.size());
                if (bonus > 0) {
                    tvBreakdown.setText(getString(R.string.success_base_bonus_format,
                            RewardCalculator.BASE_REWARD, bonus));
                    tvBreakdown.setVisibility(View.VISIBLE);
                }
            }
        }

        // ---- Buttons ----
        android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(false);

        if (isReplayMode || lastLevel) {
            btnStayHere.setVisibility(View.GONE);
            btnNextLevel.setText(getString(R.string.btn_finish));
            btnNextLevel.setOnClickListener(v -> { dialog.dismiss(); finish(); });
        } else {
            btnStayHere.setVisibility(View.VISIBLE);
            btnNextLevel.setText(getString(R.string.btn_next_level));
            btnNextLevel.setOnClickListener(v -> { dialog.dismiss(); loadNextLevel(); });
            btnStayHere.setText(getString(R.string.btn_stay_here));
            btnStayHere.setOnClickListener(v -> dialog.dismiss());
        }

        // ---- Window styling — same as hint-shop dialog ----
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            window.setLayout((int) (screenWidth * 0.88f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);

            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.6f;
            window.setAttributes(lp);
        }

        // ---- Entrance animation — same as hint-shop dialog ----
        dialogView.setAlpha(0f);
        dialogView.setScaleX(0.92f);
        dialogView.setScaleY(0.92f);
        dialogView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        dialog.show();
    }

    private void showFailureDialog() {
        // Guard: activity might have been finished before the call fires
        if (isFinishing() || isDestroyed()) return;

        String displayName = (LanguageManager.isArabic(this)
                && targetCountry.getCountryAr() != null
                && !targetCountry.getCountryAr().isEmpty())
                ? targetCountry.getCountryAr() : targetCountry.getCountry();

        // ---- Inflate the custom layout ----
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_game_over, null);

        TextView tvTitle     = dialogView.findViewById(R.id.tvGameOverTitle);
        TextView tvMessage   = dialogView.findViewById(R.id.tvGameOverMessage);
        TextView tvCountry   = dialogView.findViewById(R.id.tvGameOverCountry);
        TextView btnClose    = dialogView.findViewById(R.id.btnGameOverClose);
        Button   btnTryAgain = dialogView.findViewById(R.id.btnTryAgain);
        Button   btnGiveUp   = dialogView.findViewById(R.id.btnGiveUp);

        // Set content
        tvTitle.setText(getString(R.string.game_over_title));
        tvMessage.setText(getString(R.string.game_over_message));
        tvCountry.setText(getString(R.string.game_over_country_format, displayName));

        // ---- Build the Dialog with a transparent window ----
        android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.setCancelable(false);

        // ---- Window styling — same as success dialog ----
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            window.setLayout((int) (screenWidth * 0.88f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);

            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.6f;
            window.setAttributes(lp);
        }

        // ---- Entrance animation — same as success dialog ----
        dialogView.setAlpha(0f);
        dialogView.setScaleX(0.92f);
        dialogView.setScaleY(0.92f);
        dialogView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();

        // ---- Button actions ----
        btnTryAgain.setOnClickListener(v -> {
            dialog.dismiss();
            resetLevel();
        });

        btnGiveUp.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        btnClose.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();
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

        if (hintDialog != null) { hintDialog.dismiss(); hintDialog = null; }
        etCountryInput.setEnabled(true);
        etCountryInput.setText("");
        updateGuessCounter();
        updateHintButton();
    }

    private void loadNextLevel() {
        int nextLevel = currentLevel + 1;
        if (nextLevel > repository.getTotalCountries()) {
            Toast.makeText(this, getString(R.string.error_no_more_levels), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Country nextTarget = repository.getCountryByLevel(nextLevel);
        if (nextTarget == null) {
            Toast.makeText(this, getString(R.string.error_loading_level), Toast.LENGTH_SHORT).show();
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

        if (hintDialog != null) { hintDialog.dismiss(); hintDialog = null; }
        etCountryInput.setEnabled(true);
        etCountryInput.setText("");
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

        // Re-insert hint reveal cards into the adapter for any already-purchased hints.
        // Hints are shown below the guess that triggered the purchase, but since we can't
        // know the original insertion order vs. guesses we add them after guess restoration
        // (restoreGuessHistory is called first). They appear at the top, newest first.
        for (int slot = 1; slot <= 2; slot++) {
            HintManager.PurchasedHint ph = hintManager.getPurchasedHint(slot);
            if (ph != null) {
                guessAdapter.addHintReveal(ph);
            }
        }
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
