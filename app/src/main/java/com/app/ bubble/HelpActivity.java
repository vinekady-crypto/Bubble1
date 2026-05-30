package com.app.bubble;

import android.app.Activity;
import android.os.Bundle;

public class HelpActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the content view to the help screen layout we created earlier.
        setContentView(R.layout.activity_help);
    }
}

