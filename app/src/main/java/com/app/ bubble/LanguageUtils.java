package com.app.bubble;

import java.util.ArrayList;
import java.util.List;

public class LanguageUtils {

    // 1. Language Names (Displayed to User)
    // Ordered Alphabetically for easier finding
    public static final String[] LANGUAGE_NAMES = {
        "Afrikaans", "Albanian", "Amharic", "Arabic", "Armenian", "Azerbaijani", "Basque", "Belarusian", "Bengali", 
        "Bosnian", "Bulgarian", "Catalan", "Cebuano", "Chichewa", "Chinese (Simplified)", "Chinese (Traditional)", 
        "Corsican", "Croatian", "Czech", "Danish", "Dutch", "English", "Esperanto", "Estonian", "Filipino", "Finnish", 
        "French", "Frisian", "Galician", "Georgian", "German", "Greek", "Gujarati", "Haitian Creole", "Hausa", 
        "Hawaiian", "Hebrew", "Hindi", "Hmong", "Hungarian", "Icelandic", "Igbo", "Indonesian", "Irish", "Italian", 
        "Japanese", "Javanese", "Kannada", "Kazakh", "Khmer", "Kinyarwanda", "Korean", "Kurdish (Kurmanji)", 
        "Kyrgyz", "Lao", "Latin", "Latvian", "Lithuanian", "Luxembourgish", "Macedonian", "Malagasy", "Malay", 
        "Malayalam", "Maltese", "Maori", "Marathi", "Mongolian", "Myanmar (Burmese)", "Nepali", "Norwegian", 
        "Odia (Oriya)", "Pashto", "Persian", "Polish", "Portuguese", "Punjabi", "Romanian", "Russian", "Samoan", 
        "Scots Gaelic", "Serbian", "Sesotho", "Shona", "Sindhi", "Sinhala", "Slovak", "Slovenian", "Somali", 
        "Spanish", "Sundanese", "Swahili", "Swedish", "Tajik", "Tamil", "Tatar", "Telugu", "Thai", "Turkish", 
        "Turkmen", "Ukrainian", "Urdu", "Uyghur", "Uzbek", "Vietnamese", "Welsh", "Xhosa", "Yiddish", "Yoruba", "Zulu"
    };

    // 2. Language Codes (Sent to API)
    // Must match the order of LANGUAGE_NAMES exactly
    public static final String[] LANGUAGE_CODES = {
        "af", "sq", "am", "ar", "hy", "az", "eu", "be", "bn", 
        "bs", "bg", "ca", "ceb", "ny", "zh-CN", "zh-TW", 
        "co", "hr", "cs", "da", "nl", "en", "eo", "et", "tl", "fi", 
        "fr", "fy", "gl", "ka", "de", "el", "gu", "ht", "ha", 
        "haw", "iw", "hi", "hmn", "hu", "is", "ig", "id", "ga", "it", 
        "ja", "jw", "kn", "kk", "km", "rw", "ko", "ku", 
        "ky", "lo", "la", "lv", "lt", "lb", "mk", "mg", "ms", 
        "ml", "mt", "mi", "mr", "mn", "my", "ne", "no", 
        "or", "ps", "fa", "pl", "pt", "pa", "ro", "ru", "sm", 
        "gd", "sr", "st", "sn", "sd", "si", "sk", "sl", "so", 
        "es", "su", "sw", "sv", "tg", "ta", "tt", "te", "th", "tr", 
        "tk", "uk", "ur", "ug", "uz", "vi", "cy", "xh", "yi", "yo", "zu"
    };

    /**
     * Helper to get the API code for a selected position in the spinner.
     */
    public static String getCode(int position) {
        if (position >= 0 && position < LANGUAGE_CODES.length) {
            return LANGUAGE_CODES[position];
        }
        return "en"; // Default to English if error
    }

    /**
     * Helper to find the index of a language code (e.g., to set default selection).
     */
    public static int getIndexForCode(String code) {
        for (int i = 0; i < LANGUAGE_CODES.length; i++) {
            if (LANGUAGE_CODES[i].equalsIgnoreCase(code)) {
                return i;
            }
        }
        return 0; // Default to first item
    }
}