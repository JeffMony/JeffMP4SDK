package com.jeffmony.mp4parser;

import org.mp4parser.tools.IsoTypeReader;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;

public class MP4Structure {

    private List<String> containers = Arrays.asList(
            "moov",
            "trak",
            "mdia",
            "minf",
            "udta",
            "stbl"
    );

    private static class Holder {
        public static MP4Structure instance = new MP4Structure();
    }

    public static MP4Structure getInstance() {
        return Holder.instance;
    }

    public void printMP4Structure(FileChannel fc, int level, long start, long end) throws IOException {
        fc.position(start);
        if(end <= 0) {
            end = start + fc.size();
            LogUtils.i("Setting END to " + end);
        }
        while (end - fc.position() > 8) {
            long begin = fc.position();
            ByteBuffer bb = ByteBuffer.allocate(8);
            fc.read(bb);
            ((Buffer)bb).rewind();
            long size = IsoTypeReader.readUInt32(bb);
            String type = IsoTypeReader.read4cc(bb);
            long fin = begin + size;
            // indent by the required number of spaces
            for (int i = 0; i < level; i++) {
                LogUtils.i(" ");
            }
            String tempStr = new String(type.getBytes(), "UTF-8");
            LogUtils.i(tempStr + "@" + (begin) + " size: " + size);
            if (containers.contains(type)) {
                printMP4Structure(fc, level + 1, begin + 8, fin);
                if(fc.position() != fin) {
                    LogUtils.i("End of container contents at " + fc.position());
                    LogUtils.i("  FIN = " + fin);
                }
            }
            fc.position(fin);
        }
    }
}
