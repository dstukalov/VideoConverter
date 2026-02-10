package com.dstukalov.videoconverterdemo;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaCodecInfo;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.dstukalov.videoconverter.MediaConverter;

import java.util.Objects;

public class ConversionParameters implements Parcelable {
    int mVideoResolution;
    @MediaConverter.VideoCodec String mVideoCodec;
    int mVideoBitrate;
    int mVideoBitrateMode;
    int mAudioBitrate;

    public static final Parcelable.Creator<ConversionParameters> CREATOR = new Parcelable.Creator<>() {
        public ConversionParameters createFromParcel(Parcel in) {
            return new ConversionParameters(in);
        }

        public ConversionParameters[] newArray(int size) {
            return new ConversionParameters[size];
        }
    };

    ConversionParameters(int videoResolution, @NonNull @MediaConverter.VideoCodec String videoCodec, int videoBitrate, int videoBitrateMode, int audioBitrate) {
        mVideoResolution = videoResolution;
        mVideoCodec = videoCodec;
        mVideoBitrate = videoBitrate;
        mVideoBitrateMode = videoBitrateMode;
        mAudioBitrate = audioBitrate;
    }

    ConversionParameters(Parcel in) {
        mVideoResolution = in.readInt();
        mVideoCodec = in.readString();
        mVideoBitrate = in.readInt();
        mVideoBitrateMode = in.readInt();
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
        dest.writeInt(mVideoBitrateMode);
        dest.writeInt(mAudioBitrate);
    }

    @Override
    public @NonNull String toString() {
        return mVideoResolution + "p " + mVideoCodec + " video:" + mVideoBitrate + "kbps mode:" + mVideoBitrateMode + " audio:" + mAudioBitrate + "kbps";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversionParameters that = (ConversionParameters) o;
        return mVideoResolution == that.mVideoResolution &&
                mVideoBitrate == that.mVideoBitrate &&
                mVideoBitrateMode == that.mVideoBitrateMode &&
                mAudioBitrate == that.mAudioBitrate &&
                mVideoCodec.equals(that.mVideoCodec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mVideoResolution, mVideoCodec, mVideoBitrate, mVideoBitrateMode, mAudioBitrate);
    }

    @NonNull
    public ConversionParameters clone() {
        return new ConversionParameters(mVideoResolution, mVideoCodec, mVideoBitrate, mVideoBitrateMode, mAudioBitrate);
    }

    public void load(@NonNull Context context) {
        final SharedPreferences prefs = context.getSharedPreferences("conversion_parameters", Context.MODE_PRIVATE);
        mVideoCodec = prefs.getString("video_codec", MediaConverter.VIDEO_CODEC_H264);
        mVideoResolution = prefs.getInt("video_resolution", 720);
        mVideoBitrate = prefs.getInt("video_bitrate", 4000000);
        mVideoBitrateMode = prefs.getInt("video_bitrate_mode", MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        mAudioBitrate = prefs.getInt("audio_bitrate", 192000);
    }

    public void save(@NonNull Context context) {
        context.getSharedPreferences("conversion_parameters", Context.MODE_PRIVATE).edit()
                .putString("video_codec", mVideoCodec)
                .putInt("video_resolution", mVideoResolution)
                .putInt("video_bitrate", mVideoBitrate)
                .putInt("video_bitrate_mode", mVideoBitrateMode)
                .putInt("audio_bitrate", mAudioBitrate).apply();
    }
}
