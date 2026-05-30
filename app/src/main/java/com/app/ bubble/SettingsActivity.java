package com.app.bubble;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

// NEW: AdMob Imports
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

public class SettingsActivity extends Activity {

    // Constants for SharedPreferences to ensure consistency.
    public static final String PREFS_NAME = "BubbleTranslatorPrefs";
    public static final String KEY_TIMER_DURATION = "CropTimerDuration";
    public static final String KEY_API_KEY = "GeminiApiKey"; // New constant for the API key

    private Spinner cropTimerSpinner;
    private EditText apiKeyEditText; // New variable for the EditText field
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cropTimerSpinner = findViewById(R.id.crop_timer_spinner);
        apiKeyEditText = findViewById(R.id.api_key_edit_text); // Find the new EditText

        // --- Setup for Crop Timer Spinner ---
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            this,
            R.array.crop_timer_options,
            android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cropTimerSpinner.setAdapter(adapter);

        // Load the saved preferences and set the views to the correct state.
        loadCurrentSettings();

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
}