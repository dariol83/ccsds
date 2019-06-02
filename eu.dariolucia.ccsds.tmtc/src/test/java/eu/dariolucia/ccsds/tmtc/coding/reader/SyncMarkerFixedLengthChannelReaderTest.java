/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.coding.reader;

import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class SyncMarkerFixedLengthChannelReaderTest {

    private static final String FILE_TM1 = "dumpFile_tm_1.hex";

    @Test
    void testReadNext() throws IOException {
        // Prepare the input
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        byte[] frame = null;
        while((frame = reader.readNext()) != null) {
            bos.writeBytes(frame);
        }
        bos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        SyncMarkerFixedLengthChannelReader smReader = new SyncMarkerFixedLengthChannelReader(bis, TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER,  1275);

        int counter = 0;
        while ((frame = smReader.readNext()) != null) {
            ++counter;
        }
        smReader.close();

        assertEquals(152, counter);
    }

    @Test
    void testReadNextWrongLength() throws IOException {
        // Prepare the input
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        byte[] frame = null;
        while((frame = reader.readNext()) != null) {
            bos.writeBytes(frame);
        }
        bos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        SyncMarkerFixedLengthChannelReader smReader = new SyncMarkerFixedLengthChannelReader(bis, TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER,  1279);

        int counter = 0;
        try {
            while ((frame = smReader.readNext()) != null) {
                ++counter;
            }
        } catch (IOException e) {
            smReader.close();
            assertEquals(67, counter);
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void testReadNextThrowOutSync() throws IOException {
        // Prepare the input
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        byte[] frame = null;
        while((frame = reader.readNext()) != null) {
            bos.writeBytes(frame);
        }
        bos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        SyncMarkerFixedLengthChannelReader smReader = new SyncMarkerFixedLengthChannelReader(bis, TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER,  1279, true, true);

        int counter = 0;
        try {
            while ((frame = smReader.readNext()) != null) {
                ++counter;
            }
        } catch (SynchronizationLostException e) {
            smReader.close();
            assertEquals(1, counter);
        } catch (Exception e) {
            fail(e);
        }
    }
}