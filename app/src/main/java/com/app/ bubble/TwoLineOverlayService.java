package com.app.bubble;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class TwoLineOverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    // Views from the layout
    private View lineTop, lineBottom;
    private View linesContainer;
    private View handleTop, handleBottom;
    
    // Logic for dragging
    private View activeDragView = null;
    private View activeHandleView = null;
    private float initialTouchY, initialViewY;
    private int screenHeight;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        setupOverlay();
    }

    private void setupOverlay() {
        // 1. Inflate the new layout
        LayoutInflater inflater = LayoutInflater.from(this);
        overlayView = inflater.inflate(R.layout.layout_two_line_overlay, null);

        // 2. Determine Window Type based on Android Version
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        // 3. Set Window Parameters
        // IMPORTANT: FLAG_SECURE is REMOVED so we can take screenshots.
        // FLAG_NOT_FOCUSABLE: Ensures system keys (Back, Home) still work.
        // FLAG_LAYOUT_IN_SCREEN: Allows the overlay to draw over the status bar.
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        // 4. Add View to WindowManager
        windowManager.addView(overlayView, params);

        // 5. Initialize Views
        lineTop = overlayView.findViewById(R.id.line_top);
        lineBottom = overlayView.findViewById(R.id.line_bottom);
        linesContainer = overlayView.findViewById(R.id.lines_container);
        handleTop = overlayView.findViewById(R.id.handle_top);
        handleBottom = overlayView.findViewById(R.id.handle_bottom);

        // 6. Setup Buttons
        ImageView btnClose = overlayView.findViewById(R.id.btn_close_overlay);
        Button btnAdd = overlayView.findViewById(R.id.btn_add_page);
        Button btnDone = overlayView.findViewById(R.id.btn_done);

        // Close Action
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSelf(); // Destroys the service and removes view
            }
        });

        // Add Page Action (Manual Accumulator)
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendIntentToService("ACTION_ADD_PAGE");
            }
        });

        // Done Action (Finish and Copy)
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendIntentToService("ACTION_DONE");
                stopSelf(); // Close the overlay automatically
            }
        });

        // 7. Setup Touch Listener for Dragging Lines
        setupTouchListener();
    }

    /**
     * Sends the current coordinates of the Green and Red lines to the main service.
     */
    private void sendIntentToService(String action) {
        // Get absolute Y coordinates on screen
        int[] topLoc = new int[2];
        lineTop.getLocationOnScreen(topLoc);
        
        int[] botLoc = new int[2];
        lineBottom.getLocationOnScreen(botLoc);

        int topY = topLoc[1];
        int botY = botLoc[1];

        // Validation: Top must be above Bottom
        if (topY >= botY) {
            Toast.makeText(this, "Green Line must be above Red Line!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create the Rect representing the area to crop
        Rect cropRect = new Rect(0, topY, getResources().getDisplayMetrics().widthPixels, botY);

        // Send Intent to FloatingTranslatorService
        Intent intent = new Intent(this, FloatingTranslatorService.class);
        intent.setAction(action);
        intent.putExtra("RECT", cropRect);
        
        // Use startService to deliver the command
        startService(intent);
    }

    /**
     * Handles the logic for dragging the Green and Red lines.
     */
    private void setupTouchListener() {
        linesContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float rawY = event.getRawY();
                // How close (in pixels) the finger needs to be to grab a line
                int threshold = 150; 

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Calculate distances to both lines
                        int[] topLoc = new int[2]; 
                        lineTop.getLocationOnScreen(topLoc);
                        
                        int[] botLoc = new int[2]; 
                        lineBottom.getLocationOnScreen(botLoc);
                        
                        float distTop = Math.abs(rawY - topLoc[1]);
                        float distBot = Math.abs(rawY - botLoc[1]);

                        // Determine which line is closer
                        if (distTop < threshold && distTop < distBot) {
                            // Grab Top Line
                            activeDragView = lineTop;
                            activeHandleView = handleTop;
                            initialTouchY = rawY;
                            initialViewY = lineTop.getY();
                            return true;
                        } else if (distBot < threshold) {
                            // Grab Bottom Line
                            activeDragView = lineBottom;
                            activeHandleView = handleBottom;
                            initialTouchY = rawY;
                            initialViewY = lineBottom.getY();
                            return true;
                        }
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        if (activeDragView != null) {
                            // Calculate movement
                            float diff = rawY - initialTouchY;
                            float newY = initialViewY + diff;
                            
                            // Prevent dragging off-screen (Bounds Check)
                            if (newY < 0) newY = 0;
                            // Keep it above the bottom control bar (approx 100dp)
                            if (newY > screenHeight - 200) newY = screenHeight - 200;

                            // Update Position
                            activeDragView.setY(newY);
                            
                            // Update the Handle Position (Sync with line)
                            // The handle is centered on the line, but setY sets the top edge.
                            // We adjust slightly to keep visual alignment.
                            if (activeHandleView != null) {
                                activeHandleView.setY(newY - (activeHandleView.getHeight() / 2) + (activeDragView.getHeight() / 2));
                            }
                            return true;
                        }
                        return false;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Release the line
                        activeDragView = null;
                        activeHandleView = null;
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null) {
            windowManager.removeView(overlayView);
        }
    }
}