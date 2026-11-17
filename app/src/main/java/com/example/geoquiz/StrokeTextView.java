package com.example.geoquiz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * A TextView that draws a solid stroke outline behind the text,
 * then redraws the fill on top — giving a clean bordered text effect.
 */
public class StrokeTextView extends AppCompatTextView {

    private float strokeWidth = 6f;  // px; override via setStrokeWidth()

    public StrokeTextView(Context context) {
        super(context);
    }

    public StrokeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StrokeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setStrokeWidth(float px) {
        this.strokeWidth = px;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // --- Draw stroke pass ---
        Paint p = getPaint();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(strokeWidth);
        p.setStrokeJoin(Paint.Join.ROUND);
        int originalColor = getCurrentTextColor();
        setTextColor(0xFF000000); // black stroke
        super.onDraw(canvas);

        // --- Draw fill pass ---
        p.setStyle(Paint.Style.FILL);
        setTextColor(originalColor);
        super.onDraw(canvas);
    }
}
