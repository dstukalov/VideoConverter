package com.dstukalov.videoconverter.muxer;

import androidx.annotation.NonNull;

import org.mp4parser.boxes.iso14496.part1.objectdescriptors.AudioSpecificConfig;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.DecoderConfigDescriptor;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.ESDescriptor;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.SLConfigDescriptor;
import org.mp4parser.boxes.iso14496.part12.SampleDescriptionBox;
import org.mp4parser.boxes.iso14496.part14.ESDescriptorBox;
import org.mp4parser.boxes.sampleentry.AudioSampleEntry;
import org.mp4parser.streaming.extensions.DefaultSampleFlagsTrackExtension;
import org.mp4parser.streaming.extensions.TrackIdTrackExtension;
import org.mp4parser.streaming.input.AbstractStreamingTrack;
import org.mp4parser.streaming.input.StreamingSampleImpl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class AacTrack extends AbstractStreamingTrack {

    private static Map<Integer, Integer> samplingFrequencyIndexMap = new HashMap<>();
    static {
        samplingFrequencyIndexMap.put(96000, 0);
        samplingFrequencyIndexMap.put(88200, 1);
        samplingFrequencyIndexMap.put(64000, 2);
        samplingFrequencyIndexMap.put(48000, 3);
        samplingFrequencyIndexMap.put(44100, 4);
        samplingFrequencyIndexMap.put(32000, 5);
        samplingFrequencyIndexMap.put(24000, 6);
        samplingFrequencyIndexMap.put(22050, 7);
        samplingFrequencyIndexMap.put(16000, 8);
        samplingFrequencyIndexMap.put(12000, 9);
        samplingFrequencyIndexMap.put(11025, 10);
        samplingFrequencyIndexMap.put(8000, 11);
    }

    private SampleDescriptionBox stsd;
    private String lang = "und";
    private long avgBitrate;
    private long maxBitrate;

    private int sampleRate;
    private int channelCount;
    private int aacProfile;

    public AacTrack(long avgBitrate, long maxBitrate, int sampleRate, int channelCount, int aacProfile) {
        this.avgBitrate = avgBitrate;
        this.maxBitrate = maxBitrate;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.aacProfile = aacProfile;
        DefaultSampleFlagsTrackExtension defaultSampleFlagsTrackExtension = new DefaultSampleFlagsTrackExtension();
        defaultSampleFlagsTrackExtension.setIsLeading(2);
        defaultSampleFlagsTrackExtension.setSampleDependsOn(2);
        defaultSampleFlagsTrackExtension.setSampleIsDependedOn(2);
        defaultSampleFlagsTrackExtension.setSampleHasRedundancy(2);
        defaultSampleFlagsTrackExtension.setSampleIsNonSyncSample(false);
        this.addTrackExtension(defaultSampleFlagsTrackExtension);
    }

    public synchronized SampleDescriptionBox getSampleDescriptionBox() {
        if (stsd == null) {
            stsd = new SampleDescriptionBox();
            AudioSampleEntry audioSampleEntry = new AudioSampleEntry("mp4a");
            if (channelCount == 7) {
                audioSampleEntry.setChannelCount(8);
            } else {
                audioSampleEntry.setChannelCount(channelCount);
            }
            audioSampleEntry.setSampleRate(sampleRate);
            audioSampleEntry.setDataReferenceIndex(1);
            audioSampleEntry.setSampleSize(16);


            ESDescriptorBox esds = new ESDescriptorBox();
            ESDescriptor descriptor = new ESDescriptor();
            descriptor.setEsId(0);

            SLConfigDescriptor slConfigDescriptor = new SLConfigDescriptor();
            slConfigDescriptor.setPredefined(2);
            descriptor.setSlConfigDescriptor(slConfigDescriptor);

            DecoderConfigDescriptor decoderConfigDescriptor = new DecoderConfigDescriptor();
            decoderConfigDescriptor.setObjectTypeIndication(0x40 /*Audio ISO/IEC 14496-3*/);
            decoderConfigDescriptor.setStreamType(5 /*audio stream*/);
            decoderConfigDescriptor.setBufferSizeDB(1536);
            decoderConfigDescriptor.setMaxBitRate(maxBitrate);
            decoderConfigDescriptor.setAvgBitRate(avgBitrate);

            AudioSpecificConfig audioSpecificConfig = new AudioSpecificConfig();
            audioSpecificConfig.setOriginalAudioObjectType(aacProfile);
            Integer sampleRateIndex = samplingFrequencyIndexMap.get(sampleRate);
            audioSpecificConfig.setSamplingFrequencyIndex(sampleRateIndex == null ? 0 : sampleRateIndex);
            audioSpecificConfig.setChannelConfiguration(channelCount);
            decoderConfigDescriptor.setAudioSpecificInfo(audioSpecificConfig);

            descriptor.setDecoderConfigDescriptor(decoderConfigDescriptor);

            esds.setEsDescriptor(descriptor);

            audioSampleEntry.addBox(esds);
            stsd.addBox(audioSampleEntry);

        }
        return stsd;
    }

    public long getTimescale() {
        return sampleRate;
    }

    public String getHandler() {
        return "soun";
    }

    public String getLanguage() {
        return lang;
    }

    public void setLanguage(String lang) {
        this.lang = lang;
    }

    public void close() {
    }

    public void processSample(byte[] frame) throws IOException {
        sampleSink.acceptSample(new StreamingSampleImpl(ByteBuffer.wrap(frame), 1024), this);
    }

    @Override
    public @NonNull String toString() {
        TrackIdTrackExtension trackIdTrackExtension = this.getTrackExtension(TrackIdTrackExtension.class);
        if (trackIdTrackExtension != null) {
            return "AacTrack{trackId=" + trackIdTrackExtension.getTrackId() + "}";
        } else {
            return "AacTrack{}";
        }
    }
}
