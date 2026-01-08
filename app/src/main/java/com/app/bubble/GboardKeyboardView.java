package com.app.bubble;

import android.content.Context;
import android.graphics.Canvas;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import java.util.List;

public class GboardKeyboardView extends KeyboardView {

    private Context mContext;
    private PopupWindow mPopupWindow;
    private KeyboardView mPopupKeyboardView;
    private boolean isPopupShowing = false;
    private int mActivePointerId = -1;
    
    // Logic for Long Press
    private Handler mHandler = new Handler();
    private int mLongPressKeyIndex = -1;
    private static final int LONG_PRESS_TIMEOUT = 400; // ms to trigger popup

    private Runnable mLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mLongPressKeyIndex != -1) {
                showCustomPopup(mLongPressKeyIndex);
            }
        }
    };

    public GboardKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GboardKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        this.mContext = context;
        // Disable the built-in preview to prevent conflict
        setPreviewEnabled(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        int action = me.getActionMasked();

        // 1. IF POPUP IS OPEN: Redirect all touches to the popup logic
        if (isPopupShowing) {
            handlePopupTouch(me);
            return true; // Consume event so main keyboard doesn't process it
        }

        // 2. MAIN KEYBOARD LOGIC
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = me.getPointerId(0);
                int x = (int) me.getX();
                int y = (int) me.getY();
                
                // Check which key was pressed
                mLongPressKeyIndex = getKeyIndex(x, y);
                
                if (mLongPressKeyIndex != -1) {
                    Keyboard.Key key = getKeyboard().getKeys().get(mLongPressKeyIndex);
                    // Check if it is the DOT key (Code 46)
                    if (key.codes[0] == 46) {
                        // Start the timer for long press
                        mHandler.postDelayed(mLongPressRunnable, LONG_PRESS_TIMEOUT);
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // If finger moves too far before popup opens, cancel the timer
                if (mLongPressKeyIndex != -1) {
                    int curX = (int) me.getX();
                    int curY = (int) me.getY();
                    // Simple check: if moved off key, cancel
                    if (getKeyIndex(curX, curY) != mLongPressKeyIndex) {
                        mHandler.removeCallbacks(mLongPressRunnable);
                        mLongPressKeyIndex = -1;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mHandler.removeCallbacks(mLongPressRunnable);
                mLongPressKeyIndex = -1;
                break;
        }

        // Pass to standard keyboard for normal typing
        return super.onTouchEvent(me);
    }

    // --- POPUP LOGIC ---

    private void showCustomPopup(int keyIndex) {
        isPopupShowing = true;
        
        // 1. Inflate the custom layout
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View popupView = inflater.inflate(R.layout.layout_custom_popup, null);
        
        // 2. Setup the KeyboardView inside the popup
        mPopupKeyboardView = popupView.findViewById(R.id.popup_keyboard_view);
        Keyboard popupKeyboard = new Keyboard(mContext, R.xml.popup_template);
        mPopupKeyboardView.setKeyboard(popupKeyboard);
        
        // 3. Create the Window
        mPopupWindow = new PopupWindow(popupView, 
                                     ViewGroup.LayoutParams.WRAP_CONTENT, 
                                     ViewGroup.LayoutParams.WRAP_CONTENT);
        mPopupWindow.setBackgroundDrawable(null); 
        mPopupWindow.setOutsideTouchable(false);
        mPopupWindow.setClippingEnabled(false); // Allow drawing outside bounds

        // 4. Calculate Position (Center above the Dot key)
        Keyboard.Key key = getKeyboard().getKeys().get(keyIndex);
        View parent = (View) getParent(); 
        
        int[] location = new int[2];
        getLocationInWindow(location);
        
        int popupWidth = 800; // Approx width of popup (8 keys * width)
        // Center popup horizontally relative to the key, then shift up
        int showX = (key.x + location[0]) - (popupWidth / 2) + (key.width / 2);
        int showY = location[1] + key.y - 150; // 150px above the key
        
        // Show it
        mPopupWindow.showAtLocation(this, Gravity.NO_GRAVITY, showX, showY);
        
        // Cancel the visual feedback on the main keyboard dot key
        // so it doesn't look like we are typing "."
        MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        super.onTouchEvent(cancel);
        cancel.recycle();
    }

    private void handlePopupTouch(MotionEvent me) {
        int action = me.getActionMasked();
        
        // Convert screen coordinates to Popup coordinates
        int[] popupLoc = new int[2];
        mPopupKeyboardView.getLocationOnScreen(popupLoc);
        
        float touchX = me.getRawX() - popupLoc[0];
        float touchY = me.getRawY() - popupLoc[1];

        Keyboard popupKeyboard = mPopupKeyboardView.getKeyboard();
        List<Keyboard.Key> keys = popupKeyboard.getKeys();
        
        // Find which key in the popup we are touching
        int selectedIndex = -1;
        for (int i = 0; i < keys.size(); i++) {
            Keyboard.Key k = keys.get(i);
            if (touchX >= k.x && touchX <= k.x + k.width &&
                touchY >= k.y && touchY <= k.y + k.height) {
                selectedIndex = i;
                break;
            }
        }

        // UPDATE VISUALS (Blue Highlight)
        // We manually toggle the 'pressed' state of the keys
        for (int i = 0; i < keys.size(); i++) {
            keys.get(i).pressed = (i == selectedIndex);
        }
        mPopupKeyboardView.invalidateAllKeys(); // Redraw popup

        // HANDLE RELEASE
        if (action == MotionEvent.ACTION_UP) {
            if (selectedIndex != -1) {
                // User selected a symbol!
                int code = keys.get(selectedIndex).codes[0];
                getOnKeyboardActionListener().onKey(code, null);
            }
            dismissPopup();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            dismissPopup();
        }
    }

    private void dismissPopup() {
        if (mPopupWindow != null && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
        }
        isPopupShowing = false;
        mPopupWindow = null;
        mPopupKeyboardView = null;
    }

    // Helper to find key index on main keyboard
    private int getKeyIndex(int x, int y) {
        List<Keyboard.Key> keys = getKeyboard().getKeys();
        for (int i = 0; i < keys.size(); i++) {
            Keyboard.Key k = keys.get(i);
            if (k.isInside(x, y)) {
                return i;
            }
        }
        return -1;
    }
}