package com.app.bubble;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

public class GalleryActivity extends Activity {

    private static final int REQUEST_CODE_PICK_IMAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // This Activity has no UI (Theme.Translucent). 
        // It immediately launches the System Gallery.
        openGallery();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // Ensure we only get images
        intent.setType("image/*");
        
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open Gallery", Toast.LENGTH_SHORT).show();
            // If we can't open gallery, restore the scanner window immediately
            ScannerUiManager.onGalleryCancelled(this);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null) {
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    // Success: Pass the image to the Manager
                    ScannerUiManager.onImagePicked(this, selectedImageUri);
                } else {
                    // Weird edge case: Result OK but no data? Restore window.
                    ScannerUiManager.onGalleryCancelled(this);
                }
            } else {
                // User Cancelled (Pressed Back): Restore the Scanner Window!
                ScannerUiManager.onGalleryCancelled(this);
                Toast.makeText(this, "Selection Cancelled", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Unknown request: Restore window just in case
            ScannerUiManager.onGalleryCancelled(this);
        }
        
        // Always close this bridge activity immediately after handling the result
        finish();
    }
}