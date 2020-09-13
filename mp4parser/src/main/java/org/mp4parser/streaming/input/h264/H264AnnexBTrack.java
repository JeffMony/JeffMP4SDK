package org.mp4parser.streaming.input.h264;

import org.mp4parser.streaming.extensions.TrackIdTrackExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Reads H264 data from an Annex B InputStream.
 */
public class H264AnnexBTrack extends H264NalConsumingTrack implements Callable<Void> {

    private InputStream inputStream;

    public H264AnnexBTrack(InputStream inputStream) throws IOException {
        assert inputStream != null;
        this.inputStream = new BufferedInputStream(inputStream);
    }


    public Void call() throws IOException, InterruptedException {
        byte[] nal;
        NalStreamTokenizer st = new NalStreamTokenizer(inputStream);

        while ((nal = st.getNext()) != null) {
            //System.err.println("NAL before consume");
            consumeNal(ByteBuffer.wrap(nal));
            //System.err.println("NAL after consume");
        }
        pushSample(createSample(buffered, fvnd.sliceHeader, sliceNalUnitHeader), true, true);
        return null;
    }

    @Override
    public String toString() {
        TrackIdTrackExtension trackIdTrackExtension = this.getTrackExtension(TrackIdTrackExtension.class);
        if (trackIdTrackExtension != null) {
            return "H264AnnexBTrack{trackId=" + trackIdTrackExtension.getTrackId() + "}";
        } else {
            return "H264AnnexBTrack{}";
        }
    }

    public static class NalStreamTokenizer {
        private static Logger LOG = LoggerFactory.getLogger(NalStreamTokenizer.class.getName());
        MyByteArrayOutputStream next = new MyByteArrayOutputStream();
        int pattern = 0;
        private InputStream inputStream;

        public NalStreamTokenizer(InputStream inputStream) {
            this.inputStream = inputStream;

        }

        public byte[] getNext() throws IOException {
            //System.err.println("getNext() called");
            if (LOG.isDebugEnabled()) {
                LOG.debug("getNext() called");
            }
            int c;


            while ((c = inputStream.read()) != -1) {
                if (!(pattern == 2 && c == 3)) {
                    next.write(c);


                    if (pattern == 0 && c == 0) {
                        pattern = 1;
                    } else if (pattern == 1 && c == 0) {
                        pattern = 2;
                    } else if (pattern == 2 && c == 0) {
                        byte[] s = next.toByteArrayLess3();
                        next.reset();
                        if (s != null) {
                            return s;
                        }
                    } else if (pattern == 2 && c == 1) {
                        byte[] s = next.toByteArrayLess3();
                        next.reset();
                        pattern = 0;
                        if (s != null) {
                            return s;
                        }
                    } else if (pattern != 0) {
                        pattern = 0;
                    }
                } else {
                    pattern = 0;
                }
            }
            byte[] s = next.toByteArray();
            next.reset();
            if (s.length > 0) {
                return s;
            } else {
                return null;
            }
        }


    }

    static class MyByteArrayOutputStream extends ByteArrayOutputStream {

        public byte[] toByteArrayLess3() {
            if (count > 3) {
                return Arrays.copyOf(buf, count - 3 > 0 ? count - 3 : 0);
            } else {
                return null;
            }

        }


    }
}
