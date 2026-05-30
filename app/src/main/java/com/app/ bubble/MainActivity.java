package com.app.bubble;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Toast;

// NEW: Imports for In-App Updates
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.gms.tasks.Task;

public class MainActivity extends Activity { // It is now Activity, NOT AppCompatActivity
    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private static final int SCREEN_CAPTURE_REQ_CODE = 5678;
    
    // NEW: Request code for In-App Update
    private static final int APP_UPDATE_REQUEST_CODE = 999;
    
    // NEW: App Update Manager
    private AppUpdateManager appUpdateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (checkOverlayPermission()) {
						requestScreenCapture();
					}
				}
			});
            
        // NEW: Check for immediate updates when app starts
        appUpdateManager = AppUpdateManagerFactory.create(this);
        checkForAppUpdate();

        // NEW: Prompt user to enable advanced features (Keyboard & Auto-Scroll)
        checkAndPromptForServices();
        
        // CHECK: Handle auto-restart request from Service (Fix for "Permission Not Available")
        handleAutoPermissionRequest(getIntent());
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // If the service sends the user back here while the activity is in background
        handleAutoPermissionRequest(intent);
    }

    private void handleAutoPermissionRequest(Intent intent) {
        if (intent != null && intent.getBooleanExtra("AUTO_REQUEST_PERMISSION", false)) {
            // Service requested a permission restart. Check overlay first, then pop dialog.
            if (checkOverlayPermission()) {
                requestScreenCapture();
            }
        }
    }

    // NEW: Check if services are enabled and show dialog if not
    private void checkAndPromptForServices() {
        boolean isKeyboardEnabled = isInputMethodEnabled();
        boolean isAccessibilityEnabled = isAccessibilityEnabled();

        if (!isKeyboardEnabled || !isAccessibilityEnabled) {
            new AlertDialog.Builder(this)
                .setTitle("Enable Advanced Features")
                .setMessage("To use the Two-Line Copy and Auto-Scroll features, you must enable the Bubble Keyboard and Accessibility Service in settings.")
                .setPositiveButton("Enable Keyboard", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNeutralButton("Enable Accessibility", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }

    // NEW: Check if our Custom Keyboard is enabled
    private boolean isInputMethodEnabled() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
        ComponentName componentName = new ComponentName(this, BubbleKeyboardService.class);
        return id != null && id.contains(componentName.flattenToShortString());
    }

    // NEW: Check if our Accessibility Service is enabled
    private boolean isAccessibilityEnabled() {
        String prefString = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (prefString != null) {
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(prefString);
            ComponentName componentName = new ComponentName(this, GlobalScrollService.class);
            while (splitter.hasNext()) {
                String accessibilityService = splitter.next();
                if (accessibilityService.equalsIgnoreCase(componentName.flattenToString())) {
                    return true;
                }
            }
        }
        return false;
    }

    // NEW: Method to check for Google Play updates
    private void checkForAppUpdate() {
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

        appUpdateInfoTask.addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<AppUpdateInfo>() {
            @Override
            public void onSuccess(AppUpdateInfo appUpdateInfo) {
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                                appUpdateInfo,
                                AppUpdateType.IMMEDIATE,
                                MainActivity.this,
                                APP_UPDATE_REQUEST_CODE);
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    // NEW: Check on resume if the update is in progress
    @Override
    protected void onResume() {
        super.onResume();
        
        appUpdateManager
            .getAppUpdateInfo()
            .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<AppUpdateInfo>() {
                @Override
                public void onSuccess(AppUpdateInfo appUpdateInfo) {
                    if (appUpdateInfo.updateAvailability()
                            == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                        // If an in-app update is already in progress, resume the update.
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    AppUpdateType.IMMEDIATE,
                                    MainActivity.this,
                                    APP_UPDATE_REQUEST_CODE);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            return false;
        }
        return true;
    }

    private void requestScreenCapture() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQ_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // NEW: Handle the result of the In-App Update
        if (requestCode == APP_UPDATE_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                // If the update is cancelled or fails, close the app (Strict Mode)
                Toast.makeText(this, "Update is required to continue.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    requestScreenCapture();
                } else {
                    Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == SCREEN_CAPTURE_REQ_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Intent serviceIntent = new Intent(this, FloatingTranslatorService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                // Clear any previous auto-restart flags
                serviceIntent.removeExtra("AUTO_REQUEST_PERMISSION");
                
                // IMPORTANT FIX:
                // Now that FloatingTranslatorService handles notifications properly,
                // we MUST use startForegroundService on Android O+ to prevent the app
                // from being killed when you open WhatsApp/Browser.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                // We do NOT finish() here if it was an auto-request, so the user sees the app state
                // But for standard flow, we usually finish or moveTaskToBack.
                moveTaskToBack(true);
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}