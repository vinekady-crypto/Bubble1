package com.app.bubble;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BubbleKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private LinearLayout mainLayout;
    private KeyboardView kv;
    private View candidateView;
    private LinearLayout candidateContainer;
    private LinearLayout toolbarContainer; 
    private View emojiPaletteView;

    private View clipboardPaletteView;
    private ClipboardUiManager clipboardUiManager;

    private View translationPanelView;
    private TranslationUiManager translationUiManager;
    private boolean isTranslationMode = false;
    private StringBuilder translationBuffer = new StringBuilder();
    private int lastSentTranslationLength = 0;
    private String lastSentTranslation = ""; // Added to track committed translation text
    private boolean clearTranslationOnNextResult = false;

    private boolean isDirectTranslateEnabled = false;
    private String directTargetLangCode = "es"; 
    private StringBuilder directBuffer = new StringBuilder();
    private int lastDirectOutputLength = 0;
    private long lastGlobeClickTime = 0;
    private Handler directHandler = new Handler(Looper.getMainLooper());
    private Runnable directTranslateRunnable;
    private ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

    // --- AUTO SAVE VARIABLES ---
    private boolean isAutoSaveEnabled = false;
    private StringBuilder autoSaveBuffer = new StringBuilder();
    private boolean isTypingNewEntry = true; // True = Create new clip; False = Update existing clip

    // Dynamic Customizable Toolbar Views
    private ImageButton btnGridMenu;
    private LinearLayout activeToolsContainer;
    private View gridMenuView;
    private RecyclerView gridMenuRecycler;
    private GridMenuAdapter gridAdapter;
    private boolean isGridMenuVisible = false;

    // Helper managers
    private SharedPreferences sharedPreferences;
    private VoiceInputManager voiceInputManager;

    private Keyboard keyboardQwerty;
    private Keyboard keyboardSymbols;

    private boolean isCaps = false;
    private boolean isEmojiVisible = false;
    private StringBuilder currentWord = new StringBuilder(); 
    private String lastCommittedWord = null; 

    private boolean justAutoCorrected = false;
    private String lastOriginalWord = "";
    private String lastCorrectedWord = "";
    private boolean ignoreNextCorrection = false;

    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private boolean isSpaceLongPressed = false;
    private static final int LONG_PRESS_DELAY = 500; 

    // Persistent storage keys for dynamic layout customization
    private static final String PREFS_NAME = "BubbleTranslatorPrefs";
    private static final String KEY_ACTIVE_TOOLS = "KeyboardActiveTools";
    private static final String KEY_INACTIVE_TOOLS = "KeyboardInactiveTools";

    // Defaults matching Gboard first-run installation specification
    private static final String DEFAULT_ACTIVE_TOOLS = "clipboard,translate,autosave,mic";
    private static final String DEFAULT_INACTIVE_TOOLS = "keyboard_switch,direct_translate,bubble_launcher,ocr_copy,scanner,settings";

    private Runnable spaceLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            isSpaceLongPressed = true;
            InputMethodManager ime = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (ime != null) {
                ime.showInputMethodPicker();
            }
        }
    };

    @Override
    public View onCreateInputView() {
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        LayoutInflater inflater = getLayoutInflater();

        translationPanelView = inflater.inflate(R.layout.layout_translation_panel, mainLayout, false);
        translationPanelView.setVisibility(View.GONE);

        translationUiManager = new TranslationUiManager(this, translationPanelView, new TranslationUiManager.TranslationListener() {
            @Override
            public void onTranslationResult(String translatedText) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    if (lastSentTranslationLength > 0) {
                        ic.deleteSurroundingText(lastSentTranslationLength, 0);
                    }
                    ic.commitText(translatedText, 1);
                    lastSentTranslationLength = translatedText.length();
                    lastSentTranslation = translatedText;

                    if (clearTranslationOnNextResult) {
                        translationBuffer.setLength(0);
                        translationUiManager.updateInputPreview("");
                        lastSentTranslationLength = 0;
                        lastSentTranslation = "";
                        clearTranslationOnNextResult = false;

                        if (toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);
                        updateCandidates("");
                    }
                }
            }

            @Override
            public void onCloseTranslation() {
                toggleTranslationMode();
            }

            @Override
            public void onPasteText(String text) {
                if (text != null) {
                    if (translationBuffer.length() == 0) {
                        lastSentTranslationLength = 0;
                        lastSentTranslation = "";
                    }
                    translationBuffer.append(text);
                    translationUiManager.updateInputPreview(translationBuffer.toString());
                    translationUiManager.performTranslation(translationBuffer.toString());
                }
            }

            @Override
            public void onClearTranslation() {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    boolean hasSelection = false;
                    CharSequence selectedText = ic.getSelectedText(0);
                    if (selectedText != null && selectedText.length() > 0) {
                        hasSelection = true;
                    } else {
                        android.view.inputmethod.ExtractedText et = ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
                        if (et != null && et.selectionStart != et.selectionEnd) {
                            hasSelection = true;
                        }
                    }

                    if (hasSelection) {
                        ic.commitText("", 1);
                    } else if (lastSentTranslationLength > 0) {
                        ic.deleteSurroundingText(lastSentTranslationLength, 0);
                    }
                }
                translationBuffer.setLength(0);
                lastSentTranslationLength = 0;
                lastSentTranslation = "";
                translationUiManager.updateInputPreview("");

                if (toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);
                updateCandidates("");

                toggleTranslationMode();
            }
        });
        mainLayout.addView(translationPanelView);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        voiceInputManager = new VoiceInputManager(this, new VoiceInputManager.VoiceInputListener() {
            @Override
            public void onTranscriptionResult(String text) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null && text != null && !text.isEmpty()) {
                    ic.commitText(text, 1);
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(BubbleKeyboardService.this, "Voice Input Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });

        candidateView = inflater.inflate(R.layout.candidate_view, mainLayout, false);
        candidateContainer = candidateView.findViewById(R.id.candidate_container);
        toolbarContainer = candidateView.findViewById(R.id.toolbar_container);

        // Permanent 4-Square Button setup
        btnGridMenu = candidateView.findViewById(R.id.btn_grid_menu);
        activeToolsContainer = candidateView.findViewById(R.id.active_tools_container);

        if (btnGridMenu != null) {
            btnGridMenu.setOnClickListener(v -> toggleGridMenu());
        }

        buildActiveToolbar();
        mainLayout.addView(candidateView);

        // Standard QWERTY / Symbols Keys layout
        kv = (KeyboardView) inflater.inflate(R.layout.layout_real_keyboard, mainLayout, false);
        keyboardQwerty = new Keyboard(this, R.xml.qwerty);
        keyboardSymbols = new Keyboard(this, R.xml.symbols);
        kv.setKeyboard(keyboardQwerty);
        kv.setOnKeyboardActionListener(this);
        kv.setPreviewEnabled(false); 
        mainLayout.addView(kv);

        // Inflate Gboard-style customization layout, invisible by default
        gridMenuView = inflater.inflate(R.layout.layout_keyboard_grid_menu, mainLayout, false);
        gridMenuView.setVisibility(View.GONE);
        gridMenuRecycler = gridMenuView.findViewById(R.id.grid_menu_recycler);
        gridMenuRecycler.setLayoutManager(new GridLayoutManager(this, 4));
        gridAdapter = new GridMenuAdapter();
        gridMenuRecycler.setAdapter(gridAdapter);
        mainLayout.addView(gridMenuView);

        emojiPaletteView = inflater.inflate(R.layout.layout_emoji_palette, mainLayout, false);
        emojiPaletteView.setVisibility(View.GONE);
        EmojiUtils.setupEmojiGrid(this, emojiPaletteView, new EmojiUtils.EmojiListener() {
            @Override
            public void onEmojiClick(String emoji) {
                getCurrentInputConnection().commitText(emoji, 1);
            }
        });
        setupEmojiControlButtons();
        mainLayout.addView(emojiPaletteView);

        clipboardPaletteView = inflater.inflate(R.layout.layout_clipboard_palette, mainLayout, false);
        clipboardPaletteView.setVisibility(View.GONE);

        clipboardUiManager = new ClipboardUiManager(this, clipboardPaletteView, new ClipboardUiManager.ClipboardListener() {
            @Override
            public void onPasteItem(String text) {
                if (isTranslationMode) {
                    if (translationBuffer.length() == 0) {
                        lastSentTranslationLength = 0;
                        lastSentTranslation = "";
                    }
                    translationBuffer.append(text);
                    translationUiManager.updateInputPreview(translationBuffer.toString());
                    translationUiManager.performTranslation(translationBuffer.toString());
                    toggleClipboardPalette(); 
                } else if (isDirectTranslateEnabled) {
                    directBuffer.append(text);
                    performDirectTranslation(directBuffer.toString());
                    toggleClipboardPalette();
                } else {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(text, 1);
                        PredictionEngine.getInstance(BubbleKeyboardService.this).learnWord(text);
                        lastCommittedWord = text.trim(); 
                    }
                    toggleClipboardPalette(); 
                    updateCandidates("");
                }
            }

            @Override
            public void onCloseClipboard() {
                toggleClipboardPalette();
            }
        });
        mainLayout.addView(clipboardPaletteView);

        setupToolbarDragListeners();

        return mainLayout;
    }

    private void setupToolbarButtons() {
        // Handled dynamically inside buildActiveToolbar()
    }

    /**
     * Programmatically reconstructs active tool buttons on candidate view
     * based on user's persisted preferences.
     */
    private void buildActiveToolbar() {
        if (activeToolsContainer == null) return;
        activeToolsContainer.removeAllViews();

        String activeString = sharedPreferences.getString(KEY_ACTIVE_TOOLS, DEFAULT_ACTIVE_TOOLS);
        List<String> activeIds = new ArrayList<>(Arrays.asList(activeString.split(",")));

        int itemWidth = (int) (45 * getResources().getDisplayMetrics().density);

        for (final String toolId : activeIds) {
            if (toolId.trim().isEmpty()) continue;

            final ImageButton btn = new ImageButton(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.MATCH_PARENT);
            btn.setLayoutParams(params);
            btn.setImageResource(getToolDrawableId(toolId));
            btn.setBackgroundResource(android.R.drawable.list_selector_background);
            btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.setPadding(10, 10, 10, 10);
            btn.setContentDescription(getString(getToolLabelId(toolId)));

            // Handle dynamic active state tint colors
            if ("autosave".equals(toolId) && isAutoSaveEnabled) {
                btn.setColorFilter(Color.parseColor("#2196F3"), PorterDuff.Mode.SRC_IN);
            } else if ("direct_translate".equals(toolId) && isDirectTranslateEnabled) {
                btn.setColorFilter(Color.parseColor("#2196F3"), PorterDuff.Mode.SRC_IN);
            } else {
                btn.setColorFilter(Color.parseColor("#5F6368"), PorterDuff.Mode.SRC_IN);
            }

            btn.setOnClickListener(v -> handleToolClick(toolId));

            // Long click initiates dynamic Gboard-style drag-and-drop customization mode
            btn.setOnLongClickListener(v -> {
                if (!isGridMenuVisible) {
                    toggleGridMenu(); // Open grid customizable panel automatically
                }
                ClipData data = ClipData.newPlainText("tool_id", toolId);
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    v.startDragAndDrop(data, shadowBuilder, v, 0);
                } else {
                    v.startDrag(data, shadowBuilder, v, 0);
                }
                return true;
            });

            activeToolsContainer.addView(btn);
        }
    }

    private void handleToolClick(String toolId) {
        if ("clipboard".equals(toolId)) {
            toggleClipboardPalette();
        } else if ("keyboard_switch".equals(toolId)) {
            InputMethodManager ime = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (ime != null) ime.showInputMethodPicker();
        } else if ("translate".equals(toolId)) {
            toggleTranslationMode();
        } else if ("direct_translate".equals(toolId)) {
            toggleDirectTranslationMode();
        } else if ("bubble_launcher".equals(toolId)) {
            Intent intent = new Intent(this, FloatingTranslatorService.class);
            intent.setAction("ACTION_SHOW_BUBBLE");
            startService(intent);
        } else if ("ocr_copy".equals(toolId)) {
            requestHideSelf(0);
            Intent intent = new Intent(this, FloatingTranslatorService.class);
            intent.setAction("ACTION_TRIGGER_COPY_ONLY");
            startService(intent);
        } else if ("scanner".equals(toolId)) {
            requestHideSelf(0);
            ScannerUiManager.getInstance(this).show();
        } else if ("autosave".equals(toolId)) {
            toggleAutoSaveMode();
            buildActiveToolbar(); // Refresh toolbar states to update auto save active tint
        } else if ("settings".equals(toolId)) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else if ("mic".equals(toolId)) {
            triggerVoiceInput();
        }
    }

    private void triggerVoiceInput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show();
            // Launch the Main Settings redirection
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            if (voiceInputManager != null) {
                voiceInputManager.toggleListening();
            }
        }
    }

    private int getToolDrawableId(String toolId) {
        switch (toolId) {
            case "clipboard": return R.drawable.content_paste_24px;
            case "keyboard_switch": return R.drawable.change_circle_24px;
            case "translate": return R.drawable.g_translate_24px;
            case "direct_translate": return R.drawable.electric_bolt_24px;
            case "bubble_launcher": return R.drawable.bubble_24px;
            case "ocr_copy": return R.drawable.select_all_24px;
            case "scanner": return R.drawable.document_scanner_24px;
            case "autosave": return R.drawable.save_24px;
            case "settings": return R.drawable.settings_24px;
            case "mic": return R.drawable.mic_24px;
            default: return R.drawable.settings_24px;
        }
    }

    private int getToolLabelId(String toolId) {
        switch (toolId) {
            case "clipboard": return R.string.label_clipboard;
            case "keyboard_switch": return R.string.label_keyboard_switch;
            case "translate": return R.string.label_translate;
            case "direct_translate": return R.string.label_direct_translate;
            case "bubble_launcher": return R.string.label_bubble_launcher;
            case "ocr_copy": return R.string.label_ocr_copy;
            case "scanner": return R.string.label_scanner;
            case "autosave": return R.string.label_autosave;
            case "mic": return R.string.label_microphone;
            default: return R.string.settings_title;
        }
    }

    private void toggleGridMenu() {
        if (gridMenuView.getVisibility() == View.GONE) {
            kv.setVisibility(View.GONE);
            emojiPaletteView.setVisibility(View.GONE);
            clipboardPaletteView.setVisibility(View.GONE);
            translationPanelView.setVisibility(View.GONE);
            isTranslationMode = false;

            gridMenuView.setVisibility(View.VISIBLE);
            isGridMenuVisible = true;
            btnGridMenu.setColorFilter(Color.parseColor("#2196F3"), PorterDuff.Mode.SRC_IN);

            // Reload available tools lists
            loadGridAdapterData();
        } else {
            resetToStandardKeyboard();
        }
    }

    private void loadGridAdapterData() {
        String inactiveString = sharedPreferences.getString(KEY_INACTIVE_TOOLS, DEFAULT_INACTIVE_TOOLS);
        List<String> inactiveIds = new ArrayList<>(Arrays.asList(inactiveString.split(",")));
        gridAdapter.setData(inactiveIds);
    }

    private void setupToolbarDragListeners() {
        // Drag listener for candidate toolbar to intercept dropped tools from grid menu
        activeToolsContainer.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);

                    case DragEvent.ACTION_DROP:
                        ClipData.Item item = event.getClipData().getItemAt(0);
                        String draggedToolId = item.getText().toString();

                        String activeString = sharedPreferences.getString(KEY_ACTIVE_TOOLS, DEFAULT_ACTIVE_TOOLS);
                        List<String> activeIds = new ArrayList<>(Arrays.asList(activeString.split(",")));

                        String inactiveString = sharedPreferences.getString(KEY_INACTIVE_TOOLS, DEFAULT_INACTIVE_TOOLS);
                        List<String> inactiveIds = new ArrayList<>(Arrays.asList(inactiveString.split(",")));

                        if (inactiveIds.contains(draggedToolId)) {
                            inactiveIds.remove(draggedToolId);
                            activeIds.add(draggedToolId);

                            // Save layouts
                            sharedPreferences.edit()
                                .putString(KEY_ACTIVE_TOOLS, TextUtils.join(",", activeIds))
                                .putString(KEY_INACTIVE_TOOLS, TextUtils.join(",", inactiveIds))
                                .apply();

                            buildActiveToolbar();
                            loadGridAdapterData();
                        }
                        return true;

                    default:
                        return true;
                }
            }
        });

        // Drag listener on the grid container to intercept dropped active tools pulled down from toolbar
        gridMenuView.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent event) {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);

                    case DragEvent.ACTION_DROP:
                        ClipData.Item item = event.getClipData().getItemAt(0);
                        String draggedToolId = item.getText().toString();

                        String activeString = sharedPreferences.getString(KEY_ACTIVE_TOOLS, DEFAULT_ACTIVE_TOOLS);
                        List<String> activeIds = new ArrayList<>(Arrays.asList(activeString.split(",")));

                        String inactiveString = sharedPreferences.getString(KEY_INACTIVE_TOOLS, DEFAULT_INACTIVE_TOOLS);
                        List<String> inactiveIds = new ArrayList<>(Arrays.asList(inactiveString.split(",")));

                        // Do not allow empty candidate view toolbar, ensure at least one active tool remains
                        if (activeIds.contains(draggedToolId) && activeIds.size() > 1) {
                            activeIds.remove(draggedToolId);
                            inactiveIds.add(draggedToolId);

                            // Save layouts
                            sharedPreferences.edit()
                                .putString(KEY_ACTIVE_TOOLS, TextUtils.join(",", activeIds))
                                .putString(KEY_INACTIVE_TOOLS, TextUtils.join(",", inactiveIds))
                                .apply();

                            buildActiveToolbar();
                            loadGridAdapterData();
                        } else if (activeIds.size() <= 1) {
                            Toast.makeText(BubbleKeyboardService.this, "At least one tool must remain in active view", Toast.LENGTH_SHORT).show();
                        }
                        return true;

                    default:
                        return true;
                }
            }
        });
    }

    // --- NEW: Toggle Auto Save Mode ---
    private void toggleAutoSaveMode() {
        isAutoSaveEnabled = !isAutoSaveEnabled;
        if (isAutoSaveEnabled) {
            // Start a new tracking session
            autoSaveBuffer.setLength(0);
            isTypingNewEntry = true;
            Toast.makeText(this, "Real-Time Saving ON", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Real-Time Saving OFF", Toast.LENGTH_SHORT).show();
        }
    }

    // --- NEW: Update Clipboard in Real-Time (Safety Logic) ---
    private void updateAutoSaveClipboard() {
        String currentText = autoSaveBuffer.toString();

        // 1. If we are editing an existing line, delete the old version first
        if (!isTypingNewEntry) {
            // Assume the previous state was just 1 char shorter
            if (currentText.length() > 0) {
                 String prevText = currentText.substring(0, currentText.length() - 1);
                 ClipboardManagerHelper.getInstance(this).deleteItem(prevText);
            }
        }

        // 2. Add the current text
        if (!currentText.isEmpty()) {
            ClipboardManagerHelper.getInstance(this).addClip(currentText);
        }

        // 3. Mark as "Updating" for next key
        isTypingNewEntry = false;
    }

    private void showDirectLanguagePopup(View v) {
        Context wrapper = new ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog);
        AlertDialog.Builder builder = new AlertDialog.Builder(wrapper);
        builder.setTitle("Select Target Language");

        builder.setItems(LanguageUtils.LANGUAGE_NAMES, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                directTargetLangCode = LanguageUtils.getCode(which);
                Toast.makeText(BubbleKeyboardService.this, "Target: " + LanguageUtils.LANGUAGE_NAMES[which], Toast.LENGTH_SHORT).show();

                if (isDirectTranslateEnabled) {
                    toggleDirectTranslationMode();
                    toggleDirectTranslationMode();
                }

                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                lp.type = WindowManager.LayoutParams.TYPE_PHONE;
            }

            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setAttributes(lp);
        }
        dialog.show();
    }

    private void toggleDirectTranslationMode() {
        isDirectTranslateEnabled = !isDirectTranslateEnabled;
        if (isDirectTranslateEnabled) {
            Toast.makeText(this, "Live Translation ON (Auto -> " + directTargetLangCode + ")", Toast.LENGTH_SHORT).show();
            directBuffer.setLength(0);
            lastDirectOutputLength = 0;
        } else {
            Toast.makeText(this, "Live Translation OFF", Toast.LENGTH_SHORT).show();
        }
    }

    private void performDirectTranslation(final String text) {
        if (text.trim().isEmpty()) return;
        if (directTranslateRunnable != null) directHandler.removeCallbacks(directTranslateRunnable);

        directTranslateRunnable = new Runnable() {
            @Override
            public void run() {
                bgExecutor.execute(() -> {
                    final String result = TranslateApi.translate("auto", directTargetLangCode, text);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (result != null) {
                            InputConnection ic = getCurrentInputConnection();
                            if (ic != null) {
                                if (lastDirectOutputLength > 0) {
                                    ic.deleteSurroundingText(lastDirectOutputLength, 0);
                                }
                                ic.commitText(result, 1);
                                lastDirectOutputLength = result.length();
                            }
                        }
                    });
                });
            }
        };
        directHandler.postDelayed(directTranslateRunnable, 500);
    }

    private void setupEmojiControlButtons() {
        View btnBack = emojiPaletteView.findViewById(R.id.btn_back_to_abc);
        if (btnBack != null) btnBack.setOnClickListener(v -> toggleEmojiPalette());

        View btnDel = emojiPaletteView.findViewById(R.id.btn_emoji_backspace);
        if (btnDel != null) btnDel.setOnClickListener(v -> handleBackspace());
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        // Icon Visibility
        if (isTranslationMode) {
            if (Character.isLetterOrDigit(primaryCode)) {
                if (toolbarContainer != null) toolbarContainer.setVisibility(View.GONE);
            } else if (primaryCode == 32 || primaryCode == 46 || translationBuffer.length() == 0) { 
                if (toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);
            }
        } else if (!isDirectTranslateEnabled) {
            if (Character.isLetterOrDigit(primaryCode)) {
                if (toolbarContainer != null) toolbarContainer.setVisibility(View.GONE);
            } else if (primaryCode == 32 || primaryCode == 46 || currentWord.length() == 0) { 
                if (toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);
            }
        } else {
            if (toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);
        }

        // --- DELETE KEY LOGIC ---
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            // Handle Real-Time Deletion for Auto-Save
            if (isAutoSaveEnabled && autoSaveBuffer.length() > 0) {
                boolean hasSelection = false;
                CharSequence selectedText = ic.getSelectedText(0);
                if (selectedText != null && selectedText.length() > 0) {
                    hasSelection = true;
                } else {
                    android.view.inputmethod.ExtractedText et = ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
                    if (et != null && et.selectionStart != et.selectionEnd) {
                        hasSelection = true;
                    }
                }

                if (hasSelection) {
                    ClipboardManagerHelper.getInstance(this).deleteItem(autoSaveBuffer.toString());
                    autoSaveBuffer.setLength(0);
                    isTypingNewEntry = true;
                } else {
                    ClipboardManagerHelper.getInstance(this).deleteItem(autoSaveBuffer.toString());
                    autoSaveBuffer.deleteCharAt(autoSaveBuffer.length() - 1);
                    if (autoSaveBuffer.length() > 0) {
                        ClipboardManagerHelper.getInstance(this).addClip(autoSaveBuffer.toString());
                    } else {
                        isTypingNewEntry = true;
                    }
                }
            }

            if (isTranslationMode) {
                if (translationBuffer.length() > 0) {
                    translationBuffer.deleteCharAt(translationBuffer.length() - 1);
                    translationUiManager.updateInputPreview(translationBuffer.toString());
                    updateCandidates(getLastWord(translationBuffer.toString()));

                    if (translationBuffer.length() == 0) {
                        ic.deleteSurroundingText(lastSentTranslationLength, 0);
                        lastSentTranslationLength = 0;
                        lastSentTranslation = "";
                    } 
                } else {
                    handleBackspace();
                }
            } else if (isDirectTranslateEnabled) {
                if (directBuffer.length() > 0) {
                    directBuffer.deleteCharAt(directBuffer.length() - 1);
                    performDirectTranslation(directBuffer.toString());
                    if (directBuffer.length() == 0) {
                        ic.deleteSurroundingText(lastDirectOutputLength, 0);
                        lastDirectOutputLength = 0;
                    }
                } else {
                    handleBackspace();
                }
            } else {
                if (justAutoCorrected) {
                    int lengthToDelete = lastCorrectedWord.length() + 1;
                    ic.deleteSurroundingText(lengthToDelete, 0);
                    ic.commitText(lastOriginalWord, 1);
                    currentWord.setLength(0);
                    currentWord.append(lastOriginalWord);
                    ignoreNextCorrection = true;
                    justAutoCorrected = false;
                } else {
                    handleBackspace();
                }
            }
            return;
        }

        if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            isCaps = !isCaps;
            keyboardQwerty.setShifted(isCaps);
            kv.invalidateAllKeys();
            return;
        }

        if (primaryCode == Keyboard.KEYCODE_DONE) { 
            // Enter Key finalized the text - Start new entry
            if (isAutoSaveEnabled) {
                isTypingNewEntry = true;
                autoSaveBuffer.setLength(0);
            }

            if (isTranslationMode) {
                String textToTranslate = translationBuffer.toString();
                translationBuffer.setLength(0);
                translationUiManager.updateInputPreview("");

                if (lastSentTranslationLength > 0 && !textToTranslate.isEmpty()) {
                    lastSentTranslationLength = 0;
                    lastSentTranslation = "";
                    clearTranslationOnNextResult = false;
                } else {
                    clearTranslationOnNextResult = true;
                    translationUiManager.performTranslation(textToTranslate);
                }

                if (toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);
                updateCandidates("");
            } else if (isDirectTranslateEnabled) {
                directBuffer.setLength(0);
                lastDirectOutputLength = 0;
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            } else {
                PredictionEngine.getInstance(this).learnWord(currentWord.toString());
                lastCommittedWord = currentWord.toString();
                currentWord.setLength(0); 
                updateCandidates("");
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            }
            return;
        }

        if (primaryCode == -2) { 
            if (kv.getKeyboard() == keyboardQwerty) kv.setKeyboard(keyboardSymbols);
            else kv.setKeyboard(keyboardQwerty);
            return;
        }

        if (primaryCode == -100) { toggleEmojiPalette(); return; }

        if (primaryCode == -10) { 
            requestHideSelf(0);
            Intent intent = new Intent(BubbleKeyboardService.this, TwoLineOverlayService.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startService(intent);
            return;
        }

        if (primaryCode == 32) { 
            if (!isSpaceLongPressed) {
                // Space also saves immediately
                if (isAutoSaveEnabled) {
                    autoSaveBuffer.append(" ");
                    updateAutoSaveClipboard();
                }

                if (isTranslationMode) {
                    if (translationBuffer.length() == 0) {
                        lastSentTranslationLength = 0;
                        lastSentTranslation = "";
                    }
                    translationBuffer.append(" ");
                    translationUiManager.updateInputPreview(translationBuffer.toString());
                    updateCandidates("");
                } else if (isDirectTranslateEnabled) {
                    directBuffer.append(" ");
                    performDirectTranslation(directBuffer.toString());
                } else {
                    String typo = currentWord.toString();
                    boolean correctionApplied = false;

                    if (!ignoreNextCorrection && typo.length() > 1) {
                        String correction = PredictionEngine.getInstance(this).getBestMatch(typo);
                        if (correction != null && !correction.equals(typo)) {
                            lastOriginalWord = typo;
                            lastCorrectedWord = correction;
                            justAutoCorrected = true;
                            ic.deleteSurroundingText(typo.length(), 0);
                            ic.commitText(correction, 1);
                            currentWord.setLength(0);
                            currentWord.append(correction);
                            correctionApplied = true;
                        }
                    } 
                    if (!correctionApplied) {
                        justAutoCorrected = false;
                        ignoreNextCorrection = false;
                    }
                    ic.commitText(" ", 1);
                    String justTyped = currentWord.toString();
                    PredictionEngine.getInstance(this).learnWord(justTyped);
                    if (lastCommittedWord != null && !lastCommittedWord.isEmpty()) {
                        PredictionEngine.getInstance(this).learnNextWord(lastCommittedWord, justTyped);
                    }
                    lastCommittedWord = justTyped;
                    currentWord.setLength(0); 
                    updateCandidates("");
                }
            }
            return;
        }

        char code = (char) primaryCode;
        if (Character.isLetter(code) && isCaps) {
            code = Character.toUpperCase(code);
        }

        // Real-Time Character Saving
        if (isAutoSaveEnabled) {
            autoSaveBuffer.append(code);
            updateAutoSaveClipboard();
        }

        if (isTranslationMode) {
            if (translationBuffer.length() == 0) {
                lastSentTranslationLength = 0;
                lastSentTranslation = "";
            }
            translationBuffer.append(code);
            translationUiManager.updateInputPreview(translationBuffer.toString());
            updateCandidates(getLastWord(translationBuffer.toString()));

            if (toolbarContainer != null) toolbarContainer.setVisibility(View.GONE);
        } else if (isDirectTranslateEnabled) {
            directBuffer.append(code);
            performDirectTranslation(directBuffer.toString());
        } else {
            ic.commitText(String.valueOf(code), 1);
            justAutoCorrected = false; 
            if (Character.isLetterOrDigit(code)) {
                currentWord.append(code);
                updateCandidates(currentWord.toString());
            } else {
                PredictionEngine.getInstance(this).learnWord(currentWord.toString());
                lastCommittedWord = currentWord.toString();
                currentWord.setLength(0);
                updateCandidates("");
            }
        }
    }

    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            boolean selectionDeleted = false;
            CharSequence selectedText = ic.getSelectedText(0);

            if (selectedText != null && selectedText.length() > 0) {
                ic.commitText("", 1);
                selectionDeleted = true;
            } else {
                android.view.inputmethod.ExtractedText et = ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0);
                if (et != null && et.selectionStart != et.selectionEnd) {
                    ic.commitText("", 1);
                    selectionDeleted = true;
                }
            }

            if (selectionDeleted) {
                currentWord.setLength(0);
                if (toolbarContainer != null) {
                    toolbarContainer.setVisibility(View.VISIBLE);
                }
                updateCandidates("");
                return;
            }

            ic.deleteSurroundingText(1, 0);
            if (currentWord.length() > 0) {
                currentWord.deleteCharAt(currentWord.length() - 1);
                updateCandidates(currentWord.toString());
            } else {
                if (toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);
                updateCandidates("");
            }
        }
    }

    private void toggleEmojiPalette() {
        if (emojiPaletteView.getVisibility() == View.GONE) {
            kv.setVisibility(View.GONE);
            candidateView.setVisibility(View.GONE);
            clipboardPaletteView.setVisibility(View.GONE);
            translationPanelView.setVisibility(View.GONE);
            isTranslationMode = false;
            if (isGridMenuVisible) {
                gridMenuView.setVisibility(View.GONE);
                isGridMenuVisible = false;
                btnGridMenu.clearColorFilter();
            }
            emojiPaletteView.setVisibility(View.VISIBLE);
        } else {
            resetToStandardKeyboard();
        }
    }

    private void toggleClipboardPalette() {
        if (clipboardPaletteView.getVisibility() == View.GONE) {
            kv.setVisibility(View.GONE);
            if (isTranslationMode) {
                translationPanelView.setVisibility(View.VISIBLE);
            } else {
                translationPanelView.setVisibility(View.GONE);
            }
            candidateView.setVisibility(View.GONE);
            emojiPaletteView.setVisibility(View.GONE);
            if (isGridMenuVisible) {
                gridMenuView.setVisibility(View.GONE);
                isGridMenuVisible = false;
                btnGridMenu.clearColorFilter();
            }
            clipboardPaletteView.setVisibility(View.VISIBLE);
            if (clipboardUiManager != null) clipboardUiManager.reloadHistory();
        } else {
            clipboardPaletteView.setVisibility(View.GONE);
            if (isTranslationMode) {
                translationPanelView.setVisibility(View.VISIBLE);
                candidateView.setVisibility(View.VISIBLE); 
                kv.setVisibility(View.VISIBLE);
            } else {
                resetToStandardKeyboard();
            }
        }
    }

    private void toggleTranslationMode() {
        if (translationPanelView.getVisibility() == View.GONE) {
            candidateView.setVisibility(View.VISIBLE); 
            clipboardPaletteView.setVisibility(View.GONE);
            emojiPaletteView.setVisibility(View.GONE);
            if (isGridMenuVisible) {
                gridMenuView.setVisibility(View.GONE);
                isGridMenuVisible = false;
                btnGridMenu.clearColorFilter();
            }
            translationPanelView.setVisibility(View.VISIBLE);
            kv.setVisibility(View.VISIBLE);
            isTranslationMode = true;
            translationBuffer.setLength(0); 
            lastSentTranslationLength = 0; 
            lastSentTranslation = "";
            translationUiManager.updateInputPreview("");
        } else {
            resetToStandardKeyboard();
        }
    }

    private void resetToStandardKeyboard() {
        emojiPaletteView.setVisibility(View.GONE);
        clipboardPaletteView.setVisibility(View.GONE);
        translationPanelView.setVisibility(View.GONE);
        if (isGridMenuVisible) {
            gridMenuView.setVisibility(View.GONE);
            isGridMenuVisible = false;
            btnGridMenu.clearColorFilter();
        }
        candidateView.setVisibility(View.VISIBLE);
        kv.setVisibility(View.VISIBLE);
        isTranslationMode = false;
    }

    @Override
    public void onPress(int primaryCode) {
        if (primaryCode == 32) {
            isSpaceLongPressed = false; 
            longPressHandler.postDelayed(spaceLongPressRunnable, LONG_PRESS_DELAY);
        }
    }

    @Override
    public void onRelease(int primaryCode) {
        if (primaryCode == 32) {
            longPressHandler.removeCallbacks(spaceLongPressRunnable);
        }
    }

    private String getLastWord(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] parts = text.split("\\s+");
        if (parts.length > 0) return parts[parts.length - 1];
        return "";
    }

    private void updateCandidates(String wordBeingTyped) {
        if (candidateContainer == null) return;

        candidateContainer.removeAllViews();
        List<String> suggestions;

        if (wordBeingTyped.isEmpty()) {
            if (lastCommittedWord != null) {
                suggestions = PredictionEngine.getInstance(this).getNextWordSuggestions(lastCommittedWord);
            } else {
                suggestions = PredictionEngine.getInstance(this).getSuggestions(""); 
            }
        } else {
            suggestions = PredictionEngine.getInstance(this).getSuggestions(wordBeingTyped);
        }

        for (final String word : suggestions) {
            TextView tv = new TextView(this);
            tv.setText(word);
            tv.setTextSize(18);
            tv.setPadding(40, 20, 40, 20);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);

            tv.setOnClickListener(v -> {
                if (isTranslationMode) {
                    if (translationBuffer.length() == 0) {
                        lastSentTranslationLength = 0;
                        lastSentTranslation = "";
                    }
                    String currentBuffer = translationBuffer.toString();
                    int lastSpace = currentBuffer.lastIndexOf(" ");
                    if (lastSpace != -1) {
                        translationBuffer.setLength(lastSpace + 1);
                    } else {
                        translationBuffer.setLength(0);
                    }
                    translationBuffer.append(word).append(" ");
                    translationUiManager.updateInputPreview(translationBuffer.toString());
                    translationUiManager.performTranslation(translationBuffer.toString());
                    updateCandidates("");
                } else {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        if (currentWord.length() > 0) {
                            ic.deleteSurroundingText(currentWord.length(), 0);
                        }
                        ic.commitText(word + " ", 1);
                        PredictionEngine.getInstance(this).learnWord(word);
                        if (lastCommittedWord != null) {
                            PredictionEngine.getInstance(this).learnNextWord(lastCommittedWord, word);
                        }
                        lastCommittedWord = word;
                        currentWord.setLength(0);
                        updateCandidates("");
                    }
                }
            });
            candidateContainer.addView(tv);
        }
    }

    @Override public void onText(CharSequence text) {}
    @Override public void swipeLeft() {}
    @Override public void swipeRight() {}
    @Override public void swipeDown() {}
    @Override public void swipeUp() {}

    // --- NEW: ADAPTER FOR GRID CUSTOMIZATION ---
    private class GridMenuAdapter extends RecyclerView.Adapter<GridMenuAdapter.ViewHolder> {
        private List<String> dataList = new ArrayList<>();

        public void setData(List<String> data) {
            this.dataList = new ArrayList<>(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid_tool, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final String toolId = dataList.get(position);
            holder.textLabel.setText(getToolLabelId(toolId));
            holder.imgIcon.setImageResource(getToolDrawableId(toolId));
            holder.imgIcon.setColorFilter(Color.parseColor("#5F6368"), PorterDuff.Mode.SRC_IN);

            holder.itemView.setOnClickListener(v -> handleToolClick(toolId));

            // Long click on item in customize grid triggers drag-to-toolbar action
            holder.itemView.setOnLongClickListener(v -> {
                ClipData data = ClipData.newPlainText("tool_id", toolId);
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(holder.imgIcon);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    v.startDragAndDrop(data, shadowBuilder, v, 0);
                } else {
                    v.startDrag(data, shadowBuilder, v, 0);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.ImageView imgIcon;
            TextView textLabel;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgIcon = itemView.findViewById(R.id.tool_icon);
                textLabel = itemView.findViewById(R.id.tool_label);
            }
        }
    }
}
