package com.app.bubble;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

public class CropSelectionView extends View {

    private Paint paint;
    private Paint borderPaint;
    private float startX, startY, endX, endY;
    private RectF selectionRect = new RectF();

    private Handler autoCloseHandler = new Handler(Looper.getMainLooper());
    private Runnable autoCloseRunnable;
    private long timeoutDuration; 
    
    private int screenHeight;
    private int screenWidth;
    private static final int SCROLL_THRESHOLD = 150; // Pixels from bottom to trigger scroll

    public CropSelectionView(Context context) {
        super(context);
        init();
    }

    private void init() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenHeight = metrics.heightPixels;
        screenWidth = metrics.widthPixels;

        paint = new Paint();
        paint.setColor(Color.argb(100, 0, 100, 255)); // Transparent blue
        paint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint();
        borderPaint.setColor(Color.BLUE);
        borderPaint.setStrokeWidth(5f);
        borderPaint.setStyle(Paint.Style.STROKE);

        // Read settings
        SharedPreferences prefs = getContext().getSharedPreferences(
            SettingsActivity.PREFS_NAME, 
            Context.MODE_PRIVATE
        );
        timeoutDuration = prefs.getLong(SettingsActivity.KEY_TIMER_DURATION, 5000L);

        autoCloseRunnable = new Runnable() {
            @Override
            public void run() {
                // Ensure we stop scrolling if the timer kills the view
                GlobalScrollService.stopScroll();
                
                // Calculate the final normalized rect
                RectF normalized = getNormalizedRect();

                // Safety Check: ensure the rect has some size
                if (normalized.width() < 10 || normalized.height() < 10) {
                   // If too small (accidental tap), default to a reasonable size or ignore
                   // Here we default to full screen width for safety if it was a scroll attempt
                   normalized.left = 0;
                   normalized.right = screenWidth;
                }

                // Tell the service that selection is finished
                try {
                    FloatingTranslatorService service = (FloatingTranslatorService) getContext();
                    Rect finalRect = new Rect(
                        (int) normalized.left,
                        (int) normalized.top,
                        (int) normalized.right,
                        (int) normalized.bottom
                    );
                    service.onCropFinished(finalRect);
                } catch (ClassCastException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    // Helper to ensure left is always < right and top < bottom
    private RectF getNormalizedRect() {
        float left = Math.min(startX, endX);
        float top = Math.min(startY, endY);
        float right = Math.max(startX, endX);
        float bottom = Math.max(startY, endY);
        return new RectF(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        selectionRect = getNormalizedRect();
        
        // Draw the blue fill
        canvas.drawRect(selectionRect, paint);
        // Draw the border
        canvas.drawRect(selectionRect, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getRawX();
                startY = event.getRawY();
                endX = startX;
                endY = startY;
                resetAutoCloseTimer();
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                endX = event.getRawX();
                endY = event.getRawY();
                
                // *** DRAG-TO-SCROLL LOGIC ***
                // Check if finger is at the bottom edge of the screen
                if (endY >= screenHeight - SCROLL_THRESHOLD) {
                    // Force the selection to the absolute bottom visually
                    endY = screenHeight; 
                    
                    // Trigger continuous smooth scrolling
                    GlobalScrollService.startSmoothScroll();
                } else {
                    // Stop scrolling if finger moves away from edge
                    GlobalScrollService.stopScroll();
                }

                resetAutoCloseTimer();
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                // Stop scrolling immediately when finger lifts
                GlobalScrollService.stopScroll();
                return true;

            case MotionEvent.ACTION_CANCEL:
                // *** FIX FOR GLITCH ***
                // When the system takes over touch events (during scroll), it sends CANCEL.
                // We MUST stop scrolling logic, but we MUST NOT reset coordinates.
                // Just stop the timer and the scroll service.
                GlobalScrollService.stopScroll();
                autoCloseHandler.removeCallbacks(autoCloseRunnable);
                return true;
        }
        return false;
    }

    private void resetAutoCloseTimer() {
        autoCloseHandler.removeCallbacks(autoCloseRunnable);
        autoCloseHandler.postDelayed(autoCloseRunnable, timeoutDuration);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Safety check to ensure scrolling stops if view is removed
        GlobalScrollService.stopScroll();
    }
}