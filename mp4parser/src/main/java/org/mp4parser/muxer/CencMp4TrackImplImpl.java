package org.mp4parser.muxer;

import org.mp4parser.Box;
import org.mp4parser.Container;
import org.mp4parser.boxes.iso14496.part12.*;
import org.mp4parser.boxes.iso23001.part7.CencSampleAuxiliaryDataFormat;
import org.mp4parser.boxes.iso23001.part7.TrackEncryptionBox;
import org.mp4parser.boxes.sampleentry.SampleEntry;
import org.mp4parser.muxer.tracks.encryption.CencEncryptedTrack;
import org.mp4parser.tools.IsoTypeReader;
import org.mp4parser.tools.Path;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.mp4parser.tools.CastUtils.l2i;

/**
 * This track implementation is to be used when MP4 track is CENC encrypted.
 */
public class CencMp4TrackImplImpl extends Mp4TrackImpl implements CencEncryptedTrack {

    private List<CencSampleAuxiliaryDataFormat> sampleEncryptionEntries;



    /**
     * Creates a track from a TrackBox and potentially fragments. Use <b>fragements parameter
     * only</b> to supply additional fragments that are not located in the main file.
     *
     * @param trackId      ID of the track to extract
     * @param isofile      the parsed MP4 file
     * @param randomAccess the RandomAccessSource to read the samples from
     * @param name         an arbitrary naem to identify track later - e.g. filename
     * @throws java.io.IOException if reading from underlying <code>DataSource</code> fails
     */
    public CencMp4TrackImplImpl(final long trackId, Container isofile, RandomAccessSource randomAccess, String name) throws IOException {
        super(trackId, isofile, randomAccess, name);

        TrackBox trackBox = null;
        for (TrackBox box : Path.<TrackBox>getPaths(isofile, "moov/trak")) {
            if (box.getTrackHeaderBox().getTrackId() == trackId) {
                trackBox = box;
                break;
            }
        }
        assert trackBox != null;
        List<SchemeTypeBox> schms = Path.getPaths((Container) trackBox, "mdia[0]/minf[0]/stbl[0]/stsd[0]/enc./sinf[0]/schm[0]");
        for (SchemeTypeBox schm : schms) {
            assert schm != null && (schm.getSchemeType().equals("cenc") || schm.getSchemeType().equals("cbc1")) : "Track must be CENC (cenc or cbc1) encrypted";
        }
        List<SampleEntry> sampleEntries = trackBox.getMediaBox().getMediaInformationBox().getSampleTableBox().getSampleDescriptionBox().getBoxes(SampleEntry.class);
        assert trackBox.getMediaBox().getMediaInformationBox().getSampleTableBox().getSampleDescriptionBox().getBoxes().size() == sampleEntries.size();

        sampleEncryptionEntries = new ArrayList<>();

        final List<MovieExtendsBox> movieExtendsBoxes = Path.getPaths(isofile, "moov/mvex");
        if (!movieExtendsBoxes.isEmpty()) {


            for (MovieFragmentBox movieFragmentBox : isofile.getBoxes(MovieFragmentBox.class)) {
                List<TrackFragmentBox> trafs = movieFragmentBox.getBoxes(TrackFragmentBox.class);
                for (TrackFragmentBox traf : trafs) {
                    if (traf.getTrackFragmentHeaderBox().getTrackId() == trackId) {
                        long baseOffset;
                        if (traf.getTrackFragmentHeaderBox().hasBaseDataOffset()) {
                            baseOffset = traf.getTrackFragmentHeaderBox().getBaseDataOffset();
                        } else {
                            Iterator<Box> it = isofile.getBoxes().iterator();
                            baseOffset = 0;
                            for (Box b = it.next(); b != movieFragmentBox; b = it.next()) {
                                baseOffset += b.getSize();
                            }
                        }
                        TrackEncryptionBox tenc = Path.getPath((Container) sampleEntries.get(l2i(traf.getTrackFragmentHeaderBox().getSampleDescriptionIndex()-1)), "sinf[0]/schi[0]/tenc[0]");


                        FindSaioSaizPair saizSaioPair = new FindSaioSaizPair(traf).invoke();
                        SampleAuxiliaryInformationOffsetsBox saio = saizSaioPair.getSaio();
                        SampleAuxiliaryInformationSizesBox saiz = saizSaioPair.getSaiz();
                        // now we have the correct saio/saiz combo!
                        assert saio != null;
                        long[] saioOffsets = saio.getOffsets();
                        assert saioOffsets.length == traf.getBoxes(TrackRunBox.class).size();
                        assert saiz != null;

                        List<TrackRunBox> truns = traf.getBoxes(TrackRunBox.class);
                        int sampleNo = 0;
                        for (int i = 0; i < saioOffsets.length; i++) {
                            int numSamples = truns.get(i).getEntries().size();
                            long offset = saioOffsets[i];
                            long length = 0;

                            for (int j = sampleNo; j < sampleNo + numSamples; j++) {
                                length += saiz.getSize(j);
                            }
                            ByteBuffer trunsCencSampleAuxData = randomAccess.get(baseOffset + offset, length);
                            for (int j = sampleNo; j < sampleNo + numSamples; j++) {
                                int auxInfoSize = saiz.getSize(j);
                                if (tenc!=null) {
                                    sampleEncryptionEntries.add(
                                            parseCencAuxDataFormat(tenc.getDefaultIvSize(), trunsCencSampleAuxData, auxInfoSize)
                                    );
                                } else {
                                    sampleEncryptionEntries.add(new CencSampleAuxiliaryDataFormat());
                                }

                            }

                            sampleNo += numSamples;
                        }
                    }
                }

            }
        } else {

            ChunkOffsetBox chunkOffsetBox = Path.getPath(trackBox, "mdia[0]/minf[0]/stbl[0]/stco[0]");

            if (chunkOffsetBox == null) {
                chunkOffsetBox = Path.getPath(trackBox, "mdia[0]/minf[0]/stbl[0]/co64[0]");
            }

            assert chunkOffsetBox != null;
            long[] chunkSizes = trackBox.getSampleTableBox().getSampleToChunkBox().blowup(chunkOffsetBox.getChunkOffsets().length);


            FindSaioSaizPair saizSaioPair = new FindSaioSaizPair((Container) Path.getPath(trackBox, "mdia[0]/minf[0]/stbl[0]")).invoke();
            SampleAuxiliaryInformationOffsetsBox saio = saizSaioPair.saio;
            SampleAuxiliaryInformationSizesBox saiz = saizSaioPair.saiz;
            SampleEntry se = null;
            TrackEncryptionBox tenc = null;
            List<Sample> samples = this.getSamples();

            if (saio.getOffsets().length == 1) {
                long offset = saio.getOffsets()[0];
                int sizeInTotal = 0;
                if (saiz.getDefaultSampleInfoSize() > 0) {
                    sizeInTotal += saiz.getSampleCount() * saiz.getDefaultSampleInfoSize();
                } else {
                    for (int i = 0; i < saiz.getSampleCount(); i++) {
                        sizeInTotal += saiz.getSampleInfoSizes()[i];
                    }
                }

                ByteBuffer chunksCencSampleAuxData = randomAccess.get(offset, sizeInTotal);

                for (int i = 0; i < saiz.getSampleCount(); i++) {
                    long auxInfoSize = saiz.getSize(i);
                    SampleEntry _se = samples.get(i).getSampleEntry();
                    if (se != _se) {
                        tenc = Path.getPath((Container) _se, "sinf[0]/schi[0]/tenc[0]");
                    }
                    se = _se;
                    if (tenc != null) {
                        sampleEncryptionEntries.add(
                                parseCencAuxDataFormat(tenc.getDefaultIvSize(), chunksCencSampleAuxData, auxInfoSize)
                        );
                    } else {
                        sampleEncryptionEntries.add(new CencSampleAuxiliaryDataFormat());
                    }
                }

            } else if (saio.getOffsets().length == chunkSizes.length) {
                int currentSampleNo = 0;


                for (int i = 0; i < chunkSizes.length; i++) {
                    long offset = saio.getOffsets()[i];
                    long size = 0;
                    if (saiz.getDefaultSampleInfoSize() > 0) {
                        size += saiz.getSampleCount() * chunkSizes[i];
                    } else {
                        for (int j = 0; j < chunkSizes[i]; j++) {
                            size += saiz.getSize(currentSampleNo + j);
                        }
                    }

                    ByteBuffer chunksCencSampleAuxData = randomAccess.get(offset, size);
                    for (int j = 0; j < chunkSizes[i]; j++) {
                        long auxInfoSize = saiz.getSize(currentSampleNo + j);
                        SampleEntry _se = samples.get(currentSampleNo + j).getSampleEntry();
                        if (se != _se) {
                            tenc = Path.getPath((Container) _se, "sinf[0]/schi[0]/tenc[0]");
                        }
                        se = _se;
                        if (tenc != null) {
                            sampleEncryptionEntries.add(
                                    parseCencAuxDataFormat(tenc.getDefaultIvSize(), chunksCencSampleAuxData, auxInfoSize)
                            );
                        } else {
                            sampleEncryptionEntries.add(new CencSampleAuxiliaryDataFormat());
                        }
                    }
                    currentSampleNo += chunkSizes[i];
                }
            } else {
                throw new RuntimeException("Number of saio offsets must be either 1 or number of chunks");
            }
        }
    }

