package com.app.bubble;

import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public final class TranslateApi {

    // This class is not meant to be instantiated, so we make the constructor private.
    private TranslateApi() {}

    /**
     * Translates text from a source language to a target language.
     * @param fromLang The source language code (e.g., "en" for English).
     * @param toLang The target language code (e.g., "ml" for Malayalam).
     * @param text The text to be translated.
     * @return The translated text as a String, or null if an error occurs.
     */
    public static String translate(String fromLang, String toLang, String text) {
        try {
            // Construct the URL for the Google Translate API.
            String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" +
				fromLang + "&tl=" + toLang + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");

            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // Set a user-agent to avoid being blocked.
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            // Read the response from the server.
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();

            // The API returns a complex JSON array. We need to parse it to extract the full translation.
            // [[["Translated sentence 1","Original sentence 1"],["Translated sentence 2","Original sentence 2"]],...]
            // The previous bug was only reading the first element. This new code iterates through all parts.
            JSONArray jsonArray = new JSONArray(response.toString());
            JSONArray translations = jsonArray.getJSONArray(0);
            StringBuilder translatedText = new StringBuilder();

            for (int i = 0; i < translations.length(); i++) {
                JSONArray translationSegment = translations.getJSONArray(i);
                // Append each translated segment to our result.
                translatedText.append(translationSegment.getString(0));
            }

            return translatedText.toString();

        } catch (Exception e) {
            // Log the error for debugging purposes.
            e.printStackTrace();
            // Return null to indicate that the translation failed.
            return null;

        }
    }
}

