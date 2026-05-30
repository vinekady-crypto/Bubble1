package com.app.bubble;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * This service is responsible for performing continuous scroll gestures.
 * It is triggered when the user holds a selection handle at the bottom of the screen.
 */
public class GlobalScrollService extends AccessibilityService {

    private static GlobalScrollService sInstance;
    private Handler scrollHandler;
    private boolean isScrolling = false;

    // Defines how fast the scroll repeats (in milliseconds)
    private static final long SCROLL_INTERVAL = 100; 
    // Defines how long the swipe gesture takes (smoothness)
    private static final int GESTURE_DURATION = 300; 

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        scrollHandler = new Handler(Looper.getMainLooper());
        Log.d("GlobalScrollService", "Service Connected");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopScroll();
        sInstance = null;
        Log.d("GlobalScrollService", "Service Unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used
    }

    @Override
    public void onInterrupt() {
        stopScroll();
    }

    public static GlobalScrollService getInstance() {
        return sInstance;
    }

    /**
     * Starts the continuous scroll loop.
     */
    public static void startSmoothScroll() {
        if (sInstance != null && !sInstance.isScrolling) {
            sInstance.isScrolling = true;
            sInstance.scrollHandler.post(sInstance.scrollRunnable);
        }
    }

    /**
     * Stops the continuous scroll loop.
     */
    public static void stopScroll() {
        if (sInstance != null) {
            sInstance.isScrolling = false;
            sInstance.scrollHandler.removeCallbacks(sInstance.scrollRunnable);
        }
    }

    // The loop that triggers the swipe repeatedly
    private Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScrolling) {
                performScrollGesture();
                // Schedule the next swipe
                scrollHandler.postDelayed(this, SCROLL_INTERVAL);
            }
        }
    };

    /**
     * Performs a single short vertical swipe.
     */
    private void performScrollGesture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;

            // Start swipe from 80% down
            float startX = width / 2.0f;
            float startY = height * 0.80f;

            // End swipe at 60% down (a short drag)
            float endX = width / 2.0f;
            float endY = height * 0.60f;

            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription gesture = builder
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, GESTURE_DURATION))
                    .build();

            dispatchGesture(gesture, null, null);
        }
    }
}