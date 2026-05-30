package com.app.bubble;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

// NEW: AdMob Imports
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends Activity {

    // Constants for SharedPreferences to ensure consistency.
    public static final String PREFS_NAME = "BubbleTranslatorPrefs";
    public static final String KEY_TIMER_DURATION = "CropTimerDuration";
    public static final String KEY_API_KEY = "GeminiApiKey"; // Constant for the API key
    public static final String KEY_ACTIVE_MODEL = "ActiveGeminiModel"; // New constant for selected model
    public static final String KEY_MODEL_LIST = "GeminiModelList"; // New constant for the cached list

    private Spinner cropTimerSpinner;
    private EditText apiKeyEditText; // EditText field for API key
    private Spinner geminiModelSpinner; // Spinner for model selection
    private ImageButton btnFetchModels; // Button to trigger online discovery
    private SharedPreferences sharedPreferences;

    private List<String> modelList = new ArrayList<>();
    private ArrayAdapter<String> modelAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cropTimerSpinner = findViewById(R.id.crop_timer_spinner);
        apiKeyEditText = findViewById(R.id.api_key_edit_text); // Find the API key field
        geminiModelSpinner = findViewById(R.id.gemini_model_spinner); // Find the model selector
        btnFetchModels = findViewById(R.id.btn_fetch_models); // Find the fetch button

        // --- Setup for Crop Timer Spinner ---
        ArrayAdapter<CharSequence> cropAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.crop_timer_options,
            android.R.layout.simple_spinner_item
        );
        cropAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cropTimerSpinner.setAdapter(cropAdapter);

        // Set a listener to save the preference when the user selects a new item.
        cropTimerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveTimerSetting(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing.
            }
        });

        // --- Setup for Gemini Model Spinner ---
        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modelList);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        geminiModelSpinner.setAdapter(modelAdapter);

        geminiModelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < modelList.size()) {
                    saveActiveModel(modelList.get(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing.
            }
        });

        // --- Setup for Fetch Models Button ---
        btnFetchModels.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveApiKey(); // Save current text in field first
                triggerModelFetch();
            }
        });

        // Load the saved preferences and set the views to the correct state.
        loadCurrentSettings();

        // NEW: Load the AdMob Banner Ad in the footer
        AdView mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save the API key when the user leaves the activity.
        saveApiKey();
    }

    private void triggerModelFetch() {
        String apiKey = apiKeyEditText.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please enter an API Key first", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Fetching available models...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<String> fetched = GeminiApi.fetchModels(apiKey);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (fetched != null && !fetched.isEmpty()) {
                                modelList.clear();
                                modelList.addAll(fetched);
                                modelAdapter.notifyDataSetChanged();

                                // Serialize list as comma-separated string to SharedPreferences
                                String listString = TextUtils.join(",", fetched);
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString(KEY_MODEL_LIST, listString);

                                // If currently active model is not in the fresh fetched list, default to first item
                                String activeModel = sharedPreferences.getString(KEY_ACTIVE_MODEL, "gemini-2.0-flash");
                                if (!fetched.contains(activeModel)) {
                                    activeModel = fetched.get(0);
                                    editor.putString(KEY_ACTIVE_MODEL, activeModel);
                                }
                                editor.apply();

                                // Update selection inside drop-down
                                int index = modelList.indexOf(activeModel);
                                if (index != -1) {
                                    geminiModelSpinner.setSelection(index);
                                }

                                Toast.makeText(SettingsActivity.this, "Models updated successfully!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(SettingsActivity.this, "No valid models found.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (final Exception e) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(SettingsActivity.this, "Fetch failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void loadCurrentSettings() {
        // Load the saved duration for the crop timer.
        long savedDuration = sharedPreferences.getLong(KEY_TIMER_DURATION, 5000L);
        int position = 0;
        if (savedDuration == 10000L) {
            position = 1;
        } else if (savedDuration == 15000L) {
            position = 2;
        } else if (savedDuration == 20000L) {
            position = 3;
        }
        cropTimerSpinner.setSelection(position);

        // Load the saved API key.
        String savedApiKey = sharedPreferences.getString(KEY_API_KEY, "");
        apiKeyEditText.setText(savedApiKey);

        // Load saved model list
        String savedModels = sharedPreferences.getString(KEY_MODEL_LIST, "gemini-2.0-flash");
        String[] parts = savedModels.split(",");
        modelList.clear();
        for (String p : parts) {
            if (!p.trim().isEmpty()) {
                modelList.add(p.trim());
            }
        }
        if (modelList.isEmpty()) {
            modelList.add("gemini-2.0-flash");
        }
        modelAdapter.notifyDataSetChanged();

        // Load and pre-select the active model
        String activeModel = sharedPreferences.getString(KEY_ACTIVE_MODEL, "gemini-2.0-flash");
        int modelIndex = modelList.indexOf(activeModel);
        if (modelIndex != -1) {
            geminiModelSpinner.setSelection(modelIndex);
        } else {
            geminiModelSpinner.setSelection(0);
        }
    }

    private void saveTimerSetting(int position) {
        long durationToSave;
        switch (position) {
            case 1:
                durationToSave = 10000L; // 10 seconds
                break;
            case 2:
                durationToSave = 15000L; // 15 seconds
                break;
            case 3:
                durationToSave = 20000L; // 20 seconds
                break;
            case 0:
            default:
                durationToSave = 5000L;  // 5 seconds
                break;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(KEY_TIMER_DURATION, durationToSave);
        editor.apply();
    }

    private void saveApiKey() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String apiKeyToSave = apiKeyEditText.getText().toString().trim();
        editor.putString(KEY_API_KEY, apiKeyToSave);
        editor.apply();
    }

    private void saveActiveModel(String model) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_ACTIVE_MODEL, model);
        editor.apply();
    }
}