package com.dstukalov.videoconverterdemo;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.dstukalov.videoconverter.MediaConverter;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "video-converter";

    public static final String FILE_PROVIDER_AUTHORITY = "com.dstukalov.videoconverter.fileprovider";

    private View mMainLayout;
    private TextView mInputInfoView;
    private TextView mOutputInfoView;
    private ImageView mThumbView;
    private Button mConvertButton;
    private ProgressBar mConversionProgressBar;
    private ProgressBar mLoadingProgressBar;
    private TextView mElapsedTimeView;
    private TimelineRangeBar mTimelineRangeBar;
    private VideoThumbnailsView mTimelineView;
    private TextView mTrimInfo;

    private View mOutputPlayButton;
    private View mOutputSendButton;
    private View mOutputOptionsButton;

    private @Nullable FramePreview mFramePreview;
    private Converter mConverter;
    private ConversionParameters mConversionParameters = CONV_PARAMS_1080P_H265_6000kbps;
    private MainViewModel mMainViewModel;
    private Uri mOutputUri; // To store the URI of the converted file (either FileProvider or MediaStore)
    private String mOutputDisplayName; // To store the display name, useful for MediaStore URIs


    private static final ConversionParameters CONV_PARAMS_720P = new ConversionParameters(720, MediaConverter.VIDEO_CODEC_H264,  4000000, 192000);
    private static final ConversionParameters CONV_PARAMS_720P_H265 = new ConversionParameters(720, MediaConverter.VIDEO_CODEC_H265,  2000000, 192000);
    private static final ConversionParameters CONV_PARAMS_1080P = new ConversionParameters(1080, MediaConverter.VIDEO_CODEC_H264, 6000000, 192000);
    private static final ConversionParameters CONV_PARAMS_1080P_H265_3000kbps = new ConversionParameters(1080, MediaConverter.VIDEO_CODEC_H265,  3000000, 192000);
    private static final ConversionParameters CONV_PARAMS_1080P_H265_6000kbps = new ConversionParameters(1080, MediaConverter.VIDEO_CODEC_H265,  6000000, 192000);
    private static final ConversionParameters CONV_PARAMS_1440P_H265_9000kbps = new ConversionParameters(1440, MediaConverter.VIDEO_CODEC_H265,  9000000, 192000);
    private static final ConversionParameters CONV_PARAMS_2160P_H265_12000kbps = new ConversionParameters(2160, MediaConverter.VIDEO_CODEC_H265,  12000000, 192000);


    private SharedPreferences sharedPreferences;

    // Define SharedPreferences constants
    private static final String PREFS_NAME = "AppVersionPrefs";
    private static final String PREF_LAST_RUN_VERSION_NAME = "last_run_version_name";
    private static final String NO_VERSION_SAVED = "NO_VERSION_SAVED"; // A default value

    // Tag for logging version check related messages
    private static final String TAG_VERSION_CHECK = "MainActivityVersionCheck";
    private final ActivityResultLauncher<Intent> pickVideoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    onVideoSelected(result.getData());
                } else {
                    if (!mMainViewModel.isUriLoaded()) {
                        finish();
                    }
                }
            });

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()     // Log it but don't crash
                    .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }

        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        setContentView(R.layout.main);

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            // 1. Set the logo
            actionBar.setLogo(R.drawable.ic_launcher_actionbar); // Replace your_app_logo with your drawable resource ID

            // 2. Display the logo
            actionBar.setDisplayUseLogoEnabled(true);

            // 3. Show the title (app name)
            // This is usually true by default, but good to be explicit
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);

            // actionBar.setTitle(R.string.your_app_name_custom);
        }

        mConverter = Converter.getInstance(getApplication());

        mMainLayout = findViewById(R.id.main);
        mLoadingProgressBar = findViewById(R.id.loading_progress_bar);
        mInputInfoView = findViewById(R.id.input_info);
        mOutputInfoView = findViewById(R.id.output_info);
        mElapsedTimeView = findViewById(R.id.current_time);
        mTimelineRangeBar = findViewById(R.id.range_seek_bar);
        mTimelineView = findViewById(R.id.video_thumbnails);

        mTrimInfo = findViewById(R.id.trim_info);
        mThumbView = findViewById(R.id.thumb);
        mConversionProgressBar = findViewById(R.id.progress_bar);
        mConversionProgressBar.setMax(100);
        mConvertButton = findViewById(R.id.convert);
        mConvertButton.setOnClickListener(v -> {
            if (mConverter.isConverted()) {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), FILE_PROVIDER_AUTHORITY, mConverter.getConvertedFile()), "video/*");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } else if (mConverter.isConverting()) {
                mConverter.cancel();
            } else {
                convert();
            }
            updateControls();
        });

        findViewById(R.id.input_pick).setOnClickListener(v -> pickVideo());

        findViewById(R.id.input_play).setOnClickListener(v -> {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), FILE_PROVIDER_AUTHORITY, Objects.requireNonNull(mMainViewModel.getLoadedFile())), "video/*");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        });

        mOutputPlayButton = this.findViewById(R.id.output_play);
        mOutputPlayButton.setOnClickListener(v -> {
            if (mOutputUri != null) { // Check mOutputUri instead of mConverter.isConverted() directly
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(mOutputUri, "video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Crucial for content URIs and FileProvider
                startActivity(intent);
            } else if (mConverter.isConverting()) {
                Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getBaseContext(), R.string.please_select_video, Toast.LENGTH_SHORT).show();
            }
        });

        mOutputSendButton = this.findViewById(R.id.output_share);
        mOutputSendButton.setOnClickListener(v -> {
            if (mOutputUri != null) { // Check mOutputUri
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("video/*");
                intent.putExtra(Intent.EXTRA_STREAM, mOutputUri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Crucial
                startActivity(Intent.createChooser(intent, "Share video using"));
            } else if (mConverter.isConverting()) {
                Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getBaseContext(), R.string.please_select_video, Toast.LENGTH_SHORT).show();            }
        });

        mOutputOptionsButton = this.findViewById(R.id.output_options);
        mOutputOptionsButton.setOnClickListener(v -> {
            if (!mConverter.isConverting()) {
                onOutputOptions();
            } else {
                Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            }
        });

        mMainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        mMainViewModel.getLoadUriResultLiveData().observe(this, this::onUriLoaded);

        if (savedInstanceState == null) {
            if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
                final Uri uri = getIntent().getExtras() != null ? getIntent().getExtras().getParcelable(Intent.EXTRA_STREAM) : null;
                if (uri != null) {
                    loadUri(uri);
                } else {
                    pickVideo();
                }
            } else {
                pickVideo();
                //loadUri(Uri.fromFile(new File("/storage/emulated/0/Android/data/com.dstukalov.videoconverter/files/tmp.mp4")));
            }
        } else {
            mConversionParameters = savedInstanceState.getParcelable("conversion_parameters");
        }

        mConverter.progress.observe(this, progress -> {
            if (progress != null) {
                mConversionProgressBar.setIndeterminate(progress.percent >= 100);
                mConversionProgressBar.setProgress(progress.percent);
                mElapsedTimeView.setText(getString(R.string.seconds_elapsed, progress.elapsedTime / 1000L));
            }
            updateControls();
        });

        mConverter.result.observe(this, result -> {
            if (result != null) {
                    if (result.uri != null) { // Q+ path, MediaStore URI
                        mOutputUri = result.uri;
                        mOutputDisplayName = result.displayName;
                        Log.i(TAG, "Conversion successful. Output URI (MediaStore): " + mOutputUri);
                        mOutputInfoView.setText(getString(R.string.video_info_output, // Use a different string if needed
                                result.width, // Handle if width/height not set in Result
                                result.height,
                                DateUtils.formatElapsedTime(result.duration / 1000),
                                Formatter.formatShortFileSize(MainActivity.this, result.fileLength)));
                        Toast.makeText(getBaseContext(),getString(R.string.file_save_location) + (mOutputDisplayName != null ? "" + mOutputDisplayName : ""), Toast.LENGTH_LONG).show();

                    } else if (result.file != null) { // Pre-Q path, File object
                        // For File objects, always generate a FileProvider URI for sharing/viewing
                        mOutputUri = FileProvider.getUriForFile(
                                MainActivity.this,
                                FILE_PROVIDER_AUTHORITY, // Make sure this matches your manifest
                                result.file
                        );
                        mOutputDisplayName = result.file.getName();
                        Log.i(TAG, "Conversion successful. Output URI (FileProvider): " + mOutputUri);
                        // Update UI related to output info
                        mOutputInfoView.setText(getString(R.string.video_info_output, // Use a different string if needed
                                result.width, // Handle if width/height not set in Result
                                result.height,
                                DateUtils.formatElapsedTime(result.duration / 1000),
                                Formatter.formatShortFileSize(MainActivity.this, result.fileLength)));
                        Toast.makeText(getBaseContext(),getString(R.string.file_save_location) + (mOutputDisplayName != null ? "" + mOutputDisplayName : ""), Toast.LENGTH_LONG).show();
                    } else {
                        mOutputUri = null; // Should not happen if isSuccess is true
                        mOutputDisplayName = null;
                        Toast.makeText(getBaseContext(), R.string.conversion_failed, Toast.LENGTH_SHORT).show();
                    }

                    mElapsedTimeView.setText(getString(R.string.seconds_elapsed, result.elapsedTime / 1000)); // Assuming elapsedTime is in Result
                 }
                updateControls();
        });
    }

    @Override
    protected void onSaveInstanceState(final @NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.i(TAG, "onSaveInstanceState");
        outState.putParcelable("conversion_parameters", mConversionParameters);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");

        if (mFramePreview != null) {
            mFramePreview.interrupt();
            mFramePreview = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId(); // Get item id once

        if (itemId == R.id.id_library) {
            Intent libraryIntent = new Intent(this, LibraryActivity.class);
            startActivity(libraryIntent);
            return true; // Return true to indicate the event was handled
        } else if (itemId == R.id.action_launch_about) {
            Intent aboutIntent = new Intent(this, AboutActivity.class);
            startActivity(aboutIntent);
            return true; // Return true to indicate the event was handled
        }
        return super.onOptionsItemSelected(item);
    }

    private void onOutputOptions() {
        final PopupMenu popup = new PopupMenu(this, mOutputOptionsButton);
        popup.getMenuInflater().inflate(R.menu.output_options, popup.getMenu());
        if (MediaConverter.selectCodec(MediaConverter.VIDEO_CODEC_H265) == null) {
            popup.getMenu().removeItem(R.id.quality_720p_h265);
            popup.getMenu().removeItem(R.id.quality_1080p_h265_3000kbps);
            popup.getMenu().removeItem(R.id.quality_1080p_h265_6000kbps);
            popup.getMenu().removeItem(R.id.quality_1440p_h265_9000kbps);
            popup.getMenu().removeItem(R.id.quality_2160p_h265_12000kbps);
        }
        if (CONV_PARAMS_720P.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_720p).setChecked(true);
        } else if (CONV_PARAMS_720P_H265.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_720p_h265).setChecked(true);
        } else if (CONV_PARAMS_1080P.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_1080p).setChecked(true);
        } else if (CONV_PARAMS_1080P_H265_3000kbps.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_1080p_h265_3000kbps).setChecked(true);
        } else if (CONV_PARAMS_1080P_H265_6000kbps.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_1080p_h265_6000kbps).setChecked(true);
        } else if (CONV_PARAMS_1440P_H265_9000kbps.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_1440p_h265_9000kbps).setChecked(true);
        } else if (CONV_PARAMS_2160P_H265_12000kbps.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_2160p_h265_12000kbps).setChecked(true);
        }
        popup.setOnMenuItemClickListener(item -> {
            final int itemId = item.getItemId();
            if (itemId == R.id.quality_720p) {
                mConversionParameters = CONV_PARAMS_720P;
            } else if (itemId == R.id.quality_720p_h265) {
                mConversionParameters = CONV_PARAMS_720P_H265;
            } else if (itemId == R.id.quality_1080p) {
                mConversionParameters = CONV_PARAMS_1080P;
            } else if (itemId == R.id.quality_1080p_h265_3000kbps) {
                mConversionParameters = CONV_PARAMS_1080P_H265_3000kbps;
            } else if (itemId == R.id.quality_1080p_h265_6000kbps) {
                mConversionParameters = CONV_PARAMS_1080P_H265_6000kbps;
            } else if (itemId == R.id.quality_1440p_h265_9000kbps) {
                mConversionParameters = CONV_PARAMS_1440P_H265_9000kbps;
            } else if (itemId == R.id.quality_2160p_h265_12000kbps) {
                mConversionParameters = CONV_PARAMS_2160P_H265_12000kbps;
            }
            Log.i(TAG, "onOutputOptions selected " + mConversionParameters);
            estimateOutput();
            return true;
        });

        popup.show();
    }

    private void initInputData(@NonNull MainViewModel.LoadUriResult result) {

        final File file = Objects.requireNonNull(result.file);

        if (mFramePreview != null) {
            mFramePreview.interrupt();
            mFramePreview = null;
        }

        mFramePreview = new FramePreview(file.getAbsolutePath(), mThumbView);
        mFramePreview.requestShowFrame(0);

        // to fill image view with proper width/height until we get thumbnail
        mThumbView.setImageBitmap(Bitmap.createScaledBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                result.width, result.height, false));

        mInputInfoView.setText(getString(R.string.video_info,
                result.width, result.height,
                DateUtils.formatElapsedTime(result.duration / 1000),
                Formatter.formatShortFileSize(this, result.fileLength)));

        mTimelineView.setVideoFile(file.getAbsolutePath());

        mTimelineRangeBar.setDuration(result.duration);
        mTimelineRangeBar.setMinRange(1000L);
        mTimelineRangeBar.setRange(0, result.duration);
        mTimelineRangeBar.setCallback(new TimelineRangeBar.Callback() {

            final Runnable mHideTrimInfoRunnable = new Runnable() {
                @Override
                public void run() {
                    mTrimInfo.setVisibility(View.GONE);
                    final Animation fadeOut = new AlphaAnimation(1, 0);
                    fadeOut.setDuration(600);
                    mTrimInfo.startAnimation(fadeOut);
                }
            };

            @Override
            public void onRangeChanged(long position, long timeFrom, long timeTo, @TimelineRangeBar.MotionEdge int motionEdge) {
                if (mFramePreview != null) {
                    mFramePreview.requestShowFrame(position);
                }

                if (mTrimInfo.getVisibility() != View.VISIBLE) {
                    mTrimInfo.setVisibility(View.VISIBLE);
                    final Animation fadeIn = new AlphaAnimation(0, 1);
                    fadeIn.setDuration(100);
                    mTrimInfo.startAnimation(fadeIn);
                }
                mTrimInfo.removeCallbacks(mHideTrimInfoRunnable);
                mTrimInfo.postDelayed(mHideTrimInfoRunnable, 1000);
                mTrimInfo.setText(getString(R.string.trim_info,
                        DateUtils.formatElapsedTime(timeFrom / 1000),
                        DateUtils.formatElapsedTime(timeTo / 1000),
                        DateUtils.formatElapsedTime((timeTo - timeFrom) / 1000)));

                estimateOutput();
            }
        });

        mElapsedTimeView.setText("");

        estimateOutput();
    }

    private void estimateOutput() {
        final MainViewModel.LoadUriResult loadUriResult = mMainViewModel.getLoadUriResultLiveData().getValue();
        if (loadUriResult != null) {
            int dstWidth;
            int dstHeight;
            if (loadUriResult.width <= loadUriResult.height) {
                dstWidth = mConversionParameters.mVideoResolution;
                dstHeight = loadUriResult.height * dstWidth / loadUriResult.width;
                dstHeight = dstHeight & ~3;
            } else {
                dstHeight = mConversionParameters.mVideoResolution;
                dstWidth = loadUriResult.width * dstHeight / loadUriResult.height;
                dstWidth = dstWidth & ~3;
            }
            final long duration = (mTimelineRangeBar.getTimeTo() - mTimelineRangeBar.getTimeFrom()) / 1000;
            final long estimatedSize = (mConversionParameters.mVideoBitrate + mConversionParameters.mAudioBitrate) * duration / 8;

            mOutputInfoView.setText(getString(R.string.video_info_output, dstWidth, dstHeight, DateUtils.formatElapsedTime(duration), Formatter.formatShortFileSize(this, estimatedSize)));
        } else {
            mOutputInfoView.setText(null);
        }
    }

    private void pickVideo() {

        if (mConverter.isConverting()) {
            Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }

        final Intent pickIntent = new Intent(Intent.ACTION_PICK);
        pickIntent.setDataAndType(android.provider.MediaStore.Video.Media.INTERNAL_CONTENT_URI, "video/*");
        final Intent captureIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        final Intent reselectIntent = new Intent("com.dstukalov.videoconverter.intent.action.RESELECT", null);

        final Intent chooserIntent = Intent.createChooser(pickIntent, getString(R.string.select_video));

        if (mConverter.isConverted()) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{reselectIntent, captureIntent});
        } else {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{captureIntent});
        }

        pickVideoLauncher.launch(chooserIntent);
    }

    private void onVideoSelected(final Intent data) {
        if (data == null) {
            Toast.makeText(getBaseContext(), R.string.bad_video, Toast.LENGTH_SHORT).show();
            if (!mMainViewModel.isUriLoaded()) {
                pickVideo();
            }
        } else if (data.getBooleanExtra(ReselectActivity.RESELECT_EXTRA, false)) {
            mConverter.reset();
            updateControls();
            estimateOutput();
            mElapsedTimeView.setText("");
        } else {
            final Uri uri = data.getData();
            if (uri != null) {
                loadUri(uri);
            } else {
                Toast.makeText(getBaseContext(), R.string.bad_video, Toast.LENGTH_SHORT).show();
                if (!mMainViewModel.isUriLoaded()) {
                    pickVideo();
                }
            }
        }
    }

    private void updateControls() {
        if (mMainViewModel.isUriLoaded()) {
            mMainLayout.setVisibility(View.VISIBLE);
            if (mConverter.isConverted()) {
                mConversionProgressBar.setVisibility(View.INVISIBLE);
                mTimelineRangeBar.setVisibility(View.INVISIBLE);
                mTimelineView.setVisibility(View.INVISIBLE);
                mConvertButton.setText(R.string.convert);
                mConvertButton.setVisibility(View.INVISIBLE);
                mOutputOptionsButton.setVisibility(View.GONE);
                mOutputPlayButton.setVisibility(View.VISIBLE);
                mOutputSendButton.setVisibility(View.VISIBLE);
            } else if (mConverter.isConverting()) {
                mConversionProgressBar.setVisibility(View.VISIBLE);
                mTimelineRangeBar.setVisibility(View.GONE);
                mTimelineView.setVisibility(View.VISIBLE);
                mConvertButton.setText(R.string.cancel);
                mConvertButton.setVisibility(View.VISIBLE);
                mOutputOptionsButton.setVisibility(View.VISIBLE);
                mOutputPlayButton.setVisibility(View.GONE);
                mOutputSendButton.setVisibility(View.GONE);
            } else {
                mConversionProgressBar.setVisibility(View.INVISIBLE);
                mTimelineRangeBar.setVisibility(View.VISIBLE);
                mTimelineView.setVisibility(View.VISIBLE);
                mConvertButton.setText(R.string.convert);
                mConvertButton.setVisibility(View.VISIBLE);
                mOutputOptionsButton.setVisibility(View.VISIBLE);
                mOutputPlayButton.setVisibility(View.GONE);
                mOutputSendButton.setVisibility(View.GONE);
            }
        } else {
            mMainLayout.setVisibility(View.INVISIBLE);
        }
    }

    private void onInputDataLoadedAndUiReady() {
        Log.d(TAG_VERSION_CHECK, "Input data loaded and UI is ready. Starting app version check task.");
        // Start the AsyncTask to perform checks in the background
        new CheckAppVersionTask(this).execute();
    }

    // Static inner class to prevent leaks, using WeakReference to access Activity
    private static class CheckAppVersionTask extends AsyncTask<Void, Void, CheckAppVersionTask.VersionCheckResult> {

        private final WeakReference<MainActivity> activityReference;

        // Constructor
        CheckAppVersionTask(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }

        // Helper class to hold the result of background work
        static class VersionCheckResult {
            final String dialogTitle;
            final String dialogMessage;
            final String currentVersionToSave; // Version to save after dialog

            VersionCheckResult(String title, String message, String currentVersion) {
                this.dialogTitle = title;
                this.dialogMessage = message;
                this.currentVersionToSave = currentVersion;
            }
        }

        @Override
        protected VersionCheckResult doInBackground(Void... voids) {
            MainActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                return null; // Activity is gone, no work to do
            }

            String importantInfoTextMessage = activity.getString(R.string.important_info_text_message);
            String appUpdatedTextMessage = activity.getString(R.string.app_updated_text_message);
            String currentVersionName = activity.getCurrentAppVersionNameInternal(); // Background safe version
            String lastRunVersionName = activity.sharedPreferences.getString(PREF_LAST_RUN_VERSION_NAME, NO_VERSION_SAVED);

            Log.d(TAG_VERSION_CHECK, "Background Check - Current Version: " + currentVersionName + ", Last Run Version: " + lastRunVersionName);

            String title = null;
            String message = null;

            if (NO_VERSION_SAVED.equals(lastRunVersionName)) {
                title = "Welcome!";
                message = "Welcome to Video Compressor \nWelcome to version " + currentVersionName + "\n" + importantInfoTextMessage;
            } else if (!currentVersionName.equals(lastRunVersionName)) {
                title = "App Updated!";
                message = "Welcome to Video Compressor \nYou've updated to version " + currentVersionName + "\n" + appUpdatedTextMessage;
            } else {
                Log.d(TAG_VERSION_CHECK, "App version is the same as the last run.");
            }

            // Always prepare to save the current version, even if no dialog is shown immediately
            // The actual save will happen on the main thread after any dialog.
            if (title != null && message != null) {
                return new VersionCheckResult(title, message, currentVersionName);
            } else {
                // No dialog needed, but still need to save the current version if it changed
                // or if it's the first run (to mark it as no longer NO_VERSION_SAVED)
                if (!currentVersionName.equals(lastRunVersionName) || NO_VERSION_SAVED.equals(lastRunVersionName)) {
                    // We create a result just to pass the version to save, even if no dialog.
                    // Or, you could save it directly here IF `apply()` is truly non-blocking for your StrictMode.
                    // To be safe, we'll pass it to onPostExecute.
                    return new VersionCheckResult(null, null, currentVersionName);
                }
                return null; // No dialog, and version is the same as last run.
            }
        }

        @Override
        protected void onPostExecute(VersionCheckResult result) {
            MainActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                return; // Activity is gone
            }

            if (result != null) {
                // Show dialog if title and message are present
                if (result.dialogTitle != null && result.dialogMessage != null) {
                    activity.showVersionAlertDialogInternal(result.dialogTitle, result.dialogMessage);
                }

                // Save the current version name now on the main thread (apply() is async)
                // This happens after any dialog is shown (or if no dialog was needed but version changed/was first run)
                if (result.currentVersionToSave != null) {
                    Log.d(TAG_VERSION_CHECK, "Saving current version to SharedPreferences: " + result.currentVersionToSave);
                    activity.sharedPreferences.edit().putString(PREF_LAST_RUN_VERSION_NAME, result.currentVersionToSave).apply();
                }
            }
        }
    }

    // This method is now safe to call from the background task to get version name
    // as it doesn't directly interact with UI elements for this specific task.
    // It's effectively a utility method for the AsyncTask.
    private String getCurrentAppVersionNameInternal() {
        try {
            // This is generally safe as string resources are cached.
            return getString(R.string.dynamic_version_name);
        } catch (Exception e) {
            Log.e(TAG_VERSION_CHECK, "Could not get version name from resources. Falling back.", e);
            try {
                // This part (PackageManager) is slow and was a StrictMode violation.
                // It's now called within doInBackground.
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException pmEx) {
                Log.e(TAG_VERSION_CHECK, "Could not get version name from PackageManager either.", pmEx);
                return "Unknown";
            }
        }
    }
    // Renamed to avoid confusion, this is called from onPostExecute (Main Thread)
    private void showVersionAlertDialogInternal(String title, String message) {
        if (isFinishing() || isDestroyed()) { // Check if activity is still valid
            return;
        }
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.AppAlertDialogStyle);
        new AlertDialog.Builder(themedContext)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }
    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private void convert() {
        if (mMainViewModel.getLoadedFile() == null) {
            Toast.makeText(this, R.string.please_select_video, Toast.LENGTH_SHORT).show();
            return;
        }

        MainViewModel.LoadUriResult loadedVideoInfo = mMainViewModel.getLoadUriResultLiveData().getValue();
        if (loadedVideoInfo == null || loadedVideoInfo.file == null) {
            Toast.makeText(this, R.string.video_not_fully_loaded, Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Get Original Filename (without extension)
        String originalFileNameWithExt = loadedVideoInfo.originalFileName;
        if (originalFileNameWithExt == null || originalFileNameWithExt.isEmpty()) {
            // Fallback if originalFileName is somehow not set
            originalFileNameWithExt = "video_" + dateFormat.format(new Date()); // Or some other default
        }

        String originalFileNameWithoutExt = originalFileNameWithExt;
        int lastDot = originalFileNameWithExt.lastIndexOf('.');
        if (lastDot > 0 && lastDot < originalFileNameWithExt.length() - 1) { // Check if '.' is not the first or last char
            originalFileNameWithoutExt = originalFileNameWithExt.substring(0, lastDot);
        }
        // Sanitize the original filename part to remove characters invalid for filenames
        originalFileNameWithoutExt = originalFileNameWithoutExt.replaceAll("[^a-zA-Z0-9_.-]", "_");


        // 2. Get Video Width
        int videoWidth = mConversionParameters.mVideoResolution; // Assuming 'width' is available and populated

        // 3. Construct the new filename
        // Format: original_filename_width_PREFIX.mp4
        // Example: my_cool_video_1280_VID_CONVERTED_.mp4
        // You might want to add a timestamp back if original_filename + width could be repeated.
        // For example, if a user converts the same video at the same width multiple times.
        // String timestampForUniqueness = dateFormat.format(new Date());
        // final String fileName = originalFileNameWithoutExt + "_" + videoWidth + "p_" +
        //                         Converter.CONVERTED_VIDEO_PREFIX + "_" + timestampForUniqueness + ".mp4";

        // Let's refine the prefix usage for clarity.
        // If CONVERTED_VIDEO_PREFIX is "VID_CONVERTED_", then:
        // myvideo_1280_VID_CONVERTED_.mp4 (if prefix ends with _)
        // myvideo_1280_VID_CONVERTED.mp4 (if prefix doesn't end with _)
        // It's usually good to have separators.
        final String fileName = originalFileNameWithoutExt + "_" +
                videoWidth + "p" + // Added "p" for pixels, e.g., "1280p"
                Converter.CONVERTED_VIDEO_PREFIX + // Assuming this ends with an underscore or is clear
                ".mp4";


        Log.i(TAG, "Generated output filename: " + fileName);

        if (mConverter.isConverting()) {
            Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        long timeFrom = mTimelineRangeBar.getTimeFrom();
        long timeTo = mTimelineRangeBar.getTimeTo();
        if (timeTo == mTimelineRangeBar.getDuration()) {
            timeTo = 0;
        }

        mConverter.convert(Objects.requireNonNull(mMainViewModel.getLoadedFile()), fileName, timeFrom, timeTo, mConversionParameters.mVideoResolution, mConversionParameters.mVideoCodec,
                mConversionParameters.mVideoBitrate, mConversionParameters.mAudioBitrate);
    }

    private void loadUri(final @NonNull Uri uri) {
        mMainLayout.setVisibility(View.INVISIBLE);
        mLoadingProgressBar.setVisibility(View.VISIBLE);

        mMainViewModel.loadUri(uri);
    }

    private void onUriLoaded(@NonNull MainViewModel.LoadUriResult result) {
        mLoadingProgressBar.setVisibility(View.GONE);
        if (result.isOk()) {
            mConverter.reset();
            updateControls();
            initInputData(result);
            onInputDataLoadedAndUiReady();
        } else {
            Toast.makeText(getBaseContext(), R.string.bad_video, Toast.LENGTH_SHORT).show();
            pickVideo();
        }
    }

    private static class ConversionParameters implements Parcelable  {

        final int mVideoResolution;
        final @MediaConverter.VideoCodec String mVideoCodec;
        final int mVideoBitrate;
        final int mAudioBitrate;

        public static final Parcelable.Creator<ConversionParameters> CREATOR = new Parcelable.Creator<ConversionParameters>() {
            public ConversionParameters createFromParcel(Parcel in) {
                return new ConversionParameters(in);
            }

            public ConversionParameters[] newArray(int size) {
                return new ConversionParameters[size];
            }
        };

        ConversionParameters(int videoResolution, @NonNull @MediaConverter.VideoCodec String videoCodec, int videoBitrate, int audioBitrate) {
            mVideoResolution = videoResolution;
            mVideoCodec = videoCodec;
            mVideoBitrate = videoBitrate;
            mAudioBitrate = audioBitrate;
        }

        ConversionParameters(Parcel in) {
            mVideoResolution = in.readInt();
            mVideoCodec = in.readString();
            mVideoBitrate = in.readInt();
            mAudioBitrate = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mVideoResolution);
            dest.writeString(mVideoCodec);
            dest.writeInt(mVideoBitrate);
            dest.writeInt(mAudioBitrate);
        }



        @Override
        public @NonNull String toString() {
            return mVideoResolution + "p " + mVideoCodec + " video:" + mVideoBitrate + "kbps audio:" + mAudioBitrate + "kbps";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConversionParameters that = (ConversionParameters) o;
            return mVideoResolution == that.mVideoResolution && mVideoBitrate == that.mVideoBitrate && mAudioBitrate == that.mAudioBitrate && mVideoCodec.equals(that.mVideoCodec);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mVideoResolution, mVideoCodec, mVideoBitrate, mAudioBitrate);
        }
    }
}
