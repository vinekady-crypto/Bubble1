package com.app.bubble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix; // FIX: Added missing import
import android.graphics.PixelFormat;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the Full Screen Scanner Overlay.
 * Handles CameraX, Universal Multi-Language OCR, Gemini AI Cloud OCR, and the Editor UI.
 */
public class ScannerUiManager implements LifecycleOwner {

    private static ScannerUiManager instance;
    private Context context;
    private WindowManager windowManager;
    private View rootView;
    private WindowManager.LayoutParams params;
    private boolean isShowing = false;

    // Lifecycle Registry for CameraX
    private LifecycleRegistry lifecycleRegistry;

    // CameraX Variables
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    // UI Elements - Layers
    private View cameraLayer;
    private View editorLayer;

    // UI Elements - Controls
    private TextView tvOcrMode; // To show current mode

    // UI Elements - Editor
    private EditText editTextResult;
    private RecyclerView recyclerHistory;
    
    // Data Helpers
    private ScanHistoryHelper historyHelper;
    private ScanHistoryAdapter historyAdapter;

    // OCR Modes
    private static final int MODE_UNIVERSAL = 0;
    private static final int MODE_GEMINI_AI = 1;
    private static final int MODE_LATIN = 2;
    private static final int MODE_DEVANAGARI = 3;
    private static final int MODE_CHINESE = 4;
    private static final int MODE_KOREAN = 5;
    private static final int MODE_JAPANESE = 6;

    private int currentOcrMode = MODE_UNIVERSAL;

