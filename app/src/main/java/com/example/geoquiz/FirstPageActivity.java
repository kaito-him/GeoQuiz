package com.example.geoquiz;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

public class FirstPageActivity extends AppCompatActivity {

    private TextView coinText;
    private ImageButton btnMenu;
    private LinearLayout btnDailyCountry;
    private CoinManager coinManager;
    private DrawerLayout drawerLayout;

    // ------------------------------------------------------------------
    // Locale — apply before super.attachBaseContext so strings are correct
    // ------------------------------------------------------------------

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LanguageManager.applyLanguage(base));
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_first_page);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        coinManager  = new CoinManager(this);
        drawerLayout = findViewById(R.id.drawerLayout);

        // Coin counter
        coinText = findViewById(R.id.coinText);

        // Menu button → open drawer
        btnMenu = findViewById(R.id.btnMenu);
        btnMenu.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // Drawer: Language item
        View menuItemLanguage = drawerLayout.findViewById(R.id.menuItemLanguage);
        if (menuItemLanguage != null) {
            menuItemLanguage.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showLanguageDialog();
            });
        }

        // Game mode buttons
        btnDailyCountry = findViewById(R.id.btnDailyCountry);
        btnDailyCountry.setOnClickListener(v -> openDailyCountry());

        LinearLayout btnGuessByMap = findViewById(R.id.btnGuessByMap);
        if (btnGuessByMap != null) {
            btnGuessByMap.setOnClickListener(v -> openGuessByMap());
        }

        // Apply blur to background
        ImageView backgroundImage = findViewById(R.id.backgroundImage);
        blurBackground(backgroundImage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        coinText.setText(String.valueOf(coinManager.getCoins()));
    }

    // ------------------------------------------------------------------
    // Language dialog
    // ------------------------------------------------------------------

    private void showLanguageDialog() {
        String currentLang = LanguageManager.getLanguage(this);

        // Build the two options
        String[] options = {
            getString(R.string.lang_english),
            getString(R.string.lang_arabic)
        };
        int checkedItem = LanguageManager.LANG_AR.equals(currentLang) ? 1 : 0;

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.side_menu_language_title))
            .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                String newLang = (which == 1) ? LanguageManager.LANG_AR : LanguageManager.LANG_EN;
                if (!newLang.equals(currentLang)) {
                    dialog.dismiss();
                    LanguageManager.setLanguage(this, newLang);
                    // Restart the whole task stack so every screen picks up the new locale
                    Intent intent = new Intent(this, SplashActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else {
                    dialog.dismiss();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    private void openDailyCountry() {
        Intent intent = new Intent(this, DailyLevelSelectActivity.class);
        startActivity(intent);
    }

    private void openGuessByMap() {
        Intent intent = new Intent(this, MapDifficultyActivity.class);
        startActivity(intent);
    }

    // ------------------------------------------------------------------
    // Back press — close drawer first if open
    // ------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------
    // Blur
    // ------------------------------------------------------------------

    private void blurBackground(ImageView imageView) {
        try {
            BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();
            Bitmap bitmap = drawable.getBitmap();
            Bitmap blurred = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            RenderScript renderScript = RenderScript.create(this);
            Allocation input  = Allocation.createFromBitmap(renderScript, blurred);
            Allocation output = Allocation.createTyped(renderScript, input.getType());
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
            script.setRadius(8f);
            script.setInput(input);
            script.forEach(output);
            output.copyTo(blurred);
            imageView.setImageBitmap(blurred);
            renderScript.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
