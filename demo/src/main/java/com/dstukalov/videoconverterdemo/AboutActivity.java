package com.dstukalov.videoconverterdemo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import android.text.util.Linkify;
import android.widget.TextView;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.text.SpannableString;
import android.util.Log;
import android.widget.ScrollView; // For scrollable AlertDialog content


import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;

import java.util.Objects;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


public class AboutActivity extends AppCompatActivity {
    private static final String TAG = "AboutActivity";
    private TextView seeAppLogsTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        final ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);


        TextView firstRunMessageTextView = findViewById(R.id.important_info_text);

        firstRunMessageTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportantMessageDialog();
            }
        });

        seeAppLogsTextView = findViewById(R.id.see_app_logs);
        seeAppLogsTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show a loading dialog or progress bar while logs are fetched
                ContextThemeWrapper themedContext = new ContextThemeWrapper(AboutActivity.this, R.style.AppAlertDialogStyle);
                AlertDialog.Builder loadingDialogBuilder = new AlertDialog.Builder(themedContext);
                loadingDialogBuilder.setMessage("Fetching logs, please wait...");
                loadingDialogBuilder.setCancelable(false);
                AlertDialog loadingDialog = loadingDialogBuilder.create();
                loadingDialog.show();

                new FetchLogsTask(AboutActivity.this, loadingDialog).execute();
            }
        });

        // Set up code for version name and version code data extraction
        TextView textVersion = findViewById(R.id.text_version);

        // Get the generated dynamic strings
        String dynamicVersionName = getString(R.string.dynamic_version_name);
        String dynamicVersionCode = getString(R.string.dynamic_version_code);

        // Set the text using the formatted string resource
        textVersion.setText(getString(R.string.version_display_format, dynamicVersionName, dynamicVersionCode));

        // Set up clickable links
        setupLink(R.id.text_my_repository, "https://github.com/dhlalit11/VideoConverter");
        setupLink(R.id.text_changelog, "https://github.com/dhlalit11/VideoConverter/commits/master");
        setupLink(R.id.text_original_repository, "https://github.com/dstukalov/VideoConverter");
        setupLink(R.id.text_privacy, "https://github.com/dhlalit11/VideoConverter/blob/master/PrivacyPolicy");
        setupLink(R.id.text_license, "https://www.apache.org/licenses/LICENSE-2.0");
    }

    private void setupLink(int viewId, String url) {
        TextView textView = findViewById(viewId);
        textView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
    }

private void showImportantMessageDialog() {
    ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.AppAlertDialogStyle);
    new AlertDialog.Builder(themedContext).setTitle("Working of this app") // Set your desired title
            .setMessage("Welcome to Video Compressor \nWelcome to version " + getString(R.string.dynamic_version_name) + "\n" + getString(R.string.important_info_text_message)) // Set your desired message
            .setPositiveButton("OK, Got It", (dialog, which) -> {
                // User clicked OK
                dialog.dismiss();
            })
            // You can add a setNegativeButton or setNeutralButton if needed
            // .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .setCancelable(true) // Allows dismissing by tapping outside or back button
            .show();

}

    private void showLogsDialog(String logs) {
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.AppAlertDialogStyle);
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setTitle("App Logs (E/W/I)");

        // Create a TextView for the logs to make them scrollable within the dialog
        final TextView messageTextView;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // Apply the style directly for API 23+
            messageTextView = new TextView(themedContext, null, 0, R.style.LogsMessageTextStyle);
        } else {
            // For older versions, create the TextView and then set its appearance
            messageTextView = new TextView(themedContext); // Use themedContext if other theme attributes should apply
            messageTextView.setTextAppearance(themedContext, R.style.LogsMessageTextStyle);
        }

        messageTextView.setText(logs.isEmpty() ? "No relevant logs found for this app." : logs);
        messageTextView.setPadding(50, 30, 50, 20);
        messageTextView.setTextIsSelectable(true);

        // It's good practice to make links clickable if any appear in logs
        Linkify.addLinks(messageTextView, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        messageTextView.setMovementMethod(LinkMovementMethod.getInstance());


        // To make the TextView scrollable within the AlertDialog
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(messageTextView);
        // You might want to limit the height of the ScrollView in the dialog
        // scrollView.setLayoutParams(new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, 800)); // Example: max height 800px

        builder.setView(scrollView);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        // Add a "Copy to Clipboard" button (Optional but useful)
        builder.setNeutralButton("Copy Logs", (dialog, which) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                android.content.ClipData clip = android.content.ClipData.newPlainText("AppLogs", logs);
                clipboard.setPrimaryClip(clip);
                android.widget.Toast.makeText(this, "Logs copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
            }
        });


        builder.setCancelable(true);
        AlertDialog dialog = builder.create();

        dialog.show();
    }


    private static class FetchLogsTask extends AsyncTask<Void, Void, String> {
        private final WeakReference<AboutActivity> activityReference;
        private final WeakReference<AlertDialog> loadingDialogReference; // To dismiss it

        FetchLogsTask(AboutActivity context, AlertDialog loadingDialog) {
            activityReference = new WeakReference<>(context);
            loadingDialogReference = new WeakReference<>(loadingDialog);
        }

        @Override
        protected String doInBackground(Void... voids) {
            AboutActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                return "";
            }

            StringBuilder logBuilder = new StringBuilder();
            try {
                String packageName = activity.getPackageName();
                int pid = android.os.Process.myPid();


                Process process = Runtime.getRuntime().exec("logcat -d --pid=" + pid + " -v time");
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

                String line;
                int linesRead = 0;
                int maxLines = 1000; // Limit the number of lines to prevent performance issues

                while ((line = bufferedReader.readLine()) != null && linesRead < maxLines) {
                    if (line.contains("E/") || line.contains("W/") || line.contains("I/") ||
                            line.contains(packageName) && (line.matches("^[IWE]/.*"))) { // Broader check
                        String priorityChar = line.length() > 0 ? line.substring(0, 1) : "";
                            logBuilder.append(line).append("\n●➜ ");
                            linesRead++;

                    }
                }
                bufferedReader.close();

                if (logBuilder.length() == 0) {
                    Log.d(TAG, "PID specific log was empty, trying broader logcat for app (less reliable)");
                }

            } catch (IOException e) {
                Log.e(TAG, "Error fetching logs: ", e);
                logBuilder.append("Error fetching logs: ").append(e.getMessage());
            }
            return logBuilder.toString();
        }

        @Override
        protected void onPostExecute(String logs) {
            AboutActivity activity = activityReference.get();
            AlertDialog loadingDialog = loadingDialogReference.get();

            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }

            if (activity != null && !activity.isFinishing()) {
                activity.showLogsDialog(logs);
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
