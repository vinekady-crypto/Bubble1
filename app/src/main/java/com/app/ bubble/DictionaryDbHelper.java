package com.app.bubble;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Manages the high-performance local SQLite FTS4 (Full-Text Search) dictionary database.
 * Parses the compressed assets word list inside a single database transaction on the first launch.
 */
public class DictionaryDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "bubble_dict.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "word_search_table";
    private static final String COLUMN_WORD = "word_text";

    private static final String PREFS_NAME = "BubbleDictionaryPrefs";
    private static final String KEY_IS_LOADED = "is_dict_loaded";

    private final Context mContext;

    public DictionaryDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create an FTS4 virtual table for high-speed prefix matching
        db.execSQL("CREATE VIRTUAL TABLE " + TABLE_NAME + " USING fts4(" + COLUMN_WORD + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    /**
     * Checks if the database is already populated. If not, reads the GZIP asset
     * file and loads the 350,000 words inside a background thread transaction.
     */
    public void initializeDictionaryIfNeeded() {
        final SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isLoaded = prefs.getBoolean(KEY_IS_LOADED, false);

        if (isLoaded) {
            return; // Already populated, skip to prevent overhead
        }

        // Run the population logic on a background thread to prevent UI locking
        new Thread(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase db = getWritableDatabase();
                db.beginTransaction();
                
                InputStream is = null;
                GZIPInputStream gis = null;
                BufferedReader reader = null;

                try {
                    // Open the compressed GZIP stream renamed as .bin to bypass build compression bugs
                    is = mContext.getAssets().open("english_words.bin");
                    gis = new GZIPInputStream(is);
                    reader = new BufferedReader(new InputStreamReader(gis, "UTF-8"));

                    // Compile the statement template once to optimize insert speeds
                    String sql = "INSERT INTO " + TABLE_NAME + " (" + COLUMN_WORD + ") VALUES (?);";
                    SQLiteStatement stmt = db.compileStatement(sql);

                    String line;
                    while ((line = reader.readLine()) != null) {
                        String cleanWord = line.trim();
                        if (!cleanWord.isEmpty()) {
                            stmt.bindString(1, cleanWord);
                            stmt.executeInsert();
                            stmt.clearBindings();
                        }
                    }

                    db.setTransactionSuccessful();
                    
                    // Toggle loading state flag on success
                    prefs.edit().putBoolean(KEY_IS_LOADED, true).apply();
                    Log.d("DictionaryDbHelper", "Dictionary database populated successfully.");

                } catch (Exception e) {
                    Log.e("DictionaryDbHelper", "Failed to populate dictionary database", e);
                } finally {
                    db.endTransaction();
                    try {
                        if (reader != null) {
                            reader.close();
                        }
                        if (gis != null) {
                            gis.close();
                        }
                        if (is != null) {
                            is.close();
                        }
                    } catch (Exception e) {
                        Log.e("DictionaryDbHelper", "Failed to close input streams", e);
                    }
                }
            }
        }).start();
    }

    /**
     * Queries the FTS4 virtual table using wildcard prefix matching.
     * Searches are executed natively in C, returning lists in milliseconds.
     */
    public List<String> getMatchesForPrefix(String prefix) {
        List<String> results = new ArrayList<>();
        if (prefix == null || prefix.trim().isEmpty()) {
            return results;
        }

        SQLiteDatabase db = getReadableDatabase();
        
        // Escape single quotes to prevent syntax parsing issues
        String cleanPrefix = prefix.trim().replace("'", "''");
        // Append FTS trailing wildcard (abc -> abc*)
        String matchValue = cleanPrefix + "*";

        String query = "SELECT " + COLUMN_WORD + " FROM " + TABLE_NAME + 
                       " WHERE " + COLUMN_WORD + " MATCH ? LIMIT 10;";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, new String[]{matchValue});
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    results.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("DictionaryDbHelper", "Failed to query prefix: " + prefix, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return results;
    }
}