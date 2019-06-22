package com.dstukalov.videoconverter.muxer;

import androidx.annotation.Nullable;

import org.mp4parser.boxes.iso14496.part12.SampleDescriptionBox;
import org.mp4parser.boxes.iso14496.part15.HevcConfigurationBox;
import org.mp4parser.boxes.iso14496.part15.HevcDecoderConfigurationRecord;
import org.mp4parser.boxes.sampleentry.VisualSampleEntry;
import org.mp4parser.muxer.MemoryDataSourceImpl;
import org.mp4parser.muxer.tracks.AbstractH26XTrack;
import org.mp4parser.muxer.tracks.CleanInputStream;
import org.mp4parser.muxer.tracks.h265.H265NalUnitHeader;
import org.mp4parser.muxer.tracks.h265.H265NalUnitTypes;
import org.mp4parser.muxer.tracks.h265.SequenceParameterSetRbsp;
import org.mp4parser.streaming.StreamingSample;
import org.mp4parser.streaming.extensions.DimensionTrackExtension;
import org.mp4parser.streaming.extensions.SampleFlagsSampleExtension;
import org.mp4parser.streaming.input.AbstractStreamingTrack;
import org.mp4parser.streaming.input.StreamingSampleImpl;
import org.mp4parser.tools.ByteBufferByteChannel;
import org.mp4parser.tools.IsoTypeReader;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HevcTrack extends AbstractStreamingTrack implements H265NalUnitTypes {

    private ArrayList<ByteBuffer> nals = new ArrayList<>();
    private boolean vclNalUnitSeenInAU;
    private boolean isIdr = true;
    private long currentPresentationTimeUs;
    private SampleDescriptionBox stsd;

    @Override
    public long getTimescale() {
        return 90000;
    }

    @Override
    public String getHandler() {
        return "vide";
    }

    @Override
    public String getLanguage() {
        return "eng";
    }

    @Override
    public SampleDescriptionBox getSampleDescriptionBox() {
        return stsd;
    }

    @Override
    public void close() {
    }

    public void configure(ByteBuffer csd) throws IOException {
        ArrayList<ByteBuffer> sps = new ArrayList<>();
        ArrayList<ByteBuffer> pps = new ArrayList<>();
        ArrayList<ByteBuffer> vps = new ArrayList<>();
        SequenceParameterSetRbsp spsStruct = null;

        MemoryDataSourceImpl dataSource = new MemoryDataSourceImpl(csd);
        AbstractH26XTrack.LookAhead lookAhead = new AbstractH26XTrack.LookAhead(dataSource);
        ByteBuffer nal;
        while ((nal = findNextNal(lookAhead)) != null) {
            H265NalUnitHeader unitHeader = getNalUnitHeader(nal);
            nal.position(0);
            // collect sps/vps/pps
            switch (unitHeader.nalUnitType) {
                case NAL_TYPE_PPS_NUT:
                    pps.add(nal.duplicate());
                    break;
                case NAL_TYPE_VPS_NUT:
                    vps.add(nal.duplicate());
                    break;
                case NAL_TYPE_SPS_NUT:
                    sps.add(nal.duplicate());
                    nal.position(2);
                    spsStruct = new SequenceParameterSetRbsp(new CleanInputStream(Channels.newInputStream(new ByteBufferByteChannel(nal.slice()))));
                    break;
                case NAL_TYPE_PREFIX_SEI_NUT:
                    //new SEIMessage(new BitReaderBuffer(nal.slice()));
                    break;
            }
        }

        stsd = new SampleDescriptionBox();
        stsd.addBox(createSampleEntry(sps, pps, vps, spsStruct));

    }

    protected ByteBuffer findNextNal(AbstractH26XTrack.LookAhead la) throws IOException {
        try {
            while (!la.nextThreeEquals001()) {
                la.discardByte();
            }
            la.discardNext3AndMarkStart();

            while (!la.nextThreeEquals000or001orEof(true)) {
                la.discardByte();
            }
            return la.getNal();
        } catch (EOFException e) {
            return null;
        }
    }


    protected void consumeLastNal() throws IOException {
        wrapUp(nals, currentPresentationTimeUs);
    }

    public void consumeNal(ByteBuffer nal, long presentationTimeUs) throws IOException {

        H265NalUnitHeader unitHeader = getNalUnitHeader(nal);
        //
        if (vclNalUnitSeenInAU) { // we need at least 1 VCL per AU
            // This branch checks if we encountered the start of a samples/AU
            if (isVcl(unitHeader)) {
                if ((nal.get(2) & -128) != 0) { // this is: first_slice_segment_in_pic_flag  u(1)
                    wrapUp(nals, presentationTimeUs);
                }
            } else {
                switch (unitHeader.nalUnitType) {
                    case NAL_TYPE_PREFIX_SEI_NUT:
                    case NAL_TYPE_AUD_NUT:
                    case NAL_TYPE_PPS_NUT:
                    case NAL_TYPE_VPS_NUT:
                    case NAL_TYPE_SPS_NUT:
                    case NAL_TYPE_RSV_NVCL41:
                    case NAL_TYPE_RSV_NVCL42:
                    case NAL_TYPE_RSV_NVCL43:
                    case NAL_TYPE_RSV_NVCL44:
                    case NAL_TYPE_UNSPEC48:
                    case NAL_TYPE_UNSPEC49:
                    case NAL_TYPE_UNSPEC50:
                    case NAL_TYPE_UNSPEC51:
                    case NAL_TYPE_UNSPEC52:
                    case NAL_TYPE_UNSPEC53:
                    case NAL_TYPE_UNSPEC54:
                    case NAL_TYPE_UNSPEC55:

                    case NAL_TYPE_EOB_NUT: // a bit special but also causes a sample to be formed
                    case NAL_TYPE_EOS_NUT:
                        wrapUp(nals, presentationTimeUs);
                        break;
                }
            }
        }


        switch (unitHeader.nalUnitType) {
            case NAL_TYPE_SPS_NUT:
            case NAL_TYPE_VPS_NUT:
            case NAL_TYPE_PPS_NUT:
            case NAL_TYPE_EOB_NUT:
            case NAL_TYPE_EOS_NUT:
            case NAL_TYPE_AUD_NUT:
            case NAL_TYPE_FD_NUT:
                // ignore these
                break;
            default:
                nals.add(nal);
        }

        if (isVcl(unitHeader)) {
            isIdr = unitHeader.nalUnitType == NAL_TYPE_IDR_W_RADL || unitHeader.nalUnitType == NAL_TYPE_IDR_N_LP;
        }

        vclNalUnitSeenInAU |= isVcl(unitHeader);
    }

    public void wrapUp(List<ByteBuffer> nals, long presentationTimeUs) throws IOException {

        long duration = presentationTimeUs - currentPresentationTimeUs;
        currentPresentationTimeUs = presentationTimeUs;

        StreamingSample sample = new StreamingSampleImpl(
                nals, getTimescale() * Math.max(0, duration) / 1000000L);

        SampleFlagsSampleExtension sampleFlagsSampleExtension = new SampleFlagsSampleExtension();
        sampleFlagsSampleExtension.setSampleIsNonSyncSample(!isIdr);

        sample.addSampleExtension(sampleFlagsSampleExtension);

        sampleSink.acceptSample(sample, this);

        vclNalUnitSeenInAU = false;
        isIdr = true;
        nals.clear();
    }

    public static H265NalUnitHeader getNalUnitHeader(ByteBuffer nal) {
        nal.position(0);
        int nal_unit_header = IsoTypeReader.readUInt16(nal);

        H265NalUnitHeader nalUnitHeader = new H265NalUnitHeader();
        nalUnitHeader.forbiddenZeroFlag = (nal_unit_header & 0x8000) >> 15;
        nalUnitHeader.nalUnitType = (nal_unit_header & 0x7E00) >> 9;
        nalUnitHeader.nuhLayerId = (nal_unit_header & 0x1F8) >> 3;
        nalUnitHeader.nuhTemporalIdPlusOne = (nal_unit_header & 0x7);
        return nalUnitHeader;
    }

    private VisualSampleEntry createSampleEntry(ArrayList<ByteBuffer> sps, ArrayList<ByteBuffer> pps, ArrayList<ByteBuffer> vps, @Nullable SequenceParameterSetRbsp spsStruct) {


        VisualSampleEntry visualSampleEntry = new VisualSampleEntry("hvc1");
        visualSampleEntry.setDataReferenceIndex(1);
        visualSampleEntry.setDepth(24);
        visualSampleEntry.setFrameCount(1);
        visualSampleEntry.setHorizresolution(72);
        visualSampleEntry.setVertresolution(72);
        visualSampleEntry.setCompressorname("HEVC Coding");

        HevcConfigurationBox hevcConfigurationBox = new HevcConfigurationBox();
        hevcConfigurationBox.getHevcDecoderConfigurationRecord().setConfigurationVersion(1);

        if (spsStruct != null) {
            visualSampleEntry.setWidth(spsStruct.pic_width_in_luma_samples);
            visualSampleEntry.setHeight(spsStruct.pic_height_in_luma_samples);
            DimensionTrackExtension dte = this.getTrackExtension(DimensionTrackExtension.class);
            if (dte == null) {
                this.addTrackExtension(new DimensionTrackExtension(spsStruct.pic_width_in_luma_samples, spsStruct.pic_height_in_luma_samples));
            }
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setChromaFormat(spsStruct.chroma_format_idc);
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setGeneral_profile_idc(spsStruct.general_profile_idc);
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setGeneral_profile_compatibility_flags(spsStruct.general_profile_compatibility_flags);
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setGeneral_constraint_indicator_flags(spsStruct.general_constraint_indicator_flags);
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setGeneral_level_idc(spsStruct.general_level_idc);
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setGeneral_tier_flag(spsStruct.general_tier_flag);
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setGeneral_profile_space(spsStruct.general_profile_space);
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setBitDepthChromaMinus8(spsStruct.bit_depth_chroma_minus8);
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setBitDepthLumaMinus8(spsStruct.bit_depth_luma_minus8);
            hevcConfigurationBox.getHevcDecoderConfigurationRecord().setTemporalIdNested(spsStruct.sps_temporal_id_nesting_flag);
        }

        hevcConfigurationBox.getHevcDecoderConfigurationRecord().setLengthSizeMinusOne(3);

        HevcDecoderConfigurationRecord.Array vpsArray = new HevcDecoderConfigurationRecord.Array();
        vpsArray.array_completeness = false;
        vpsArray.nal_unit_type = NAL_TYPE_VPS_NUT;
        vpsArray.nalUnits = new ArrayList<>();
        for (ByteBuffer vp : vps) {
            vpsArray.nalUnits.add(toArray(vp));
        }

        HevcDecoderConfigurationRecord.Array spsArray = new HevcDecoderConfigurationRecord.Array();
        spsArray.array_completeness = false;
        spsArray.nal_unit_type = NAL_TYPE_SPS_NUT;
        spsArray.nalUnits = new ArrayList<>();
        for (ByteBuffer sp : sps) {
            spsArray.nalUnits.add(toArray(sp));
        }

        HevcDecoderConfigurationRecord.Array ppsArray = new HevcDecoderConfigurationRecord.Array();
        ppsArray.array_completeness = false;
        ppsArray.nal_unit_type = NAL_TYPE_PPS_NUT;
        ppsArray.nalUnits = new ArrayList<>();
        for (ByteBuffer pp : pps) {
            ppsArray.nalUnits.add(toArray(pp));
        }

        hevcConfigurationBox.getArrays().addAll(Arrays.asList(spsArray, vpsArray, ppsArray));

        visualSampleEntry.addBox(hevcConfigurationBox);
        return visualSampleEntry;
    }

    private boolean isVcl(H265NalUnitHeader nalUnitHeader) {
        return nalUnitHeader.nalUnitType >= 0 && nalUnitHeader.nalUnitType <= 31;
    }

    private static byte[] toArray(ByteBuffer buf) {
        buf = buf.duplicate();
        byte[] b = new byte[buf.remaining()];
        buf.get(b, 0, b.length);
        return b;
    }
}
