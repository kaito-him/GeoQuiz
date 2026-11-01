package com.example.geoquiz;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Main gameplay screen for Guess by the Map.
 *
 * ── Attempt state machine ───────────────────────────────────────────────────
 *   READY   → awaiting first input (stopwatch already running)
 *   PLAYING → guesses being accepted, stopwatch running
 *   WON     → correct guess; stopwatch stopped
 *   LOST    → 10 wrong guesses used; stopwatch stopped
 *
 * ── Stopwatch ────────────────────────────────────────────────────────────────
 *   Handler + Runnable posting every second.
 *   Monotonic base: SystemClock.elapsedRealtime().
 *   Survives rotation via onSaveInstanceState.
 *
 * ── 10-guess limit ───────────────────────────────────────────────────────────
 *   Only valid, unique, resolved guesses increment guessCount.
 *   submissionInProgress blocks re-entry during the 900 ms feedback animation.
 *
 * ── Hint system ──────────────────────────────────────────────────────────────
 *   Slot 1 unlocks after guess 4.  Slot 2 unlocks after guess 8.
 *   Each slot may be used at most once (max 2 hints per attempt).
 *   If the attempt ends (won/lost) neither slot can be used.
 *   The hint button opens a selection panel with all four MapHintType options.
 *
 * ── Coin rewards ─────────────────────────────────────────────────────────────
 *   First-success-only, guarded by MapProgressManager.hasRewardBeenIssued().
 *   Formula: base + speedBonus (see MapRewardCalculator).
 *   Hint purchases deduct coins immediately via CoinManager.
 */
public class GuessByMapActivity extends AppCompatActivity {

    // ── Intent extras ─────────────────────────────────────────────────────
    public static final String EXTRA_DIFFICULTY = MapLevelSelectActivity.EXTRA_DIFFICULTY;
    public static final String EXTRA_LEVEL      = MapLevelSelectActivity.EXTRA_LEVEL;

    // ── Attempt state ─────────────────────────────────────────────────────
    private enum AttemptState { READY, PLAYING, WON, LOST }

    // ── Hint slot thresholds ──────────────────────────────────────────────
    private static final int HINT_SLOT1_AFTER = 4;   // available once guessCount >= 4
    private static final int HINT_SLOT2_AFTER = 8;   // available once guessCount >= 8

    // ── Constants ─────────────────────────────────────────────────────────
    private static final int  MAX_GUESSES         = 10;
    private static final long WRONG_FEEDBACK_MS   = 900L;
    private static final long OVERLAY_DURATION_MS = 700L;
    private static final long STOPWATCH_TICK_MS   = 1_000L;

    // ── savedInstanceState keys ───────────────────────────────────────────
    private static final String KEY_STATE         = "gbm_state";
    private static final String KEY_GUESS_COUNT   = "gbm_guess_count";
    private static final String KEY_GUESSED_SET   = "gbm_guessed_set";
    private static final String KEY_SW_ELAPSED    = "gbm_sw_elapsed";
    private static final String KEY_SW_RUNNING    = "gbm_sw_running";
    private static final String KEY_SLOT1_USED    = "gbm_slot1_used";
    private static final String KEY_SLOT2_USED    = "gbm_slot2_used";
    private static final String KEY_HINT1_KEY     = "gbm_hint1_key";
    private static final String KEY_HINT1_TEXT    = "gbm_hint1_text";
    private static final String KEY_HINT2_KEY     = "gbm_hint2_key";
    private static final String KEY_HINT2_TEXT    = "gbm_hint2_text";
    private static final String KEY_REWARD_GIVEN  = "gbm_reward_given";

    // ── Views ─────────────────────────────────────────────────────────────
    private ImageView            ivCountryMap;
    private TextView             tvMapFallback;
    private View                 frameMapCard;
    private View                 viewWrongOverlay;
    private TextView             tvLevelLabel;
    private TextView             tvTimer;
    private TextView             tvCoinBalance;
    private TextView             tvAttemptCounter;
    private TextView             tvGuessFeedback;
    private TextView             tvHintStatus;
    private AutoCompleteTextView etCountryInput;
    private TextView             btnHint;
    private ImageButton          btnBack;
    private LinearLayout         layoutGuessList;
    private ScrollView           scrollGuesses;
    private CountrySuggestionAdapter suggestionAdapter;

    // ── Domain objects ────────────────────────────────────────────────────
    private CountryRepository  repo;
    private MapProgressManager progressManager;
    private CoinManager        coinManager;
    private MapDifficulty      difficulty;
    private int                levelWithinDifficulty;
    private Country            targetCountry;
    private List<Country>      difficultyCountries;

    // ── Attempt state ─────────────────────────────────────────────────────
    private AttemptState      attemptState        = AttemptState.READY;
    private int               guessCount          = 0;
    private final Set<String> guessedNamesLower   = new HashSet<>();
    private final List<Country> guessedCountries  = new ArrayList<>();
    private boolean           submissionInProgress = false;

