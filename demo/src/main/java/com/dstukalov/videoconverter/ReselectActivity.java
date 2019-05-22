package com.dstukalov.videoconverter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class ReselectActivity extends Activity {
    
    public static final String RESELECT_EXTRA = "reselect";

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = new Intent();
        intent.putExtra(RESELECT_EXTRA, true);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }
}