    public static synchronized ScannerUiManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScannerUiManager(context);
        }
        return instance;
    }

    public static void onImagePicked(Context context, Uri imageUri) {
        if (instance != null) {
            instance.show();
            if (imageUri != null) {
                instance.processGalleryImage(imageUri);
            }
        }
    }

    public static void onGalleryCancelled(Context context) {
        if (instance != null) {
            instance.show();
        }
    }

    private ScannerUiManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.cameraExecutor = Executors.newSingleThreadExecutor();
        this.lifecycleRegistry = new LifecycleRegistry(this);
        this.historyHelper = new ScanHistoryHelper(context);
    }

    public void show() {
        if (isShowing) return;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Camera permission required.", Toast.LENGTH_LONG).show();
            return;
        }

        if (rootView == null) {
            initView();
        }

        try {
            windowManager.addView(rootView, params);
            isShowing = true;
            lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
            lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);
            startCamera();
            loadHistory();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hide() {
        if (!isShowing) return;
        try {
            lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
            windowManager.removeView(rootView);
            isShowing = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        rootView = inflater.inflate(R.layout.layout_scanner_window, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, 
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        // Find Views
        cameraLayer = rootView.findViewById(R.id.camera_layer);
        editorLayer = rootView.findViewById(R.id.editor_layer);
        previewView = rootView.findViewById(R.id.camera_preview);
        editTextResult = rootView.findViewById(R.id.edit_text_result);
        recyclerHistory = rootView.findViewById(R.id.recycler_history);
        tvOcrMode = rootView.findViewById(R.id.tv_ocr_mode);

        // --- CAMERA LAYER BUTTONS ---
        rootView.findViewById(R.id.btn_close_scanner).setOnClickListener(v -> hide());
        rootView.findViewById(R.id.btn_capture).setOnClickListener(v -> takePhoto());
        rootView.findViewById(R.id.btn_open_gallery).setOnClickListener(v -> openGallery());
        
        // New: OCR Mode Button
        rootView.findViewById(R.id.btn_ocr_settings).setOnClickListener(v -> showOcrModePopup(v));
        
        // New: Direct Editor Button
        rootView.findViewById(R.id.btn_open_editor).setOnClickListener(v -> switchToEditorMode());

        // --- EDITOR LAYER BUTTONS ---
        rootView.findViewById(R.id.btn_back_to_camera).setOnClickListener(v -> switchToCameraMode());
        rootView.findViewById(R.id.btn_save_exit).setOnClickListener(v -> saveAndExit());
        rootView.findViewById(R.id.btn_copy_text).setOnClickListener(v -> copyTextToClipboard());
        rootView.findViewById(R.id.btn_clear_text).setOnClickListener(v -> editTextResult.setText(""));

        // Setup History
        recyclerHistory.setLayoutManager(new LinearLayoutManager(context));
        historyAdapter = new ScanHistoryAdapter(new ScanHistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String text) {
                editTextResult.setText(text);
            }
            @Override
            public void onDeleteItems(List<String> itemsToDelete) {
                historyHelper.deleteItems(itemsToDelete);
                loadHistory(); 
            }
        });
        recyclerHistory.setAdapter(historyAdapter);

        // Initialize UI Text
        updateOcrModeText();
        switchToCameraMode();
    }

    private void showOcrModePopup(View v) {
        PopupMenu popup = new PopupMenu(context, v);
        
        // Add menu items programmatically
        popup.getMenu().add(0, MODE_UNIVERSAL, 0, "Universal (Auto)");
        popup.getMenu().add(0, MODE_GEMINI_AI, 1, "Gemini AI (Cloud/Pro)");
        popup.getMenu().add(0, MODE_LATIN, 2, "English / Latin");
        popup.getMenu().add(0, MODE_DEVANAGARI, 3, "Devanagari (Hindi)");
        popup.getMenu().add(0, MODE_CHINESE, 4, "Chinese");
        popup.getMenu().add(0, MODE_KOREAN, 5, "Korean");
        popup.getMenu().add(0, MODE_JAPANESE, 6, "Japanese");

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                currentOcrMode = item.getItemId();
                updateOcrModeText();
                return true;
            }
        });
        popup.show();
    }

    private void updateOcrModeText() {
        if (tvOcrMode == null) return;
        switch (currentOcrMode) {
            case MODE_UNIVERSAL: tvOcrMode.setText("Auto"); break;
            case MODE_GEMINI_AI: tvOcrMode.setText("Gemini"); break;
            case MODE_LATIN: tvOcrMode.setText("Latin"); break;
            case MODE_DEVANAGARI: tvOcrMode.setText("Indic"); break;
            case MODE_CHINESE: tvOcrMode.setText("CN"); break;
            case MODE_KOREAN: tvOcrMode.setText("KR"); break;
            case MODE_JAPANESE: tvOcrMode.setText("JP"); break;
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("ScannerUi", "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        Toast.makeText(context, "Scanning...", Toast.LENGTH_SHORT).show();

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                // If Gemini Mode is selected, we need the Bitmap
                if (currentOcrMode == MODE_GEMINI_AI) {
                    Bitmap bitmap = imageProxyToBitmap(image);
                    if (bitmap != null) {
                        runGeminiRecognition(bitmap);
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> 
                            Toast.makeText(context, "Image conversion failed", Toast.LENGTH_SHORT).show());
                    }
                    image.close();
                } 
                // Otherwise use ML Kit's InputImage
                else {
                    @SuppressLint("UnsafeOptInUsageError")
                    Image mediaImage = image.getImage();
                    if (mediaImage != null) {
                        InputImage inputImage = InputImage.fromMediaImage(mediaImage, image.getImageInfo().getRotationDegrees());
                        routeMlKitRecognition(inputImage);
                        image.close();
                    }
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                new Handler(Looper.getMainLooper()).post(() -> 
                    Toast.makeText(context, "Capture Failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // Helper to convert CameraX ImageProxy to Bitmap (Assuming JPEG format)
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            
            // Handle Rotation
            Matrix matrix = new Matrix();
            matrix.postRotate(image.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void openGallery() {
        hide();
        Intent intent = new Intent(context, GalleryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void processGalleryImage(Uri imageUri) {
        try {
            // For Gemini, we need Bitmap
            if (currentOcrMode == MODE_GEMINI_AI) {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);
                runGeminiRecognition(bitmap);
            } 
            // For ML Kit, InputImage is efficient
            else {
                InputImage image = InputImage.fromFilePath(context, imageUri);
                Toast.makeText(context, "Processing...", Toast.LENGTH_SHORT).show();
                routeMlKitRecognition(image);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    // --- GEMINI AI LOGIC (Cloud) ---
    private void runGeminiRecognition(Bitmap bitmap) {
        // Get API Key
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(SettingsActivity.KEY_API_KEY, "");

        if (apiKey.isEmpty()) {
            new Handler(Looper.getMainLooper()).post(() -> 
                Toast.makeText(context, "Gemini API Key missing in Settings!", Toast.LENGTH_LONG).show());
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(context, "Sending to Gemini AI...", Toast.LENGTH_SHORT).show());

        cameraExecutor.execute(() -> {
            String result = GeminiApi.performImageOcr(bitmap, apiKey);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (result != null) {
                    onScanSuccess(result);
                } else {
                    Toast.makeText(context, "Gemini Scan Failed. Check Internet/Quota.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // --- ML KIT LOGIC (Local) ---
    private void routeMlKitRecognition(InputImage image) {
        switch (currentOcrMode) {
            case MODE_UNIVERSAL: runUniversalRecognition(image); break;
            case MODE_LATIN: runSingleRecognition(image, TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)); break;
            case MODE_DEVANAGARI: runSingleRecognition(image, TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build())); break;
            case MODE_CHINESE: runSingleRecognition(image, TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build())); break;
            case MODE_KOREAN: runSingleRecognition(image, TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build())); break;
            case MODE_JAPANESE: runSingleRecognition(image, TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build())); break;
            default: runUniversalRecognition(image); break;
        }
    }

    private void runSingleRecognition(InputImage image, TextRecognizer recognizer) {
        recognizer.process(image)
            .addOnSuccessListener(text -> onScanSuccess(text.getText()))
            .addOnFailureListener(e -> Toast.makeText(context, "Scan Failed", Toast.LENGTH_SHORT).show());
    }

    private void runUniversalRecognition(InputImage image) {
        // Initialize engines
        TextRecognizer recognizerDev = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
        TextRecognizer recognizerChi = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        TextRecognizer recognizerKor = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        TextRecognizer recognizerJap = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
        TextRecognizer recognizerLat = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        
        Task<Text> t1 = recognizerDev.process(image);
        Task<Text> t2 = recognizerChi.process(image);
        Task<Text> t3 = recognizerKor.process(image);
        Task<Text> t4 = recognizerJap.process(image);
        Task<Text> t5 = recognizerLat.process(image);

        Tasks.whenAllSuccess(t1, t2, t3, t4, t5).addOnSuccessListener(results -> {
            StringBuilder sb = new StringBuilder();
            for (Object obj : results) {
                if (obj instanceof Text) {
                    String s = ((Text) obj).getText();
                    if (!s.trim().isEmpty()) sb.append(s).append("\n");
                }
            }
            
            recognizerDev.close(); recognizerChi.close(); recognizerKor.close();
            recognizerJap.close(); recognizerLat.close();

            new Handler(Looper.getMainLooper()).post(() -> {
                if (sb.toString().trim().isEmpty()) Toast.makeText(context, "No text found", Toast.LENGTH_SHORT).show();
                else onScanSuccess(sb.toString());
            });
        });
    }

    private void onScanSuccess(String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            switchToEditorMode();
            editTextResult.setText(text);
            historyHelper.addItem(text);
            loadHistory();
            copyTextToClipboard();
        });
    }

    private void switchToCameraMode() {
        if (cameraLayer != null && editorLayer != null) {
            cameraLayer.setVisibility(View.VISIBLE);
            editorLayer.setVisibility(View.GONE);
        }
    }

    private void switchToEditorMode() {
        if (cameraLayer != null && editorLayer != null) {
            cameraLayer.setVisibility(View.GONE);
            editorLayer.setVisibility(View.VISIBLE);
        }
    }

    private void copyTextToClipboard() {
        String text = editTextResult.getText().toString();
        if (!text.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Scanned Text", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Copied to Clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAndExit() {
        copyTextToClipboard();
        hide();
    }

    private void loadHistory() {
        if (historyAdapter != null && historyHelper != null) {
            historyAdapter.setItems(historyHelper.getHistory());
        }
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }
}