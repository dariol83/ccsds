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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class LineHexDumpChannelReaderTest {

    private static final String FILE_TM1 = "dumpFile_tm_1.hex";
    private static final String FILE_TM1_WITH_EMPTY_LINES = "dumpFile_tm_1_empty_lines.hex";

    @Test
    void testReadNext() throws IOException {
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        byte[] frame = null;
        int counter = 0;

        while ((frame = reader.readNext()) != null) {
            assertNotNull(frame);
            ++counter;
        }
        reader.close();

        assertEquals(152, counter);
    }

    @Test
    void testGet() throws IOException {
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        byte[] frame = null;
        int counter = 0;

        while ((frame = reader.get()) != null) {
            assertNotNull(frame);
            ++counter;
        }
        reader.close();

        assertEquals(152, counter);
    }

    @Test
    void testReadNextWithBuffer() throws IOException {
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        byte[] frame = new byte[4098];
        int counter = 0;
        int read;
        while ((read = reader.readNext(frame, 0, frame.length)) != -1) {
            assertEquals(1115 + 4 + 160, read);
            ++counter;
        }
        reader.close();

        assertEquals(152, counter);
    }

    @Test
    void testBufferTooSmall() {
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        try {
            reader.readNext(new byte[1000], 0, 1000);
            fail("IOException expected");
        } catch (IOException e) {
            // Good
        }
    }

    @Test
    void testFileWithEmptyLines() throws IOException {
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1_WITH_EMPTY_LINES));
        byte[] frame = null;
        int counter = 0;

        while ((frame = reader.get()) != null) {
            assertNotNull(frame);
            ++counter;
        }
        reader.close();

        assertEquals(152, counter);
    }
}