    private CencSampleAuxiliaryDataFormat parseCencAuxDataFormat(int ivSize, ByteBuffer chunksCencSampleAuxData, long auxInfoSize) {
        CencSampleAuxiliaryDataFormat cadf = new CencSampleAuxiliaryDataFormat();
        if (auxInfoSize > 0) {
            cadf.iv = new byte[ivSize];
            chunksCencSampleAuxData.get(cadf.iv);
            if (auxInfoSize > ivSize) {
                int numOfPairs = IsoTypeReader.readUInt16(chunksCencSampleAuxData);
                cadf.pairs = new CencSampleAuxiliaryDataFormat.Pair[numOfPairs];
                for (int i = 0; i < cadf.pairs.length; i++) {
                    cadf.pairs[i] = cadf.createPair(
                            IsoTypeReader.readUInt16(chunksCencSampleAuxData),
                            IsoTypeReader.readUInt32(chunksCencSampleAuxData));
                }
            }
        }
        return cadf;
    }


    public boolean hasSubSampleEncryption() {
        return false;
    }

    public List<CencSampleAuxiliaryDataFormat> getSampleEncryptionEntries() {
        return sampleEncryptionEntries;
    }

    @Override
    public String toString() {
        return "CencMp4TrackImpl{" +
                "handler='" + getHandler() + '\'' +
                '}';
    }

