/*
 * Copyright 2012 Sebastian Annies, Hamburg
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mp4parser.muxer.tracks;

import org.mp4parser.boxes.adobe.ActionMessageFormat0SampleEntryBox;
import org.mp4parser.boxes.iso14496.part12.CompositionTimeToSample;
import org.mp4parser.boxes.iso14496.part12.SampleDependencyTypeBox;
import org.mp4parser.boxes.iso14496.part12.SubSampleInformationBox;
import org.mp4parser.boxes.sampleentry.SampleEntry;
import org.mp4parser.muxer.AbstractTrack;
import org.mp4parser.muxer.Sample;
import org.mp4parser.muxer.SampleImpl;
import org.mp4parser.muxer.TrackMetaData;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public class Amf0Track extends AbstractTrack {
    private SortedMap<Long, byte[]> rawSamples = new TreeMap<>();
    private TrackMetaData trackMetaData = new TrackMetaData();
    private ActionMessageFormat0SampleEntryBox amf0;

    /**
     * Creates a new AMF0 track from
     *
     * @param rawSamples raw samples of the track
     */
    public Amf0Track(Map<Long, byte[]> rawSamples) {
        super("amf0");
        this.rawSamples = new TreeMap<>(rawSamples);
        trackMetaData.setCreationTime(new Date());
        trackMetaData.setModificationTime(new Date());
        trackMetaData.setTimescale(1000); // Text tracks use millieseconds
        trackMetaData.setLanguage("eng");

        amf0 = new ActionMessageFormat0SampleEntryBox();
        amf0.setDataReferenceIndex(1);

    }

    public List<Sample> getSamples() {
        LinkedList<Sample> samples = new LinkedList<>();
        for (byte[] bytes : rawSamples.values()) {
            samples.add(new SampleImpl(ByteBuffer.wrap(bytes), amf0));
        }
        return samples;
    }

    public void close() throws IOException {
        // no resources involved - doing nothing
    }

    public List<SampleEntry> getSampleEntries() {
        return Collections.<SampleEntry>singletonList(amf0);
    }

    public long[] getSampleDurations() {
        LinkedList<Long> keys = new LinkedList<>(rawSamples.keySet());
        Collections.sort(keys);
        long[] rc = new long[keys.size()];
        long lastTimeStamp = 0;
        for (int i = 0; i < keys.size(); i++) {
            long key = keys.get(i);
            long delta = key - lastTimeStamp;
            rc[i] = delta;
        }
        return rc;
    }

    public List<CompositionTimeToSample.Entry> getCompositionTimeEntries() {
        // AMF0 tracks do not have Composition Time
        return null;
    }

    public long[] getSyncSamples() {
        // AMF0 tracks do not have Sync Samples
        return null;
    }

    public List<SampleDependencyTypeBox.Entry> getSampleDependencies() {
        // AMF0 tracks do not have Sample Dependencies
        return null;
    }

    public TrackMetaData getTrackMetaData() {
        return trackMetaData;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getHandler() {
        return "data";
    }

    public SubSampleInformationBox getSubsampleInformationBox() {
        return null;
    }

}
