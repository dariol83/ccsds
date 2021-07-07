/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.cfdp.entity.segmenters;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileSegmentTest {

    @Test
    public void testFileSegmentCreation() {
        FileSegment eof = FileSegment.eof();
        assertTrue(eof.isEof());
        assertNotNull(eof.toString());

        FileSegment full = FileSegment.segment(100, new byte[] {1,2,3}, new byte[] {0}, FileDataPdu.RCS_NO_START_END);
        assertEquals(100, full.getOffset());
        assertArrayEquals(new byte[] {1,2,3}, full.getData());
        assertArrayEquals(new byte[] {0}, full.getMetadata());
        assertEquals(FileDataPdu.RCS_NO_START_END, full.getRecordContinuationState());
        assertNotNull(full.toString());
    }
}