    @Override
    public String getName() {
        return "enc(" + super.getName() + ")";
    }

    private class FindSaioSaizPair {
        private Container container;
        private SampleAuxiliaryInformationSizesBox saiz;
        private SampleAuxiliaryInformationOffsetsBox saio;

        public FindSaioSaizPair(Container container) {
            this.container = container;
        }

        public SampleAuxiliaryInformationSizesBox getSaiz() {
            return saiz;
        }

        public SampleAuxiliaryInformationOffsetsBox getSaio() {
            return saio;
        }

        public FindSaioSaizPair invoke() {
            List<SampleAuxiliaryInformationSizesBox> saizs = container.getBoxes(SampleAuxiliaryInformationSizesBox.class);
            List<SampleAuxiliaryInformationOffsetsBox> saios = container.getBoxes(SampleAuxiliaryInformationOffsetsBox.class);
            assert saizs.size() == saios.size();
            saiz = null;
            saio = null;

            for (int i = 0; i < saizs.size(); i++) {
                if (saiz == null && (saizs.get(i).getAuxInfoType() == null) || "cenc".equals(saizs.get(i).getAuxInfoType())) {
                    saiz = saizs.get(i);
                } else if (saiz != null && saiz.getAuxInfoType() == null && "cenc".equals(saizs.get(i).getAuxInfoType())) {
                    saiz = saizs.get(i);
                } else {
                    throw new RuntimeException("Are there two cenc labeled saiz?");
                }
                if (saio == null && (saios.get(i).getAuxInfoType() == null) || "cenc".equals(saios.get(i).getAuxInfoType())) {
                    saio = saios.get(i);
                } else if (saio != null && saio.getAuxInfoType() == null && "cenc".equals(saios.get(i).getAuxInfoType())) {
                    saio = saios.get(i);
                } else {
                    throw new RuntimeException("Are there two cenc labeled saio?");
                }
            }
            return this;
        }
    }
}
