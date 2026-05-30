package com.app.bubble;

import android.app.Application;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

public class MyApplication extends Application {

    private AppOpenManager appOpenManager;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize the Google Mobile Ads SDK
        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                // Ads SDK initialized successfully
            }
        });

        // Initialize the AppOpenManager to handle "Reopen" ads
        appOpenManager = new AppOpenManager(this);
    }
}