    // ── Hint state ────────────────────────────────────────────────────────
    /** Whether slot 1 has already been consumed in this attempt. */
    private boolean      slot1Used     = false;
    /** Whether slot 2 has already been consumed in this attempt. */
    private boolean      slot2Used     = false;
    /** Type used in slot 1 (null = not yet used). */
    private MapHintType  hint1Type     = null;
    /** Text revealed by slot 1. */
    private String       hint1Text     = null;
    /** Type used in slot 2 (null = not yet used). */
    private MapHintType  hint2Type     = null;
    /** Text revealed by slot 2. */
    private String       hint2Text     = null;

    // ── Reward state ──────────────────────────────────────────────────────
    /** True once coins have been credited this attempt (prevents double-grant on rotation). */
    private boolean rewardGivenThisAttempt = false;

    // ── Stopwatch ─────────────────────────────────────────────────────────
    private final Handler stopwatchHandler  = new Handler(Looper.getMainLooper());
    private Runnable      stopwatchRunnable;
    private long          swBaseMs          = 0L;
    private long          swElapsedAtPause  = 0L;
    private boolean       swRunning         = false;

    // ── Open dialogs ──────────────────────────────────────────────────────
    private android.app.Dialog resultDialog;
    private android.app.Dialog hintDialog;

    // =========================================================================
    // Locale
    // =========================================================================

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageManager.applyLanguage(base));
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guess_by_map);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        // ── Intent extras ─────────────────────────────────────────────────
        int diffOrdinal = getIntent().getIntExtra(EXTRA_DIFFICULTY, -1);
        levelWithinDifficulty = getIntent().getIntExtra(EXTRA_LEVEL, -1);

        if (diffOrdinal < 0 || diffOrdinal >= MapDifficulty.values().length
                || levelWithinDifficulty < 1) {
            Toast.makeText(this, R.string.error_invalid_level, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        difficulty = MapDifficulty.values()[diffOrdinal];

        // ── Repository ────────────────────────────────────────────────────
        repo = CountryRepository.getInstance();
        if (!repo.loadCountries(this)) {
            Toast.makeText(this, R.string.error_load_countries, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        difficultyCountries = getCountriesForDifficulty(difficulty);
        if (levelWithinDifficulty > difficultyCountries.size()) {
            Toast.makeText(this, R.string.error_invalid_level, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        targetCountry = difficultyCountries.get(levelWithinDifficulty - 1);

        // ── Support objects ───────────────────────────────────────────────
        progressManager = new MapProgressManager(this);
        coinManager     = new CoinManager(this);

        // ── Views ─────────────────────────────────────────────────────────
        initViews();
        blurBackground(findViewById(R.id.backgroundImage));

        // ── Restore or initialise ─────────────────────────────────────────
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        }

        // ── Populate UI ───────────────────────────────────────────────────
        updateCoinBalance();
        updateLevelLabel();
        loadMapImage();
        setupAutocomplete();
        updateGuessCounter();
        updateTimerDisplay(currentElapsedMs());
        updateHintButton();
        rebuildHintFeedback();
        rebuildGuessList();

        // ── Start stopwatch ───────────────────────────────────────────────
        if (swRunning) {
            swBaseMs = SystemClock.elapsedRealtime() - swElapsedAtPause;
            startStopwatch();
        } else if (attemptState == AttemptState.READY || attemptState == AttemptState.PLAYING) {
            startStopwatch();
            attemptState = AttemptState.PLAYING;
        }

        if (attemptState == AttemptState.WON || attemptState == AttemptState.LOST) {
            etCountryInput.setEnabled(false);
            btnHint.setEnabled(false);
            btnHint.setAlpha(0.4f);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCoinBalance();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        long elapsed = currentElapsedMs();
        out.putString(KEY_STATE,        attemptState.name());
        out.putInt(KEY_GUESS_COUNT,     guessCount);
        out.putString(KEY_GUESSED_SET,  String.join(",", guessedNamesLower));
        out.putLong(KEY_SW_ELAPSED,     elapsed);
        out.putBoolean(KEY_SW_RUNNING,  swRunning);
        out.putBoolean(KEY_SLOT1_USED,  slot1Used);
        out.putBoolean(KEY_SLOT2_USED,  slot2Used);
        out.putString(KEY_HINT1_KEY,    hint1Type != null ? hint1Type.key : null);
        out.putString(KEY_HINT1_TEXT,   hint1Text);
        out.putString(KEY_HINT2_KEY,    hint2Type != null ? hint2Type.key : null);
        out.putString(KEY_HINT2_TEXT,   hint2Text);
        out.putBoolean(KEY_REWARD_GIVEN, rewardGivenThisAttempt);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStopwatch();
        dismissOpenDialogs();
    }

    // =========================================================================
    // State restore
    // =========================================================================

    private void restoreInstanceState(Bundle in) {
        try { attemptState = AttemptState.valueOf(in.getString(KEY_STATE, "PLAYING")); }
        catch (IllegalArgumentException e) { attemptState = AttemptState.PLAYING; }

        guessCount = in.getInt(KEY_GUESS_COUNT, 0);

        String joined = in.getString(KEY_GUESSED_SET, "");
        if (joined != null && !joined.isEmpty()) {
            for (String n : joined.split(",")) {
                if (!n.isEmpty()) {
                    guessedNamesLower.add(n);
                    // Rebuild ordered list for the UI
                    Country c = repo.findCountryByName(n);
                    if (c == null) {
                        // try a case-insensitive scan
                        for (Country all : repo.getAllCountries()) {
                            if (all.getCountry().toLowerCase().equals(n)) { c = all; break; }
                        }
                    }
                    if (c != null) guessedCountries.add(c);
                }
            }
        }

        swElapsedAtPause = in.getLong(KEY_SW_ELAPSED, 0L);
        swRunning = in.getBoolean(KEY_SW_RUNNING, false)
                && (attemptState == AttemptState.PLAYING || attemptState == AttemptState.READY);

        slot1Used = in.getBoolean(KEY_SLOT1_USED, false);
        slot2Used = in.getBoolean(KEY_SLOT2_USED, false);
        hint1Type = MapHintType.fromKey(in.getString(KEY_HINT1_KEY, null));
        hint1Text = in.getString(KEY_HINT1_TEXT, null);
        hint2Type = MapHintType.fromKey(in.getString(KEY_HINT2_KEY, null));
        hint2Text = in.getString(KEY_HINT2_TEXT, null);
        rewardGivenThisAttempt = in.getBoolean(KEY_REWARD_GIVEN, false);
    }

    // =========================================================================
    // View initialisation
    // =========================================================================

    private void initViews() {
        ivCountryMap     = findViewById(R.id.ivCountryMap);
        tvMapFallback    = findViewById(R.id.tvMapFallback);
        frameMapCard     = findViewById(R.id.frameMapCard);
        viewWrongOverlay = findViewById(R.id.viewWrongOverlay);
        tvLevelLabel     = findViewById(R.id.tvLevelLabel);
        tvTimer          = findViewById(R.id.tvTimer);
        tvCoinBalance    = findViewById(R.id.tvCoinBalance);
        tvAttemptCounter = findViewById(R.id.tvAttemptCounter);
        tvGuessFeedback  = findViewById(R.id.tvGuessFeedback);
        tvHintStatus     = findViewById(R.id.tvHintStatus);
        etCountryInput   = findViewById(R.id.etCountryInput);
        btnHint          = findViewById(R.id.btnHint);
        btnBack          = findViewById(R.id.btnBack);
        layoutGuessList  = findViewById(R.id.layoutGuessList);
        scrollGuesses    = findViewById(R.id.scrollGuesses);

        btnBack.setOnClickListener(v -> finish());
        btnHint.setEnabled(false);
        btnHint.setAlpha(0.4f);
        btnHint.setOnClickListener(v -> openHintPanel());

        etCountryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                submitCurrentInput();
            }
            return true;
        });
    }

    // =========================================================================
    // Map loading
    // =========================================================================

    private void loadMapImage() {
        String mapFile = (targetCountry != null) ? targetCountry.getMap() : null;
        if (mapFile == null || mapFile.trim().isEmpty()) { showMapFallback(); return; }
        String assetPath = "country_maps/" + mapFile.trim();
        try {
            InputStream is = getAssets().open(assetPath);
            Drawable d = Drawable.createFromStream(is, null);
            is.close();
            if (d == null) { showMapFallback(); return; }
            ivCountryMap.setImageDrawable(d);
            ivCountryMap.setVisibility(View.VISIBLE);
            tvMapFallback.setVisibility(View.GONE);

            // After layout, resize the wrong overlay to cover exactly the ImageView
            ivCountryMap.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            ivCountryMap.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            android.view.ViewGroup.LayoutParams lp = viewWrongOverlay.getLayoutParams();
                            lp.width  = ivCountryMap.getWidth();
                            lp.height = ivCountryMap.getHeight();
                            viewWrongOverlay.setLayoutParams(lp);
                        }
                    });
        } catch (IOException e) {
            android.util.Log.w("GuessByMap", "Map asset not found: " + assetPath, e);
            showMapFallback();
        }
    }

    private void showMapFallback() {
        ivCountryMap.setVisibility(View.INVISIBLE);
        tvMapFallback.setVisibility(View.VISIBLE);
    }

    // =========================================================================
    // Autocomplete
    // =========================================================================

    private void setupAutocomplete() {
        boolean arabic = LanguageManager.isArabic(this);
        List<String> displayNames   = new ArrayList<>();
        List<String> canonicalNames = new ArrayList<>();
        for (Country c : repo.getAllCountries()) {
            String display = (arabic && c.getCountryAr() != null && !c.getCountryAr().isEmpty())
                    ? c.getCountryAr() : c.getCountry();
            displayNames.add(display);
            canonicalNames.add(c.getCountry());
        }
        CountrySuggestionAdapter adapter = new CountrySuggestionAdapter(
                this, displayNames, canonicalNames, guessedNamesLower);
        suggestionAdapter = adapter;
        etCountryInput.setAdapter(adapter);
        etCountryInput.setThreshold(1);
        etCountryInput.setOnItemClickListener((parent, v, pos, id) -> {
            // Get the actual selected text and check against guessed set
            String selected = (String) parent.getItemAtPosition(pos);
            if (selected == null) return;

            // Resolve canonical English name for the lookup
            String canonical = selected.toLowerCase();
            // Also try resolving through the adapter's canonical list if in Arabic mode
            Country resolved = repo.findCountryByName(selected);
            if (resolved == null && LanguageManager.isArabic(GuessByMapActivity.this))
                resolved = repo.findCountryByNameAr(selected);
            if (resolved != null) canonical = resolved.getCountry().toLowerCase();

            if (guessedNamesLower.contains(canonical)) {
                // Already guessed — swallow the tap
                etCountryInput.setText("");
                etCountryInput.dismissDropDown();
                return;
            }
            etCountryInput.post(this::submitCurrentInput);
        });
    }

    // =========================================================================
    // Guess submission
    // =========================================================================

    private void submitCurrentInput() {
        if (attemptState == AttemptState.WON || attemptState == AttemptState.LOST) return;
        if (submissionInProgress) return;

        String input = etCountryInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_input, Toast.LENGTH_SHORT).show();
            return;
        }

        Country guessed = repo.findCountryByName(input);
        if (guessed == null && LanguageManager.isArabic(this))
            guessed = repo.findCountryByNameAr(input);

        String trackName = (guessed != null) ? guessed.getCountry().toLowerCase()
                                             : input.toLowerCase();

        if (guessedNamesLower.contains(trackName)) {
            Toast.makeText(this, R.string.error_already_guessed, Toast.LENGTH_SHORT).show();
            etCountryInput.setText("");
            return;
        }
        if (guessed == null) {
            Toast.makeText(this, R.string.error_invalid_country, Toast.LENGTH_SHORT).show();
            return;
        }

        // Record
        guessedNamesLower.add(guessed.getCountry().toLowerCase());
        guessCount++;
        if (suggestionAdapter != null) suggestionAdapter.notifyDataSetChanged();
        etCountryInput.setText("");
        etCountryInput.dismissDropDown();
        updateGuessCounter();
        updateHintButton();

        boolean correct = guessed.getCountry().equalsIgnoreCase(targetCountry.getCountry());
        if (!correct) {
            // Add to the visual guess list immediately (with animation)
            guessedCountries.add(0, guessed); // newest first
            addGuessRow(guessed, true);
        }

        if (correct) {
            concludeAttempt(true);
        } else if (guessCount >= MAX_GUESSES) {
            submissionInProgress = true;
            etCountryInput.setEnabled(false);
            playWrongFeedback(() -> concludeAttempt(false));
        } else {
            submissionInProgress = true;
            etCountryInput.setEnabled(false);
            playWrongFeedback(() -> {
                submissionInProgress = false;
                if (attemptState == AttemptState.PLAYING) etCountryInput.setEnabled(true);
            });
        }
    }

    // =========================================================================
    // Attempt conclusion
    // =========================================================================

    private void concludeAttempt(boolean won) {
        if (attemptState == AttemptState.WON || attemptState == AttemptState.LOST) return;

        attemptState = won ? AttemptState.WON : AttemptState.LOST;
        stopStopwatch();
        submissionInProgress = false;
        etCountryInput.setEnabled(false);
        btnHint.setEnabled(false);
        btnHint.setAlpha(0.4f);
        dismissOpenDialogs();

        long elapsedMs = currentElapsedMs();
        int  coinsAwarded = 0;

        if (won) {
            progressManager.markLevelCompleted(
                    difficulty, levelWithinDifficulty, difficultyCountries.size());

            // ── First-success-only reward ──────────────────────────────────
            if (!rewardGivenThisAttempt
                    && !progressManager.hasRewardBeenIssued(difficulty, levelWithinDifficulty)) {
                coinsAwarded = MapRewardCalculator.compute(difficulty, elapsedMs);
                coinManager.addCoins(coinsAwarded);
                progressManager.markRewardIssued(difficulty, levelWithinDifficulty);
                rewardGivenThisAttempt = true;
            }
            updateCoinBalance();
        } else {
            progressManager.markLevelFailed(
                    difficulty, levelWithinDifficulty, difficultyCountries.size());
        }

        final int finalCoins = coinsAwarded;
        final long finalElapsed = elapsedMs;
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> showResultDialog(won, finalElapsed, finalCoins),
                won ? 600L : 1200L);
    }

    // =========================================================================
    // Wrong-answer feedback
    // =========================================================================

    private void playWrongFeedback(Runnable onComplete) {
        vibrateWrong();
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        frameMapCard.startAnimation(shake);

        viewWrongOverlay.setAlpha(0f);
        viewWrongOverlay.setVisibility(View.VISIBLE);
        viewWrongOverlay.animate().alpha(1f).setDuration(150).withEndAction(() ->
            viewWrongOverlay.animate()
                .alpha(0f)
                .setStartDelay(OVERLAY_DURATION_MS - 300L)
                .setDuration(150)
                .withEndAction(() -> viewWrongOverlay.setVisibility(View.INVISIBLE))
                .start()).start();

        new Handler(Looper.getMainLooper()).postDelayed(onComplete, WRONG_FEEDBACK_MS);
    }

    private void vibrateWrong() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(50);
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Hint system
    // =========================================================================

    /**
     * Returns which slot (1 or 2) is currently open for purchase,
     * or 0 if no slot is available right now.
     *
     * Rules:
     *   - Slot 1 opens when guessCount >= HINT_SLOT1_AFTER and slot1 not yet used.
     *   - Slot 2 opens when guessCount >= HINT_SLOT2_AFTER and slot2 not yet used.
     *   - Slot 2 is independent of whether slot 1 was used.
     *   - If the attempt is over, nothing is open.
     */
    private int currentOpenSlot() {
        if (attemptState == AttemptState.WON || attemptState == AttemptState.LOST) return 0;
        if (guessCount >= HINT_SLOT2_AFTER && !slot2Used) return 2;
        if (guessCount >= HINT_SLOT1_AFTER && !slot1Used) return 1;
        return 0;
    }

    private boolean canUseHint() {
        return currentOpenSlot() > 0;
    }

    /** Update the hint button enabled/alpha state and the status label. */
    private void updateHintButton() {
        boolean active = canUseHint()
                && (attemptState == AttemptState.PLAYING || attemptState == AttemptState.READY);
        btnHint.setEnabled(active);
        btnHint.setAlpha(active ? 1f : 0.4f);
        if (tvHintStatus != null) tvHintStatus.setText(buildHintStatusText());
    }

    private String buildHintStatusText() {
        if (attemptState == AttemptState.WON || attemptState == AttemptState.LOST) {
            return "";
        }
        boolean bothUsed = slot1Used && slot2Used;
        if (bothUsed) return getString(R.string.map_hint_status_none_remaining);

        if (guessCount >= HINT_SLOT2_AFTER) {
            if (!slot2Used) return getString(R.string.map_hint_status_choose_second);
            // slot2 used, nothing more
            return getString(R.string.map_hint_status_none_remaining);
        }

        if (guessCount >= HINT_SLOT1_AFTER) {
            if (!slot1Used) return getString(R.string.map_hint_status_choose_first);
            // slot1 used, waiting for slot2
            return getString(R.string.map_hint_status_wait_for_8);
        }

        // Before slot1 threshold
        if (!slot1Used) return getString(R.string.map_hint_status_before_4);
        // slot1 was somehow used already (shouldn't happen but be safe)
        return getString(R.string.map_hint_status_wait_for_8);
    }

    /** Open the hint selection panel dialog. */
    private void openHintPanel() {
        if (!canUseHint()) return;
        if (isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_map_hint_select, null);

        TextView tvTitle   = dialogView.findViewById(R.id.tvHintPanelTitle);
        TextView tvBalance = dialogView.findViewById(R.id.tvHintPanelCoinBalance);
        TextView tvClose   = dialogView.findViewById(R.id.tvHintPanelClose);
        TextView tvStatus  = dialogView.findViewById(R.id.tvHintPanelStatus);
        LinearLayout container = dialogView.findViewById(R.id.layoutHintOptions);

        tvTitle.setText(R.string.map_hint_panel_title);
        tvBalance.setText(String.valueOf(coinManager.getCoins()));
        tvStatus.setText(buildHintStatusText());

        container.removeAllViews();
        for (MapHintType type : MapHintType.values()) {
            addHintOptionRow(type, container, tvBalance);
        }

        hintDialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        hintDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        hintDialog.setContentView(dialogView);
        hintDialog.setCanceledOnTouchOutside(true);

        tvClose.setOnClickListener(v -> hintDialog.dismiss());
        hintDialog.setOnDismissListener(d -> hintDialog = null);

        styleDialogWindow(hintDialog);
        entranceAnim(dialogView);
        hintDialog.show();
    }

    private void addHintOptionRow(MapHintType type, LinearLayout container, TextView tvBalance) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_map_hint_option, container, false);

        TextView tvLabel   = row.findViewById(R.id.tvHintOptionLabel);
        TextView tvDesc    = row.findViewById(R.id.tvHintOptionDesc);
        TextView tvPrice   = row.findViewById(R.id.tvHintOptionPrice);
        LinearLayout root  = row.findViewById(R.id.layoutHintOption);

        tvLabel.setText(type.getLabel(this));
        tvDesc.setText(type.getDesc(this));
        tvPrice.setText(String.valueOf(type.price));

        boolean canAfford = coinManager.getCoins() >= type.price;
        if (!canAfford) {
            root.setAlpha(0.5f);
            root.setClickable(false);
            root.setFocusable(false);
            tvDesc.setText(getString(R.string.map_hint_not_enough_coins));
            tvDesc.setTextColor(Color.parseColor("#D32F2F"));
        } else {
            root.setAlpha(1f);
            root.setClickable(true);
            root.setFocusable(true);
            root.setOnClickListener(v -> {
                if (hintDialog != null) hintDialog.dismiss();
                confirmHintPurchase(type, tvBalance);
            });
        }
        container.addView(row);
    }

    private void confirmHintPurchase(MapHintType type, TextView tvBalanceRef) {
        if (!canUseHint()) return;
        if (coinManager.getCoins() < type.price) {
            Toast.makeText(this, R.string.map_hint_not_enough_coins, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.map_hint_confirm_title)
                .setMessage(getString(R.string.map_hint_confirm_format,
                        type.price, type.getLabel(this)))
                .setPositiveButton(R.string.map_hint_buy, (d, w) -> executeHintPurchase(type))
                .setNegativeButton(R.string.map_hint_cancel, null)
                .show();
    }

    private void executeHintPurchase(MapHintType type) {
        int slot = currentOpenSlot();
        if (slot == 0) return;
        if (coinManager.getCoins() < type.price) {
            Toast.makeText(this, R.string.map_hint_not_enough_coins, Toast.LENGTH_SHORT).show();
            return;
        }

        String revealText = generateHintText(type);
        boolean deducted = coinManager.deductCoins(type.price);
        if (!deducted) {
            Toast.makeText(this, R.string.map_hint_not_enough_coins, Toast.LENGTH_SHORT).show();
            return;
        }

        // Record in the correct slot
        if (slot == 1) {
            slot1Used = true; hint1Type = type; hint1Text = revealText;
        } else {
            slot2Used = true; hint2Type = type; hint2Text = revealText;
        }

        updateCoinBalance();
        updateHintButton();
        rebuildHintFeedback();

        // Show the revealed information
        Toast.makeText(this,
                getString(R.string.map_hint_revealed_format, type.getLabel(this), revealText),
                Toast.LENGTH_LONG).show();
    }

    /**
     * Generate the text that a hint reveals for the current target country.
     * Falls back to a graceful message when the dataset field is empty.
     */
    private String generateHintText(MapHintType type) {
        switch (type) {
            case REVEAL_CONTINENT:
                String continent = targetCountry.getContinent();
                return (continent != null && !continent.isEmpty())
                        ? continent : getString(R.string.unknown_country);

            case REVEAL_FUN_FACT:
                String fact = targetCountry.getFunFact();
                return (fact != null && !fact.trim().isEmpty())
                        ? fact : getString(R.string.map_hint_no_fun_fact);

            case REVEAL_FIRST_LETTER:
                String name = targetCountry.getCountry();
                return (name != null && !name.isEmpty())
                        ? String.valueOf(name.charAt(0)).toUpperCase(Locale.US)
                        : getString(R.string.unknown_country);

            case REVEAL_FLAG_COLORS:
                List<String> colors = targetCountry.getFlagColors();
                if (colors == null || colors.isEmpty())
                    return getString(R.string.map_hint_no_flag_colors);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < colors.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(colors.get(i));
                }
                return sb.toString();

            default:
                return getString(R.string.unknown_country);
        }
    }

    /**
     * Repopulate the hint feedback area below the input row with any
     * purchased hints (called on create/restore and after each purchase).
     */
    private void rebuildHintFeedback() {
        if (tvGuessFeedback == null) return;
        StringBuilder sb = new StringBuilder();
        if (slot1Used && hint1Type != null && hint1Text != null) {
            sb.append(getString(R.string.map_hint_revealed_format,
                    hint1Type.getLabel(this), hint1Text));
        }
        if (slot2Used && hint2Type != null && hint2Text != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(getString(R.string.map_hint_revealed_format,
                    hint2Type.getLabel(this), hint2Text));
        }
        if (sb.length() > 0) {
            tvGuessFeedback.setText(sb.toString());
            tvGuessFeedback.setTextColor(0xFF5D4037);
            tvGuessFeedback.setVisibility(View.VISIBLE);
        } else {
            tvGuessFeedback.setVisibility(View.GONE);
        }
    }

    /** Rebuild the full guess list from guessedCountries (used after rotation). */
    private void rebuildGuessList() {
        if (layoutGuessList == null) return;
        layoutGuessList.removeAllViews();
        for (Country c : guessedCountries) {
            addGuessRow(c, false);
        }
    }

    /** Inflate and insert one guess row. animate=true plays slide-in from top. */
    private void addGuessRow(Country c, boolean animate) {
        if (layoutGuessList == null) return;
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_map_guess_row, layoutGuessList, false);

        ImageView ivFlag   = row.findViewById(R.id.ivGuessFlag);
        TextView  tvName   = row.findViewById(R.id.tvGuessName);
        TextView  tvResult = row.findViewById(R.id.tvGuessResult);

        // Flag
        String flagFile = c.getFlag();
        if (flagFile != null && !flagFile.trim().isEmpty()) {
            try {
                InputStream is = getAssets().open("flags/" + flagFile.trim());
                Drawable d = Drawable.createFromStream(is, null);
                is.close();
                if (d != null) ivFlag.setImageDrawable(d);
            } catch (IOException ignored) {}
        }

        // Name
        boolean arabic = LanguageManager.isArabic(this);
        String displayName = (arabic && c.getCountryAr() != null && !c.getCountryAr().isEmpty())
                ? c.getCountryAr() : c.getCountry();
        tvName.setText(displayName);

        // Result mark — ✓ green if correct (shown on win), ✗ red otherwise
        boolean isCorrect = c.getCountry().equalsIgnoreCase(targetCountry.getCountry());
        if (isCorrect) {
            tvResult.setText("✓");
            tvResult.setTextColor(0xFF2E7D32);
        } else {
            tvResult.setText("✗");
            tvResult.setTextColor(0xFFC62828);
        }

        // Insert at top (index 0) so newest guess appears first
        layoutGuessList.addView(row, 0);

        if (animate) {
            Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_in_top);
            row.startAnimation(anim);
            // Scroll to top after animation settles
            scrollGuesses.post(() -> scrollGuesses.smoothScrollTo(0, 0));
        }
    }

    // =========================================================================
    // Result dialog
    // =========================================================================

    private void showResultDialog(boolean won, long elapsedMs, int coinsAwarded) {
        if (isFinishing() || isDestroyed()) return;

        boolean isLastLevel   = (levelWithinDifficulty >= difficultyCountries.size());
        boolean wasAlreadyDone = progressManager.hasRewardBeenIssued(difficulty, levelWithinDifficulty)
                && coinsAwarded == 0 && won;

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_map_result, null);

        TextView tvTitle         = dialogView.findViewById(R.id.tvResultTitle);
        TextView tvCountry       = dialogView.findViewById(R.id.tvResultCountry);
        TextView tvGuesses       = dialogView.findViewById(R.id.tvResultGuesses);
        TextView tvTime          = dialogView.findViewById(R.id.tvResultTime);
        LinearLayout layoutCoins = dialogView.findViewById(R.id.layoutResultCoins);
        TextView tvCoinsAmount   = dialogView.findViewById(R.id.tvResultCoinsAmount);
        TextView tvBreakdown     = dialogView.findViewById(R.id.tvResultCoinsBreakdown);
        TextView tvAlreadyDone   = dialogView.findViewById(R.id.tvResultAlreadyCompleted);
        Button   btnPrimary      = dialogView.findViewById(R.id.btnResultPrimary);
        Button   btnSecondary    = dialogView.findViewById(R.id.btnResultSecondary);

        // Title
        if (won) {
            tvTitle.setText(isLastLevel
                    ? getString(R.string.map_result_all_done_title)
                    : getString(R.string.success_title));
        } else {
            tvTitle.setText(getString(R.string.map_result_failed_title));
        }

        tvCountry.setText(getString(R.string.success_country_format,
                resolveDisplayName(targetCountry)));
        tvGuesses.setText(getString(R.string.map_result_guesses_format,
                guessCount, MAX_GUESSES));
        tvTime.setText(getString(R.string.map_result_time_format, formatElapsed(elapsedMs)));

        // Coins row
        if (won && coinsAwarded > 0) {
            layoutCoins.setVisibility(View.VISIBLE);
            tvCoinsAmount.setText(getString(R.string.map_result_coins_format, coinsAwarded));
            int bonus = MapRewardCalculator.speedBonus(difficulty, elapsedMs);
            int base  = MapRewardCalculator.baseReward(difficulty);
            if (bonus > 0) {
                tvBreakdown.setText(getString(R.string.map_result_bonus_format, base, bonus));
                tvBreakdown.setVisibility(View.VISIBLE);
            } else {
                tvBreakdown.setText(getString(R.string.map_result_no_bonus, base));
                tvBreakdown.setVisibility(View.VISIBLE);
            }
        } else if (won && wasAlreadyDone) {
            tvAlreadyDone.setVisibility(View.VISIBLE);
        }

        // Build and configure the dialog
        resultDialog = new android.app.Dialog(this,
                android.R.style.Theme_Translucent_NoTitleBar);
        resultDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        resultDialog.setContentView(dialogView);
        resultDialog.setCancelable(false);

        if (isLastLevel) {
            btnPrimary.setText(R.string.btn_finish);
            btnPrimary.setOnClickListener(v -> { resultDialog.dismiss(); finish(); });
        } else {
            btnPrimary.setText(R.string.btn_next_level);
            btnPrimary.setOnClickListener(v -> { resultDialog.dismiss(); openNextLevel(); });
        }
        btnSecondary.setText(R.string.btn_try_again);
        btnSecondary.setOnClickListener(v -> { resultDialog.dismiss(); replayCurrentLevel(); });

        styleDialogWindow(resultDialog);
        entranceAnim(dialogView);
        resultDialog.show();
    }

    // =========================================================================
    // Navigation
    // =========================================================================

    private void replayCurrentLevel() {
        Intent intent = new Intent(this, GuessByMapActivity.class);
        intent.putExtra(EXTRA_DIFFICULTY, difficulty.ordinal());
        intent.putExtra(EXTRA_LEVEL, levelWithinDifficulty);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        finish();
        startActivity(intent);
    }

    private void openNextLevel() {
        int next = levelWithinDifficulty + 1;
        if (next > difficultyCountries.size()) { finish(); return; }
        Intent intent = new Intent(this, GuessByMapActivity.class);
        intent.putExtra(EXTRA_DIFFICULTY, difficulty.ordinal());
        intent.putExtra(EXTRA_LEVEL, next);
        finish();
        startActivity(intent);
    }

    // =========================================================================
    // Stopwatch
    // =========================================================================

    private void startStopwatch() {
        stopStopwatch();
        if (swBaseMs == 0L)
            swBaseMs = SystemClock.elapsedRealtime() - swElapsedAtPause;
        swRunning = true;
        stopwatchRunnable = new Runnable() {
            @Override public void run() {
                if (!swRunning) return;
                updateTimerDisplay(currentElapsedMs());
                stopwatchHandler.postDelayed(this, STOPWATCH_TICK_MS);
            }
        };
        stopwatchHandler.post(stopwatchRunnable);
    }

    private void stopStopwatch() {
        swRunning = false;
        swElapsedAtPause = currentElapsedMs();
        if (stopwatchRunnable != null) {
            stopwatchHandler.removeCallbacks(stopwatchRunnable);
            stopwatchRunnable = null;
        }
    }

    private long currentElapsedMs() {
        if (!swRunning || swBaseMs == 0L) return swElapsedAtPause;
        return SystemClock.elapsedRealtime() - swBaseMs;
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private void updateTimerDisplay(long ms) {
        tvTimer.setText(formatElapsed(ms));
        tvTimer.setBackgroundResource(R.drawable.timer_background);
        tvTimer.setTextColor(0xFF3E2723);
    }

    private static String formatElapsed(long ms) {
        long s = ms / 1000L, h = s / 3600L, m = (s % 3600L) / 60L, sec = s % 60L;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                     : String.format(Locale.US, "%02d:%02d", m, sec);
    }

    private void updateCoinBalance() {
        if (tvCoinBalance != null)
            tvCoinBalance.setText(String.valueOf(coinManager.getCoins()));
    }

    private void updateLevelLabel() {
        tvLevelLabel.setText(getString(R.string.map_level_label_format,
                difficulty.getLabel(this), levelWithinDifficulty));
    }

    private void updateGuessCounter() {
        tvAttemptCounter.setText(getString(R.string.map_guesses_format,
                guessCount, MAX_GUESSES));
    }

    private String resolveDisplayName(Country c) {
        if (LanguageManager.isArabic(this) && c.getCountryAr() != null
                && !c.getCountryAr().isEmpty()) return c.getCountryAr();
        return c.getCountry();
    }

    private void styleDialogWindow(android.app.Dialog d) {
        Window w = d.getWindow();
        if (w == null) return;
        w.setBackgroundDrawableResource(android.R.color.transparent);
        w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.88f),
                WindowManager.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.CENTER);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.dimAmount = 0.6f;
        w.setAttributes(lp);
    }

    private void entranceAnim(View v) {
        v.setAlpha(0f); v.setScaleX(0.92f); v.setScaleY(0.92f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void dismissOpenDialogs() {
        if (resultDialog != null && resultDialog.isShowing()) { resultDialog.dismiss(); resultDialog = null; }
        if (hintDialog   != null && hintDialog.isShowing())   { hintDialog.dismiss();   hintDialog   = null; }
    }

    // =========================================================================
    // Country filtering — mirrors MapLevelSelectActivity exactly
    // =========================================================================

    private List<Country> getCountriesForDifficulty(MapDifficulty diff) {
        List<Country> result = new ArrayList<>();
        for (Country c : repo.getAllCountries()) {
            String cat = c.getDifficultyCategory();
            if (diff.dataKey.equalsIgnoreCase(cat == null ? "" : cat.trim())) result.add(c);
        }
        return result;
    }

    // =========================================================================
    // Background blur
    // =========================================================================

    private void blurBackground(ImageView iv) {
        try {
            BitmapDrawable bd = (BitmapDrawable) iv.getDrawable();
            if (bd == null) return;
            Bitmap blurred = bd.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
            RenderScript rs = RenderScript.create(this);
            Allocation in = Allocation.createFromBitmap(rs, blurred);
            Allocation out = Allocation.createTyped(rs, in.getType());
            ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blur.setRadius(8f); blur.setInput(in); blur.forEach(out); out.copyTo(blurred);
            iv.setImageBitmap(blurred);
            rs.destroy();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
