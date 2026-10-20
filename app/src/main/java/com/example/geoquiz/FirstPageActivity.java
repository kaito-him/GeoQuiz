package com.example.geoquiz;

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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class FirstPageActivity extends AppCompatActivity {

    private TextView coinText;
    private ImageButton btnMenu;
    private LinearLayout btnDailyCountry;
    private CoinManager coinManager;

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

        coinManager = new CoinManager(this);

        // Initialize coin counter
        coinText = findViewById(R.id.coinText);

        // Initialize menu button
        btnMenu = findViewById(R.id.btnMenu);
        btnMenu.setOnClickListener(v -> {
            // TODO: Open side menu/navigation drawer
            Toast.makeText(this, "Menu clicked", Toast.LENGTH_SHORT).show();
        });

        // Initialize game mode buttons
        btnDailyCountry = findViewById(R.id.btnDailyCountry);
        btnDailyCountry.setOnClickListener(v -> openDailyCountry());

        // Apply blur to background
        ImageView backgroundImage = findViewById(R.id.backgroundImage);
        blurBackground(backgroundImage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh coin display whenever the player returns from any game mode
        coinText.setText(String.valueOf(coinManager.getCoins()));
    }

    private void openDailyCountry() {
        Intent intent = new Intent(this, DailyLevelSelectActivity.class);
        startActivity(intent);
    }

    private void blurBackground(ImageView imageView) {
        try {
            BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();
            Bitmap bitmap = drawable.getBitmap();

            // Create a mutable bitmap copy
            Bitmap blurred = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            // Create RenderScript context
            RenderScript renderScript = RenderScript.create(this);

            // Create allocations for input and output
            Allocation input = Allocation.createFromBitmap(renderScript, blurred);
            Allocation output = Allocation.createTyped(renderScript, input.getType());

            // Create blur script
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
            script.setRadius(8f); // Blur radius (1-25)
            script.setInput(input);
            script.forEach(output);

            // Copy output to blurred bitmap
            output.copyTo(blurred);

            // Set blurred image
            imageView.setImageBitmap(blurred);

            // Clean up
            renderScript.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
