package com.dstukalov.videoconverterdemo;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;

import com.dstukalov.videoconverter.BadMediaException;
import com.dstukalov.videoconverter.MediaConversionException;
import com.dstukalov.videoconverter.MediaConverter;
import com.dstukalov.videoconverter.Muxer;
import com.innovattic.rangeseekbar.RangeSeekBar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "video-converter";

    private static final int ACTIVITY_REQUEST_CODE_PICK_VIDEO = 239;

    private static final String FILE_PROVIDER_AUTHORITY = "com.dstukalov.videoconverter.fileprovider";

    private View mMainLayout;
    private TextView mInputInfoView;
    private TextView mOutputInfoView;
    private ImageView mThumbView;
    private Button mConvertButton;
    private ProgressBar mConversionProgressBar;
    private ProgressBar mLoadingProgressBar;
    private TextView mTimeView;
    private RangeSeekBar mRangeSeekBar;
    private VideoThumbnailsView mTimelineView;
    private TextView mTrimInfo;

    private View mOutputPlayButton;
    private View mOutputSendButton;
    private View mOutputOptionsButton;

    private @Nullable PreviewThread mPreviewThread;

    private ConversionTask mConversionTask;
    private boolean mConverted;

    private File mOutputFolder;

    private File mInputFile; // = new File("/storage/emulated/0/Android/data/com.dstukalov.videoconverter/files/Temp/tmp.mp4");
    private File mOutputFile;

    private int mWidth;
    private int mHeight;

    private ConversionParameters mConversionParameters = CONV_PARAMS_360P;
    private long mTimeFrom;
    private long mTimeTo;

    private static final ConversionParameters CONV_PARAMS_240P = new ConversionParameters(240, MediaConverter.VIDEO_CODEC_H264, 1333000, 64000);
    private static final ConversionParameters CONV_PARAMS_360P = new ConversionParameters(360, MediaConverter.VIDEO_CODEC_H264, 2000000, 96000);
    private static final ConversionParameters CONV_PARAMS_480P = new ConversionParameters(480, MediaConverter.VIDEO_CODEC_H264, 2666000, 128000);
    private static final ConversionParameters CONV_PARAMS_720P = new ConversionParameters(720, MediaConverter.VIDEO_CODEC_H264,  4000000, 192000);
    private static final ConversionParameters CONV_PARAMS_720P_H265 = new ConversionParameters(720, MediaConverter.VIDEO_CODEC_H265,  2000000, 192000);
    private static final ConversionParameters CONV_PARAMS_1080P = new ConversionParameters(1080, MediaConverter.VIDEO_CODEC_H264, 6000000, 192000);
    private static final ConversionParameters CONV_PARAMS_1080P_H265 = new ConversionParameters(720, MediaConverter.VIDEO_CODEC_H265,  3000000, 192000);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOutputFolder = getExternalFilesDir(null);
        if (mOutputFolder == null || !mOutputFolder.mkdirs()) {
            Log.e(TAG, "cannot create " + mOutputFolder);
        }

        setContentView(R.layout.main);

        mMainLayout = findViewById(R.id.main);
        mLoadingProgressBar = findViewById(R.id.loading_progress_bar);
        mInputInfoView = findViewById(R.id.input_info);
        mOutputInfoView = findViewById(R.id.output_info);
        mTimeView = findViewById(R.id.current_time);
        mRangeSeekBar = findViewById(R.id.range_seek_bar);
        mTimelineView = findViewById(R.id.video_thumbnails);

        mTrimInfo = findViewById(R.id.trim_info);
        mThumbView = findViewById(R.id.thumb);
        mConversionProgressBar = findViewById(R.id.progress_bar);
        mConversionProgressBar.setMax(100);
        mConvertButton = findViewById(R.id.convert);
        mConvertButton.setOnClickListener(v -> {
            if (mConverted) {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), FILE_PROVIDER_AUTHORITY, mOutputFile), "video/*");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } else if (mConversionTask == null) {
                final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                mOutputFile = new File(mOutputFolder, "VID_CONVERTED_" + dateFormat.format(new Date()) + ".mp4");
                convert();
            } else {
                mConversionTask.cancel(true);
            }
            updateButtons();
        });

        findViewById(R.id.input_options).setOnClickListener(v -> onInputOptions());

        findViewById(R.id.input_play).setOnClickListener(v -> {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), FILE_PROVIDER_AUTHORITY, mInputFile), "video/*");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        });

        mOutputPlayButton = this.findViewById(R.id.output_play);
        mOutputPlayButton.setOnClickListener(v -> {
            if (mConverted) {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(FileProvider.getUriForFile(getBaseContext(), FILE_PROVIDER_AUTHORITY, mOutputFile), "video/*");
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } else if (mConversionTask == null) {
                Toast.makeText(getBaseContext(), R.string.please_select_video, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            }
        });

        mOutputSendButton = this.findViewById(R.id.output_share);
        mOutputSendButton.setOnClickListener(v -> {
            if (mConverted) {
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("video/*");
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getBaseContext(), FILE_PROVIDER_AUTHORITY, mOutputFile));
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } else if (mConversionTask == null) {
                Toast.makeText(getBaseContext(), R.string.please_select_video, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            }
        });

        mOutputOptionsButton = this.findViewById(R.id.output_options);
        mOutputOptionsButton.setOnClickListener(v -> {
            if (mConversionTask == null) {
                onOutputOptions();
            } else {
                Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            }
        });

        if (savedInstanceState != null) {
            final String inputPath = savedInstanceState.getString("input");
            if (inputPath != null) {
                mInputFile = new File(inputPath);
            }
        } else if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            final Uri uri = getIntent().getExtras() != null ? getIntent().getExtras().getParcelable(Intent.EXTRA_STREAM) : null;
            if (uri != null) {
                loadUri(uri);
            } else {
                pickVideo();
            }
        } else if (mInputFile == null || !mInputFile.exists()) {
            pickVideo();
        }

        if (mInputFile != null) {
            initInputData();
        }

        updateButtons();
    }

    @Override
    protected void onSaveInstanceState(final @NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mInputFile != null) {
            outState.putCharSequence("input", mInputFile.getAbsolutePath());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mPreviewThread != null) {
            mPreviewThread.interrupt();
            mPreviewThread = null;
        }
    }

    @Override
    protected void onActivityResult(final int request, final int result, final Intent data) {
        super.onActivityResult(request, result, data);
        switch (request) {
            case ACTIVITY_REQUEST_CODE_PICK_VIDEO: {
                if (result == Activity.RESULT_OK) {
                    if (data == null) {
                        Toast.makeText(getBaseContext(), R.string.bad_video, Toast.LENGTH_SHORT).show();
                        if (mInputFile == null) {
                            pickVideo();
                        }
                    } else if (data.getBooleanExtra(ReselectActivity.RESELECT_EXTRA, false)) {
                        mOutputFile = null;
                        mConverted = false;
                        updateButtons();
                        initInputData();
                    } else {
                        final Uri uri = data.getData();
                        if (uri != null) {
                            loadUri(uri);
                        } else {
                            Toast.makeText(getBaseContext(), R.string.bad_video, Toast.LENGTH_SHORT).show();
                            if (mInputFile == null) {
                                pickVideo();
                            }
                        }
                    }
                } else {
                    if (mInputFile == null) {
                        finish();
                    }
                }
                break;
            }
        }
    }

    private void onOutputOptions() {
        final PopupMenu popup = new PopupMenu(this, mOutputOptionsButton);
        popup.getMenuInflater().inflate(R.menu.output_options, popup.getMenu());
        if (MediaConverter.selectCodec(MediaConverter.VIDEO_CODEC_H265) == null) {
            popup.getMenu().removeItem(R.id.quality_720p_h265);
            popup.getMenu().removeItem(R.id.quality_1080p_h265);
        }
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.quality_240p:
                    mConversionParameters = CONV_PARAMS_240P;
                    break;
                case R.id.quality_360p:
                    mConversionParameters = CONV_PARAMS_360P;
                    break;
                case R.id.quality_480p:
                    mConversionParameters = CONV_PARAMS_480P;
                    break;
                case R.id.quality_720p:
                    mConversionParameters = CONV_PARAMS_720P;
                    break;
                case R.id.quality_720p_h265:
                    mConversionParameters = CONV_PARAMS_720P_H265;
                    break;
                case R.id.quality_1080p:
                    mConversionParameters = CONV_PARAMS_1080P;
                    break;
                case R.id.quality_1080p_h265:
                    mConversionParameters = CONV_PARAMS_1080P_H265;
                    break;
            }
            estimateOutput();
            return true;
        });

        popup.show();
    }

    private void onInputOptions() {
        pickVideo();
    }

    private void initInputData() {

        if (mInputFile == null) {
            return;
        }
        if (mPreviewThread != null) {
            mPreviewThread.interrupt();
            mPreviewThread = null;
        }

        final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();

        try {
            mediaMetadataRetriever.setDataSource(mInputFile.getAbsolutePath());
        } catch (Exception ex) {
            Toast.makeText(getBaseContext(), R.string.bad_video, Toast.LENGTH_SHORT).show();
            mediaMetadataRetriever.release();
            mInputFile = null;
            updateButtons();
            pickVideo();
            return;
        }

        final String width = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        final String height = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        int rotation;
        long duration;
        try {
            duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            rotation = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            mWidth = Integer.parseInt(width);
            mHeight = Integer.parseInt(height);
        } catch (NumberFormatException e) {
            Toast.makeText(getBaseContext(), R.string.bad_video, Toast.LENGTH_SHORT).show();
            mediaMetadataRetriever.release();
            mInputFile = null;
            updateButtons();
            pickVideo();
            return;
        }
        mediaMetadataRetriever.release();

        mPreviewThread = new PreviewThread(mInputFile.getAbsolutePath());
        mPreviewThread.setPriority(Thread.NORM_PRIORITY - 1);
        mPreviewThread.start();
        mPreviewThread.requestShowFrame(0);

        mTimeFrom = 0;
        mTimeTo = duration;

        // to fill image view with proper width/height until we get thumbnail
        mThumbView.setImageBitmap(Bitmap.createScaledBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                rotation % 180 == 90 ? mHeight : mWidth, rotation % 180 == 90 ? mWidth : mHeight, false));

        mInputInfoView.setText(getString(R.string.video_info, width, height,
                DateUtils.formatElapsedTime(duration / 1000),
                Formatter.formatShortFileSize(this, mInputFile.length())));

        mTimelineView.setVideoFile(mInputFile == null ? null : mInputFile.getAbsolutePath());

        mRangeSeekBar.setMax((int) duration);
        mRangeSeekBar.setSeekBarChangeListener(new RangeSeekBar.SeekBarChangeListener() {

            @Override
            public void onStartedSeeking() {
            }

            @Override
            public void onStoppedSeeking() {
                mRangeSeekBar.postDelayed(() -> {
                    mTrimInfo.setVisibility(View.GONE);
                    final Animation fadeOut = new AlphaAnimation(1, 0);
                    fadeOut.setDuration(600);
                    mTrimInfo.startAnimation(fadeOut);
                }, 1000);
            }

            @Override
            public void onValueChanged(int minValue, int maxValue) {
                if (mPreviewThread != null) {
                    if (mTimeFrom != minValue) {
                        mPreviewThread.requestShowFrame(minValue);
                    } else if (mTimeTo != maxValue) {
                        mPreviewThread.requestShowFrame(maxValue);
                    }
                }

                mTimeFrom = minValue;
                mTimeTo = maxValue;

                if (mTrimInfo.getVisibility() != View.VISIBLE) {
                    mTrimInfo.setVisibility(View.VISIBLE);
                    final Animation fadeIn = new AlphaAnimation(0, 1);
                    fadeIn.setDuration(100);
                    mTrimInfo.startAnimation(fadeIn);
                }
                mTrimInfo.setText(getString(R.string.trim_info,
                        DateUtils.formatElapsedTime(mTimeFrom / 1000),
                        DateUtils.formatElapsedTime(mTimeTo / 1000),
                        DateUtils.formatElapsedTime((mTimeTo - mTimeFrom) / 1000)));

                estimateOutput();

            }
        });

        mTimeView.setText("");

        estimateOutput();
    }

    private void estimateOutput() {
        int dstWidth;
        int dstHeight;
        if (mWidth <= mHeight) {
            dstWidth = mConversionParameters.mVideoResolution;
            dstHeight = mHeight * dstWidth / mWidth;
            dstHeight = dstHeight & ~3;
        } else {
            dstHeight = mConversionParameters.mVideoResolution;
            dstWidth = mWidth * dstHeight / mHeight;
            dstWidth = dstWidth & ~3;
        }
        final long duration = (mTimeTo - mTimeFrom) / 1000;
        final long estimatedSize = (mConversionParameters.mVideoBitrate + mConversionParameters.mAudioBitrate) * duration / 8;

        mOutputInfoView.setText(getString(R.string.video_info_output, dstWidth, dstHeight, DateUtils.formatElapsedTime(duration), Formatter.formatShortFileSize(this, estimatedSize)));
    }

    private void pickVideo() {

        if (mConversionTask != null) {
            Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }

        final Intent pickIntent = new Intent(Intent.ACTION_PICK);
        pickIntent.setDataAndType(android.provider.MediaStore.Video.Media.INTERNAL_CONTENT_URI, "video/*");
        final Intent captureIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        final Intent reselectIntent = new Intent("com.dstukalov.videoconverter.intent.action.RESELECT", null);
        final Intent chooserIntent = Intent.createChooser(pickIntent, getString(R.string.select_video));

        if (mConverted) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{reselectIntent, captureIntent});
        } else {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{captureIntent});
        }

        startActivityForResult(chooserIntent, ACTIVITY_REQUEST_CODE_PICK_VIDEO);
    }

    private void updateButtons() {
        if (mInputFile == null) {
            mMainLayout.setVisibility(View.INVISIBLE);
        } else {
            mMainLayout.setVisibility(View.VISIBLE);
            if (mConverted) {
                mConversionProgressBar.setVisibility(View.GONE);
                mRangeSeekBar.setVisibility(View.INVISIBLE);
                mTimelineView.setVisibility(View.INVISIBLE);
                mConvertButton.setText(R.string.convert);
                mConvertButton.setVisibility(View.INVISIBLE);
                mOutputOptionsButton.setVisibility(View.GONE);
                mOutputPlayButton.setVisibility(View.VISIBLE);
                mOutputSendButton.setVisibility(View.VISIBLE);
            } else if (mConversionTask == null) {
                mConversionProgressBar.setVisibility(View.GONE);
                mRangeSeekBar.setVisibility(View.VISIBLE);
                mTimelineView.setVisibility(View.VISIBLE);
                mConvertButton.setText(R.string.convert);
                mConvertButton.setVisibility(View.VISIBLE);
                mOutputOptionsButton.setVisibility(View.VISIBLE);
                mOutputPlayButton.setVisibility(View.GONE);
                mOutputSendButton.setVisibility(View.GONE);
            } else {
                mConversionProgressBar.setVisibility(View.VISIBLE);
                mRangeSeekBar.setVisibility(View.GONE);
                mTimelineView.setVisibility(View.VISIBLE);
                mConvertButton.setText(R.string.cancel);
                mConvertButton.setVisibility(View.VISIBLE);
                mOutputOptionsButton.setVisibility(View.VISIBLE);
                mOutputPlayButton.setVisibility(View.GONE);
                mOutputSendButton.setVisibility(View.GONE);
            }
        }
    }

    private void convert() {
        if (mConversionTask != null) {
            Toast.makeText(getBaseContext(), R.string.conversion_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        long timeFrom = mRangeSeekBar.getMinThumbValue();
        long timeTo = mRangeSeekBar.getMaxThumbValue();
        if (timeTo == mRangeSeekBar.getMax()) {
            timeTo = 0;
        }
        try {
            mConversionTask = new ConversionTask(mInputFile, mOutputFile, timeFrom, timeTo, mConversionParameters);
            mConversionTask.execute();
        } catch (FileNotFoundException e) {
            mConversionTask = null;
            Toast.makeText(getBaseContext(), R.string.conversion_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void loadUri(final @NonNull Uri uri) {
        mInputFile = null;
        mMainLayout.setVisibility(View.INVISIBLE);
        mLoadingProgressBar.setVisibility(View.VISIBLE);
        new LoadUriTask(uri).execute();
    }

    private class LoadUriTask extends AsyncTask<Void, Void, File> {

        final Uri mUri;

        LoadUriTask(final @NonNull Uri uri) {
            mUri = uri;
        }

        @Override
        protected File doInBackground(Void... voids) {
            File outFile = null;
            ContentResolver cr = getContentResolver();
            if (cr != null) {
                InputStream inputStream = null;
                OutputStream outputStream = null;
                try {
                    final File tmpFolder = new File(mOutputFolder, "Temp");
                    if (!tmpFolder.mkdirs()) {
                        Log.e(TAG, "cannot create " + tmpFolder);
                    }
                    outFile = new File(tmpFolder, "tmp.mp4");
                    inputStream = cr.openInputStream(mUri);
                    if (inputStream != null) {
                        outputStream = new FileOutputStream(outFile);
                        byte[] buffer = new byte[4096];
                        int n;
                        while ((n = inputStream.read(buffer, 0, buffer.length)) >= 0) {
                            outputStream.write(buffer, 0, n);
                        }
                    }
                } catch (SecurityException | IOException e) {
                    Log.w(TAG, "Unable to open stream", e);
                    outFile = null;
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            Log.w(TAG, "Unable to close stream", e);
                        }
                    }
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            Log.w(TAG, "Unable to close stream", e);
                        }
                    }
                }
            }
            return outFile;
        }

        @Override
        protected void onPostExecute(final File file) {
            mLoadingProgressBar.setVisibility(View.GONE);
            if (file != null) {
                mInputFile = file;
                mOutputFile = null;
                mConverted = false;
                updateButtons();
                initInputData();
            } else {
                Toast.makeText(getBaseContext(), R.string.bad_video, Toast.LENGTH_SHORT).show();
                pickVideo();
            }
        }
    }

    private class ConversionTask extends AsyncTask<Void, Integer, Boolean> {

        final MediaConverter mConverter;
        long mStartTime;
        WakeLock mWakeLock;

        ConversionTask(final @NonNull File input, final @NonNull File output, final long timeFrom, final long timeTo, final @NonNull ConversionParameters conversionParameters) throws FileNotFoundException {

            mConverter = new MediaConverter() {

                final String TAG = "H264";

                public Muxer createMuxer() throws IOException {
                    final Muxer muxer = super.createMuxer();
                    final FileOutputStream h264OutputStream = new FileOutputStream(output.getAbsolutePath() + ".h264");
                    final WritableByteChannel h264OutputChannel = h264OutputStream.getChannel();
                    return new Muxer() {

                        int h264TrackIndex = -1;
                        int frameNum;

                        @Override
                        public void start() throws IOException {
                            muxer.start();
                        }

                        @Override
                        public void stop() throws IOException {
                            muxer.stop();
                        }

                        @Override
                        public int addTrack(@NonNull MediaFormat format) throws IOException {
                            int trackIndex = muxer.addTrack(format);
                            final String mime = format.getString(MediaFormat.KEY_MIME);
                            if ("video/avc".equals(mime)) {
                                final ByteBuffer csd0 = format.getByteBuffer("csd-0");
                                final ByteBuffer csd1 = format.getByteBuffer("csd-1");
                                Log.i(TAG, "csd-0.position:" + csd0.position() + " remaining:" + csd0.remaining());
                                Log.i(TAG, "csd-1.position:" + csd1.position() + " remaining:" + csd1.remaining());
                                h264OutputChannel.write(csd0);
                                h264OutputChannel.write(csd1);
                                h264TrackIndex = trackIndex;
                            }
                            return trackIndex;
                        }

                        @Override
                        public void writeSampleData(int trackIndex, @NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
                            if (h264TrackIndex == trackIndex) {
                                Log.i(TAG, "frame " + (frameNum++) + " buffer.position:" + byteBuf.position() + " buffer.remaining:" + byteBuf.remaining() + " flags:" + bufferInfo.flags + " offset:" + bufferInfo.offset + " size:" + bufferInfo.size);
                                h264OutputChannel.write(byteBuf);
                            }
                            muxer.writeSampleData(trackIndex, byteBuf, bufferInfo);
                        }

                        @Override
                        public void release() {
                            muxer.release();
                        }
                    };
                }
            };
            mConverter.setInput(input);
            //mConverter.setOutput(output);
            mConverter.setOutput(new FileOutputStream(output));
            mConverter.setTimeRange(timeFrom, timeTo);
            mConverter.setVideoResolution(conversionParameters.mVideoResolution);
            mConverter.setVideoCodec(conversionParameters.mVideoCodec);
            mConverter.setVideoBitrate(conversionParameters.mVideoBitrate);
            mConverter.setAudioBitrate(conversionParameters.mAudioBitrate);

            mConverter.setListener(percent -> {
                publishProgress(percent);
                return isCancelled();
            });

            final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoConverter:convert");
        }

        @Override
        protected Boolean doInBackground(Void... params) {

            try {
                mConverter.convert();
            } catch (BadMediaException | IOException | MediaConversionException e) {
                Log.e(TAG, "failed to convert: " + e.toString(), e);
                return Boolean.FALSE;
            }

            Log.i(TAG, "Conversion finished, output file size is " + mOutputFile.length());
            return Boolean.TRUE;
        }

        protected void onProgressUpdate(Integer... values) {
            final Integer progress = values[0];
            mConversionProgressBar.setIndeterminate(progress >= 100);
            mConversionProgressBar.setProgress(progress);
            mTimeView.setText(getString(R.string.seconds_elapsed, (System.currentTimeMillis() - mStartTime) / 1000));
        }

        protected void onPreExecute() {
            mStartTime = System.currentTimeMillis();
            mConversionProgressBar.setVisibility(View.VISIBLE);
            mConversionProgressBar.setIndeterminate(true);
            mConversionProgressBar.setProgress(0);
            mRangeSeekBar.setVisibility(View.INVISIBLE);
            mWakeLock.acquire(10*60*1000L /*10 minutes*/);
        }

        protected void onCancelled() {
            mConversionTask = null;
            updateButtons();
            mTimeView.setText("");
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }

        protected void onPostExecute(final Boolean result) {
            mConversionTask = null;

            if (Boolean.TRUE.equals(result)) {
                mConverted = true;
                final Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(mOutputFile));
                getApplicationContext().sendBroadcast(intent);

                final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();

                mediaMetadataRetriever.setDataSource(mOutputFile.getAbsolutePath());
                final String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                final String width = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                final String height = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

                mediaMetadataRetriever.release();

                mOutputInfoView.setText(getString(R.string.video_info, width, height,
                        DateUtils.formatElapsedTime(duration == null ? 0 : (Long.parseLong( duration) / 1000)),
                        Formatter.formatShortFileSize(MainActivity.this, mOutputFile.length())));

            } else {
                Toast.makeText(getBaseContext(), R.string.conversion_failed, Toast.LENGTH_LONG).show();
            }
            updateButtons();
            mTimeView.setText(getString(R.string.seconds_elapsed, (System.currentTimeMillis() - mStartTime) / 1000));

            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
    }

    private class PreviewThread extends Thread {

        private final String mFilePath;
        private long mFrameTime = -1;
        private final Object mLock = new Object();
        private final AtomicBoolean mRunning = new AtomicBoolean(true);


        PreviewThread(final @NonNull String filePath) {
            mFilePath = filePath;
        }

        void requestShowFrame(final long frameTime) {
            synchronized (mLock) {
                Log.i(TAG, "video-thumbnails REQUEST FRAME AT " + frameTime);
                this.mFrameTime = frameTime;
                mLock.notifyAll();
            }
        }

        public void run() {
            final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            try {
                mediaMetadataRetriever.setDataSource(mFilePath);
            } catch (Exception ex) {
                return;
            }
            try {
                long lastFrameTime = -1;

                //noinspection InfiniteLoopStatement
                while (true) {

                    synchronized (mLock) {
                        if (mFrameTime == lastFrameTime) {
                            mLock.wait();
                        }
                        lastFrameTime = mFrameTime;
                    }

                    final Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime(lastFrameTime * 1000);
                    if (bitmap != null) {
                        Log.i(TAG, "video-thumbnails GOT FRAME AT " + mFrameTime);
                        MainActivity.this.runOnUiThread(() -> {
                            if (mRunning.get()) {
                                mThumbView.setImageBitmap(bitmap);
                            }
                        });
                    }
                }

            } catch (InterruptedException e) {
                //allow thread to exit
            }
            mRunning.set(false);
            mediaMetadataRetriever.release();
        }
    }

    private static class ConversionParameters {
        final int mVideoResolution;
        final @MediaConverter.VideoCodec String mVideoCodec;
        final int mVideoBitrate;
        final int mAudioBitrate;

        ConversionParameters(final int videoResolution, final @MediaConverter.VideoCodec String videoCodec, final int videoBitrate, final int audioBitrate) {
            mVideoResolution = videoResolution;
            mVideoCodec = videoCodec;
            mVideoBitrate = videoBitrate;
            mAudioBitrate = audioBitrate;
        }
    }
}
