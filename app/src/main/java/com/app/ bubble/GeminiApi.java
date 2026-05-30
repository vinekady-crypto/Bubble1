package com.app.bubble;

import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class GeminiApi {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";

    private GeminiApi() {}

    /**
     * Overloaded refine method for backward compatibility.
     */
    public static String refine(String textToRefine, String targetLanguage, String apiKey) throws Exception {
        return refine(textToRefine, targetLanguage, apiKey, DEFAULT_MODEL);
    }

    /**
     * Sends text to the Gemini API to be refined using a dynamic model.
     */
    public static String refine(String textToRefine, String targetLanguage, String apiKey, String model) throws Exception {
        String activeModel = (model == null || model.isEmpty()) ? DEFAULT_MODEL : model;
        URL url = new URL(BASE_URL + activeModel + ":generateContent?key=" + apiKey);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // 2. Create prompt
        String prompt = "You are an expert language assistant. Your task is to refine the following machine-translated text which is in " + targetLanguage + ". " +
            "Make it sound more natural, fluent, and grammatically perfect in " + targetLanguage + ", as if a native speaker wrote it. " +
            "Do not change the original meaning. Only provide the refined text as your answer, with no extra explanations or introductory phrases. " +
            "Here is the text: \"" + textToRefine + "\"";

        // 3. Build JSON
        JSONObject part = new JSONObject();
        part.put("text", prompt);

        JSONArray partsArray = new JSONArray();
        partsArray.put(part);

        JSONObject content = new JSONObject();
        content.put("parts", partsArray);

        JSONArray contentsArray = new JSONArray();
        contentsArray.put(content);

        JSONObject requestBody = new JSONObject();
        requestBody.put("contents", contentsArray);

        String jsonInputString = requestBody.toString();

        // 4. Send Request
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // 5. Check Response Code to catch API errors (like 400 Bad Request)
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            // Read error stream
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                errorResponse.append(line.trim());
            }
            throw new Exception("API Error " + responseCode + ": " + errorResponse.toString());
        }

        // 6. Read Success Response
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        connection.disconnect();

        // 7. Parse JSON
        JSONObject jsonResponse = new JSONObject(response.toString());
        String refinedText = jsonResponse.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text");

        return refinedText.trim();
    }

    /**
     * Overloaded performImageOcr method for backward compatibility.
     */
    public static String performImageOcr(Bitmap image, String apiKey) {
        return performImageOcr(image, apiKey, DEFAULT_MODEL);
    }

    /**
     * Sends an Image to Gemini to extract text (OCR) using a dynamic model.
     */
    public static String performImageOcr(Bitmap image, String apiKey, String model) {
        if (image == null || apiKey == null || apiKey.isEmpty()) return null;

        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            String base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP);

            String activeModel = (model == null || model.isEmpty()) ? DEFAULT_MODEL : model;
            URL url = new URL(BASE_URL + activeModel + ":generateContent?key=" + apiKey);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            JSONObject textPart = new JSONObject();
            textPart.put("text", "Extract all text from this image exactly as it appears. Do not translate. Just transcribe.");

            JSONObject inlineData = new JSONObject();
            inlineData.put("mime_type", "image/jpeg");
            inlineData.put("data", base64Image);

            JSONObject imagePart = new JSONObject();
            imagePart.put("inline_data", inlineData);

            JSONArray partsArray = new JSONArray();
            partsArray.put(textPart);
            partsArray.put(imagePart);

            JSONObject content = new JSONObject();
            content.put("parts", partsArray);

            JSONArray contentsArray = new JSONArray();
            contentsArray.put(content);

            JSONObject requestBody = new JSONObject();
            requestBody.put("contents", contentsArray);

            String jsonInputString = requestBody.toString();

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            connection.disconnect();

            JSONObject jsonResponse = new JSONObject(response.toString());
            String extractedText = jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

            return extractedText.trim();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Fetches all available Gemini models that support generateContent.
     */
    public static List<String> fetchModels(String apiKey) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("API Key is empty");
        }

        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                errorResponse.append(line.trim());
            }
            throw new Exception("API Error " + responseCode + ": " + errorResponse.toString());
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), "utf-8"))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        connection.disconnect();

        JSONObject jsonResponse = new JSONObject(response.toString());
        JSONArray modelsArray = jsonResponse.getJSONArray("models");
        List<String> list = new ArrayList<>();

        for (int i = 0; i < modelsArray.length(); i++) {
            JSONObject modelObj = modelsArray.getJSONObject(i);
            String name = modelObj.getString("name");
            
            // Filter models supporting generateContent
            JSONArray methods = modelObj.getJSONArray("supportedGenerationMethods");
            boolean supportsGenerate = false;
            for (int j = 0; j < methods.length(); j++) {
                if ("generateContent".equals(methods.getString(j))) {
                    supportsGenerate = true;
                    break;
                }
            }

            if (supportsGenerate) {
                // Strip the "models/" prefix for clean display and direct endpoint construction
                if (name.startsWith("models/")) {
                    name = name.substring("models/".length());
                }
                list.add(name);
            }
        }
        return list;
    }
}