package com.app.bubble;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Manages the background voice-to-text speech transcription engine.
 * Wraps Android's native SpeechRecognizer and delivers results through a listener callback.
 */
public class VoiceInputManager {

    private final Context context;
    private final VoiceInputListener listener;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;

    public interface VoiceInputListener {
        void onTranscriptionResult(String text);
        void onError(String error);
    }

    public VoiceInputManager(Context context, VoiceInputListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        initSpeechRecognizer();
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Toast.makeText(context, "Speak now...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBeginningOfSpeech() {
                // Fired when the user starts speaking
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Fired when the volume levels of the recorded audio stream change
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // Fired when more sound buffers are received
            }

            @Override
            public void onEndOfSpeech() {
                Toast.makeText(context, "Processing...", Toast.LENGTH_SHORT).show();
                isListening = false;
            }

            @Override
            public void onError(int error) {
                isListening = false;
                if (listener != null) {
                    listener.onError(getErrorMessage(error));
                }
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String transcription = matches.get(0);
                    if (listener != null) {
                        listener.onTranscriptionResult(transcription + " ");
                    }
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // Not implemented for simplified single-shot queries
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // Standard event fallback container
            }
        });
    }

    public void startListening() {
        if (speechRecognizer == null) {
            if (listener != null) {
                listener.onError("Speech recognition is not supported on this device.");
            }
            return;
        }

        if (!isListening) {
            speechRecognizer.startListening(speechRecognizerIntent);
            isListening = true;
        }
    }

    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    public void toggleListening() {
        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    /**
     * Converts native SpeechRecognizer integer error codes into user-friendly messages.
     */
    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client-side connection error.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions. Please allow microphone access.";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network connection error.";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network operation timed out.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No matching speech detected. Please try again.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Transcription engine is busy. Please wait.";
            case SpeechRecognizer.ERROR_SERVER:
                return "Google speech server returned an error.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech input detected.";
            default:
                return "Unknown speech recognition error occurred.";
        }
    }
}