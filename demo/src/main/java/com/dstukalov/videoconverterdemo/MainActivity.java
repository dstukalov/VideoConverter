package com.dstukalov.videoconverterdemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Log;
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
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.dstukalov.videoconverter.MediaConverter;

import java.io.File;
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
    private ConversionParameters mConversionParameters = CONV_PARAMS_360P;
    private MainViewModel mMainViewModel;

    private static final ConversionParameters CONV_PARAMS_240P = new ConversionParameters(240, MediaConverter.VIDEO_CODEC_H264, 1333000, 64000);
    private static final ConversionParameters CONV_PARAMS_360P = new ConversionParameters(360, MediaConverter.VIDEO_CODEC_H264, 2000000, 96000);
    private static final ConversionParameters CONV_PARAMS_480P = new ConversionParameters(480, MediaConverter.VIDEO_CODEC_H264, 2666000, 128000);
    private static final ConversionParameters CONV_PARAMS_720P = new ConversionParameters(720, MediaConverter.VIDEO_CODEC_H264,  4000000, 192000);
    private static final ConversionParameters CONV_PARAMS_720P_H265 = new ConversionParameters(720, MediaConverter.VIDEO_CODEC_H265,  2000000, 192000);
    private static final ConversionParameters CONV_PARAMS_1080P = new ConversionParameters(1080, MediaConverter.VIDEO_CODEC_H264, 6000000, 192000);
    private static final ConversionParameters CONV_PARAMS_1080P_H265 = new ConversionParameters(1080, MediaConverter.VIDEO_CODEC_H265,  3000000, 192000);

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
                    .detectAll()
                    .penaltyDeathOnNetwork()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .penaltyDeath()
                    .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }

        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        setContentView(R.layout.main);

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
                intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), FILE_PROVIDER_AUTHORITY, mConverter.getConvertFile()), "video/*");
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
            if (mConverter.isConverted()) {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), FILE_PROVIDER_AUTHORITY, mConverter.getConvertFile()), "video/*");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } else if (mConverter.isConverting()) {
                Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getBaseContext(), R.string.please_select_video, Toast.LENGTH_SHORT).show();
            }
        });

        mOutputSendButton = this.findViewById(R.id.output_share);
        mOutputSendButton.setOnClickListener(v -> {
            if (mConverter.isConverted()) {
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("video/*");
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getBaseContext(), FILE_PROVIDER_AUTHORITY, mConverter.getConvertFile()));
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } else if (mConverter.isConverting()) {
                Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getBaseContext(), R.string.please_select_video, Toast.LENGTH_SHORT).show();
            }
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
                if (result.file != null) {
                    mOutputInfoView.setText(getString(R.string.video_info,
                            result.width,
                            result.height,
                            DateUtils.formatElapsedTime(result.duration / 1000),
                            Formatter.formatShortFileSize(MainActivity.this, result.fileLength)));

                    mElapsedTimeView.setText(getString(R.string.seconds_elapsed, result.elapsedTime / 1000));
                } else if (result.exception != null) {
                    Toast.makeText(getBaseContext(), R.string.conversion_failed, Toast.LENGTH_LONG).show();
                    mElapsedTimeView.setText("");
                }
                updateControls();
            }
        });

        updateControls();
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

    private void onOutputOptions() {
        final PopupMenu popup = new PopupMenu(this, mOutputOptionsButton);
        popup.getMenuInflater().inflate(R.menu.output_options, popup.getMenu());
        if (MediaConverter.selectCodec(MediaConverter.VIDEO_CODEC_H265) == null) {
            popup.getMenu().removeItem(R.id.quality_720p_h265);
            popup.getMenu().removeItem(R.id.quality_1080p_h265);
        }
        if (CONV_PARAMS_240P.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_240p).setChecked(true);
        } else if (CONV_PARAMS_360P.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_360p).setChecked(true);
        } else if (CONV_PARAMS_480P.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_480p).setChecked(true);
        } else if (CONV_PARAMS_720P.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_720p).setChecked(true);
        } else if (CONV_PARAMS_720P_H265.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_720p_h265).setChecked(true);
        } else if (CONV_PARAMS_1080P.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_1080p).setChecked(true);
        } else if (CONV_PARAMS_1080P_H265.equals(mConversionParameters)) {
            popup.getMenu().findItem(R.id.quality_1080p_h265).setChecked(true);
        }
        popup.setOnMenuItemClickListener(item -> {
            final int itemId = item.getItemId();
            if (itemId == R.id.quality_240p) {
                mConversionParameters = CONV_PARAMS_240P;
            } else if (itemId == R.id.quality_360p) {
                mConversionParameters = CONV_PARAMS_360P;
            } else if (itemId == R.id.quality_480p) {
                mConversionParameters = CONV_PARAMS_480P;
            } else if (itemId == R.id.quality_720p) {
                mConversionParameters = CONV_PARAMS_720P;
            } else if (itemId == R.id.quality_720p_h265) {
                mConversionParameters = CONV_PARAMS_720P_H265;
            } else if (itemId == R.id.quality_1080p) {
                mConversionParameters = CONV_PARAMS_1080P;
            } else if (itemId == R.id.quality_1080p_h265) {
                mConversionParameters = CONV_PARAMS_1080P_H265;
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

    private void convert() {
        if (mConverter.isConverting()) {
            Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        long timeFrom = mTimelineRangeBar.getTimeFrom();
        long timeTo = mTimelineRangeBar.getTimeTo();
        if (timeTo == mTimelineRangeBar.getDuration()) {
            timeTo = 0;
        }
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        final String fileName = Converter.CONVERTED_VIDEO_PREFIX + dateFormat.format(new Date()) + ".mp4";

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

        public static final Parcelable.Creator<ConversionParameters> CREATOR = new Parcelable.Creator<>() {
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
