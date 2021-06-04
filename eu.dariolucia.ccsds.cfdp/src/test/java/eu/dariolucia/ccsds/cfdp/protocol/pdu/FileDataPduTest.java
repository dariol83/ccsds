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

package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.protocol.decoder.CfdpPduDecoder;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FileDataPduTest {

    private final byte[] P1_NOERROR     = StringUtil.toByteArray("33 0018 29 F11204 9155 A2A1A3 C4 01020304 0000000000000007 0102030405060708090A0B".replace(" ", ""));

    @Test
    public void testFileDataPduParsing() {
        FileDataPdu pdu = new FileDataPdu(P1_NOERROR);
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DATA, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, pdu.getDirection());
        assertTrue(pdu.isLargeFile());
        assertTrue(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(24, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(FileDataPdu.RCS_START_END, pdu.getRecordContinuationState());
        assertEquals(4, pdu.getSegmentMetadataLength());
        assertArrayEquals(new byte[] {1,2,3,4}, pdu.getSegmentMetadata());
        assertEquals(7, pdu.getOffset());
        assertArrayEquals(new byte[] {1,2,3,4,5,6,7,8,9,10,11}, pdu.getFileData());

        assertNotNull(pdu.toString());

        assertArrayEquals(P1_NOERROR, pdu.getPdu());
    }

    @Test
    public void testDecoderByteArray() {
        CfdpPdu pdu = CfdpPduDecoder.decode(P1_NOERROR);
        assertEquals(FileDataPdu.class, pdu.getClass());
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DATA, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, pdu.getDirection());
        assertTrue(pdu.isLargeFile());
        assertTrue(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(24, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(FileDataPdu.RCS_START_END, ((FileDataPdu)pdu).getRecordContinuationState());
        assertEquals(4, ((FileDataPdu)pdu).getSegmentMetadataLength());
        assertArrayEquals(new byte[] {1,2,3,4}, ((FileDataPdu)pdu).getSegmentMetadata());
        assertEquals(7, ((FileDataPdu)pdu).getOffset());
        assertArrayEquals(new byte[] {1,2,3,4,5,6,7,8,9,10,11}, ((FileDataPdu)pdu).getFileData());
    }

    @Test
    public void testDecoderStream() throws IOException {
        CfdpPdu pdu = CfdpPduDecoder.decode(new ByteArrayInputStream(P1_NOERROR));
        assertEquals(FileDataPdu.class, pdu.getClass());
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DATA, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, pdu.getDirection());
        assertTrue(pdu.isLargeFile());
        assertTrue(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(24, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(FileDataPdu.RCS_START_END, ((FileDataPdu)pdu).getRecordContinuationState());
        assertEquals(4, ((FileDataPdu)pdu).getSegmentMetadataLength());
        assertArrayEquals(new byte[] {1,2,3,4}, ((FileDataPdu)pdu).getSegmentMetadata());
        assertEquals(7, ((FileDataPdu)pdu).getOffset());
        assertArrayEquals(new byte[] {1,2,3,4,5,6,7,8,9,10,11}, ((FileDataPdu)pdu).getFileData());
    }
}