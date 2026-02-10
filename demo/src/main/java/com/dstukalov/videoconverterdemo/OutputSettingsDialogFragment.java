package com.dstukalov.videoconverterdemo;

import android.app.Dialog;
import android.media.MediaCodecInfo;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;

import com.dstukalov.videoconverter.MediaConverter;
import com.dstukalov.videoconverter.Preconditions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import java.util.Locale;

public class OutputSettingsDialogFragment extends BottomSheetDialogFragment {

    private static final String TAG = "video-converter";

    private static final String ARG_CONVERSION_PARAMETERS = "conversion_parameters";
    private static final String ARG_DURATION = "duration";

    private ConversionParameters mConversionParameters;
    private long mDuration;
    private boolean mH265Supported;

    private MaterialButtonToggleGroup mVideoResolutionGroup;
    private MaterialButtonToggleGroup mVideoCodecGroup;
    private RadioGroup mBitrateModeGroup;
    private Slider mVideoBitrateSlider;
    private Slider mAudioBitrateSlider;
    private TextView mOutputInfo;

    interface OutputSettingsCallback {
        void onOutputSettingsChange(@NonNull ConversionParameters conversionParameters);
    }

    public static OutputSettingsDialogFragment newInstance(@NonNull ConversionParameters conversionParameters, long duration) {
        final OutputSettingsDialogFragment frag = new OutputSettingsDialogFragment();
        final Bundle args = new Bundle();
        args.putParcelable(ARG_CONVERSION_PARAMETERS, conversionParameters.clone());
        args.putLong(ARG_DURATION, duration);
        frag.setArguments(args);
        return frag;
    }

    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final BottomSheetDialog bottomSheetDialog = (BottomSheetDialog)super.onCreateDialog(savedInstanceState);
        bottomSheetDialog.setOnShowListener(dialog -> {
            final View bottomSheet =  Preconditions.checkNotNull(bottomSheetDialog.findViewById(R.id.design_bottom_sheet));
            BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        return bottomSheetDialog;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.output_settings, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mConversionParameters = Preconditions.checkNotNull(requireArguments().getParcelable(ARG_CONVERSION_PARAMETERS));
        mDuration = requireArguments().getLong(ARG_DURATION);
        mH265Supported = MediaConverter.selectCodec(MediaConverter.VIDEO_CODEC_H265) != null;

        view.findViewById(R.id.presets).setOnClickListener(this::onPresets);

        mOutputInfo = view.findViewById(R.id.output_info);

        mVideoResolutionGroup = view.findViewById(R.id.video_resolution_button_group);
        mVideoResolutionGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                mConversionParameters.mVideoResolution = idToResolution(checkedId);
            }
        });

        mVideoCodecGroup = view.findViewById(R.id.video_codec_button_group);
        mVideoCodecGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                mConversionParameters.mVideoCodec = idToCodec(checkedId);
            }
        });
        if (!mH265Supported) {
            mConversionParameters.mVideoCodec = MediaConverter.VIDEO_CODEC_H264;
            mVideoCodecGroup.setVisibility(View.GONE);
        }

        final LabelFormatter bitrateFormatter = new BitrateLabelFormatter();

        final TextView videoBitrateTextView = view.findViewById(R.id.video_bitrate_label);
        mVideoBitrateSlider = view.findViewById(R.id.video_bitrate_slider);
        mVideoBitrateSlider.setLabelFormatter(bitrateFormatter);
        mVideoBitrateSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                mConversionParameters.mVideoBitrate = (int) value;
            }
            videoBitrateTextView.setText(getString(R.string.video_bitrate, bitrateFormatter.getFormattedValue(value)));
            estimateOutput();
        });
        videoBitrateTextView.setText(getString(R.string.audio_bitrate, bitrateFormatter.getFormattedValue(mConversionParameters.mVideoBitrate)));

        final TextView audioBitrateTextView = view.findViewById(R.id.audio_bitrate_label);
        mAudioBitrateSlider = view.findViewById(R.id.audio_bitrate_slider);
        mAudioBitrateSlider.setLabelFormatter(bitrateFormatter);
        mAudioBitrateSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                mConversionParameters.mAudioBitrate = (int) value;
            }
            audioBitrateTextView.setText(getString(R.string.audio_bitrate, bitrateFormatter.getFormattedValue(value)));
            estimateOutput();
        });
        audioBitrateTextView.setText(getString(R.string.audio_bitrate, bitrateFormatter.getFormattedValue(mConversionParameters.mAudioBitrate)));

        mBitrateModeGroup = view.findViewById(R.id.bitrate_mode);
        mBitrateModeGroup.setOnCheckedChangeListener((radioGroup, id) -> mConversionParameters.mVideoBitrateMode = idToBitrateMode(id));

        view.findViewById(R.id.ok).setOnClickListener(v -> {
            Preconditions.checkNotNull((OutputSettingsCallback)getActivity()).onOutputSettingsChange(mConversionParameters);
            dismissAllowingStateLoss();
        });
        view.findViewById(R.id.cancel).setOnClickListener(v -> dismissAllowingStateLoss());

        showSettings();
        estimateOutput();
    }

    private void showSettings() {
        mVideoResolutionGroup.check(resolutionToId(mConversionParameters.mVideoResolution));
        mVideoCodecGroup.check(codecToId(Preconditions.checkNotNull(mConversionParameters.mVideoCodec)));
        mBitrateModeGroup.check(bitrateModeToId(mConversionParameters.mVideoBitrateMode));
        mVideoBitrateSlider.setValue(mConversionParameters.mVideoBitrate);
        mAudioBitrateSlider.setValue(mConversionParameters.mAudioBitrate);
    }

    private void estimateOutput() {
        final long estimatedSize = (mConversionParameters.mVideoBitrate + mConversionParameters.mAudioBitrate) * mDuration / 8000;
        mOutputInfo.setText(getString(R.string.video_size_output, Formatter.formatShortFileSize(requireContext(), estimatedSize)));
    }

    private void onPresets(View view) {
        final PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(R.menu.output_presets, popup.getMenu());
        if (!mH265Supported) {
            popup.getMenu().removeItem(R.id.preset_720p_h265);
            popup.getMenu().removeItem(R.id.preset_1080p_h265);
        }
        popup.setOnMenuItemClickListener(item -> {
            final int itemId = item.getItemId();
            if (itemId == R.id.preset_240p) {
                mConversionParameters = new ConversionParameters(240, MediaConverter.VIDEO_CODEC_H264, 1333000, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, 64000);
            } else if (itemId == R.id.preset_360p) {
                mConversionParameters = new ConversionParameters(360, MediaConverter.VIDEO_CODEC_H264, 2000000, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, 96000);
            } else if (itemId == R.id.preset_480p) {
                mConversionParameters = new ConversionParameters(480, MediaConverter.VIDEO_CODEC_H264, 2666000, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, 128000);
            } else if (itemId == R.id.preset_720p) {
                mConversionParameters = new ConversionParameters(720, MediaConverter.VIDEO_CODEC_H264,  4000000, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, 192000);
            } else if (itemId == R.id.preset_720p_h265) {
                mConversionParameters = new ConversionParameters(720, MediaConverter.VIDEO_CODEC_H265,  2000000, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, 192000);
            } else if (itemId == R.id.preset_1080p) {
                mConversionParameters = new ConversionParameters(1080, MediaConverter.VIDEO_CODEC_H264, 6000000, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, 192000);
            } else if (itemId == R.id.preset_1080p_h265) {
                mConversionParameters = new ConversionParameters(1080, MediaConverter.VIDEO_CODEC_H265,  3000000, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, 192000);
            }
            showSettings();
            Log.i(TAG, "onOutputOptions selected " + mConversionParameters);
            return true;
        });

        popup.show();
    }

    private @IdRes int resolutionToId(int resolution) {
        switch (resolution) {
            case 240: return R.id.resolution_240p;
            case 360: return R.id.resolution_360p;
            case 480: return R.id.resolution_480p;
            case 720: return R.id.resolution_720p;
            case 1080: return R.id.resolution_1080p;
        }
        return R.id.resolution_720p;
    }

    private int idToResolution(@IdRes int id) {
        switch (id) {
            case R.id.resolution_240p: return 240;
            case R.id.resolution_360p: return 360;
            case R.id.resolution_480p: return 480;
            case R.id.resolution_720p: return 720;
            case R.id.resolution_1080p: return 1080;
        }
        return 720;
    }

    private @IdRes int codecToId(String codec) {
        switch (codec) {
            case MediaConverter.VIDEO_CODEC_H264: return R.id.h264;
            case MediaConverter.VIDEO_CODEC_H265: return R.id.h265;
        }
        return R.id.h264;
    }

    private String idToCodec(@IdRes int id) {
        switch (id) {
            case R.id.h264: return MediaConverter.VIDEO_CODEC_H264;
            case R.id.h265: return MediaConverter.VIDEO_CODEC_H265;
        }
        return MediaConverter.VIDEO_CODEC_H264;
    }

    private @IdRes int bitrateModeToId(int bitrateMode) {
        switch (bitrateMode) {
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR: return R.id.bitrate_mode_vbr;
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR: return R.id.bitrate_mode_cbr;
        }
        return R.id.bitrate_mode_vbr;
    }

    private int idToBitrateMode(@IdRes int id) {
        switch (id) {
            case R.id.bitrate_mode_vbr: return MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
            case R.id.bitrate_mode_cbr: return MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
        }
        return MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
    }

    private static final class BitrateLabelFormatter implements LabelFormatter {

        private static final int MILLION = 1000000;
        private static final int THOUSAND = 1000;

        @NonNull
        @Override
        public String getFormattedValue(float value) {
            if (value >= MILLION) {
                return String.format(Locale.US, "%.1f Mbps", value / MILLION);
            } else if (value >= THOUSAND) {
                return String.format(Locale.US, "%.0f Kbps", value / THOUSAND);
            }

            return String.format(Locale.US, "%.0f bps", value);
        }
    }
}
