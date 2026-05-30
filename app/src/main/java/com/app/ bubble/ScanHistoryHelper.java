package com.app.bubble;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Handles saving and loading scanned text history using SharedPreferences.
 * Stores data as a JSON String.
 */
public class ScanHistoryHelper {

    private static final String PREFS_NAME = "BubbleScanHistory";
    private static final String KEY_HISTORY = "ScanHistoryData";
    private static final int MAX_HISTORY_SIZE = 50; // Keep last 50 scans

    private SharedPreferences prefs;

    public ScanHistoryHelper(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Adds a new scanned text to the top of the history.
     */
    public void addItem(String text) {
        if (text == null || text.trim().isEmpty()) return;

        List<ScanItem> currentList = getHistory();
        
        // Check for duplicates (optional, but good for UX)
        for (ScanItem item : currentList) {
            if (item.text.equals(text)) {
                // If exists, move to top? Or just return.
                // Let's move to top by updating timestamp
                item.timestamp = System.currentTimeMillis();
                saveHistory(currentList);
                return;
            }
        }

        // Add new item
        currentList.add(0, new ScanItem(text, System.currentTimeMillis()));

        // Limit size
        if (currentList.size() > MAX_HISTORY_SIZE) {
            currentList.subList(MAX_HISTORY_SIZE, currentList.size()).clear();
        }

        saveHistory(currentList);
    }

    /**
     * Deletes a list of specific texts from history (Multi-select delete).
     */
    public void deleteItems(List<String> textsToDelete) {
        if (textsToDelete == null || textsToDelete.isEmpty()) return;

        List<ScanItem> currentList = getHistory();
        List<ScanItem> toRemove = new ArrayList<>();

        for (ScanItem item : currentList) {
            if (textsToDelete.contains(item.text)) {
                toRemove.add(item);
            }
        }

        currentList.removeAll(toRemove);
        saveHistory(currentList);
    }

    /**
     * Retrieves the list of scanned items, sorted by newest first.
     */
    public List<ScanItem> getHistory() {
        List<ScanItem> list = new ArrayList<>();
        String jsonString = prefs.getString(KEY_HISTORY, "");

        if (!jsonString.isEmpty()) {
            try {
                JSONArray array = new JSONArray(jsonString);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    list.add(new ScanItem(
                        obj.getString("text"),
                        obj.getLong("timestamp")
                    ));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Ensure sorted by time (Newest First)
        Collections.sort(list, new Comparator<ScanItem>() {
            @Override
            public int compare(ScanItem o1, ScanItem o2) {
                return Long.compare(o2.timestamp, o1.timestamp);
            }
        });

        return list;
    }

    private void saveHistory(List<ScanItem> list) {
        JSONArray array = new JSONArray();
        for (ScanItem item : list) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("text", item.text);
                obj.put("timestamp", item.timestamp);
                array.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    /**
     * Data model for a single scanned entry.
     */
    public static class ScanItem {
        public String text;
        public long timestamp;
        public boolean isSelected = false; // UI State for multi-delete

        public ScanItem(String text, long timestamp) {
            this.text = text;
            this.timestamp = timestamp;
        }
    }
}