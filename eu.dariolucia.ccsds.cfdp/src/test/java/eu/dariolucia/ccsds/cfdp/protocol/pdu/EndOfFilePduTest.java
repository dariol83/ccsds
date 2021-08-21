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

class EndOfFilePduTest {

    private final byte[] P1_NOERROR     = StringUtil.toByteArray("22 000A 21 F11204 9155 A2A1A3 04 00 C5A134D2 0032112D".replace(" ", ""));
    private final byte[] P1_ERROR       = StringUtil.toByteArray("22 000F 21 F11204 9155 A2A1A3 04 B0 C5A134D2 0032112D 0603F4F5F6".replace(" ", ""));

    @Test
    public void testEndOfFilePduParsing() {
        EndOfFilePdu pdu = new EndOfFilePdu(P1_NOERROR);
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, pdu.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(10, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(ConditionCode.CC_NOERROR, pdu.getConditionCode());
        assertEquals(0xC5A134D2, pdu.getFileChecksum());
        assertEquals(0x0032112D, pdu.getFileSize());
        assertNull(pdu.getFaultLocation());

        pdu = new EndOfFilePdu(P1_ERROR);
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, pdu.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(15, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(ConditionCode.CC_UNSUPPORTED_CHECKSUM_TYPE, pdu.getConditionCode());
        assertEquals(0xC5A134D2, pdu.getFileChecksum());
        assertEquals(0x0032112D, pdu.getFileSize());
        assertNotNull(pdu.getFaultLocation());
        assertEquals(0x0000000000F4F5F6L, pdu.getFaultLocation().getEntityId());

        assertNotNull(pdu.toString());
    }

    @Test
    public void testDecoderByteArray() {
        CfdpPdu decoded = CfdpPduDecoder.decode(P1_NOERROR);
        assertEquals(EndOfFilePdu.class, decoded.getClass());
        assertEquals(0b001, decoded.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, decoded.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, decoded.getDirection());
        assertFalse(decoded.isLargeFile());
        assertFalse(decoded.isSegmentMetadata());
        assertFalse(decoded.isSegmentationControlPreserved());
        assertTrue(decoded.isCrcPresent());
        assertTrue(decoded.isAcknowledged());
        assertEquals(10, decoded.getDataFieldLength());
        assertEquals(3, decoded.getEntityIdLength());
        assertEquals(2, decoded.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, decoded.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, decoded.getDestinationEntityId());
        assertEquals(0x0000000000009155L, decoded.getTransactionSequenceNumber());
        assertEquals(ConditionCode.CC_NOERROR, ((EndOfFilePdu)decoded).getConditionCode());
        assertEquals(0xC5A134D2, ((EndOfFilePdu)decoded).getFileChecksum());
        assertEquals(0x0032112D, ((EndOfFilePdu)decoded).getFileSize());
        assertNull(((EndOfFilePdu)decoded).getFaultLocation());
    }

    @Test
    public void testDecoderStream() throws IOException {
        CfdpPdu decoded = CfdpPduDecoder.decode(new ByteArrayInputStream(P1_NOERROR));
        assertEquals(EndOfFilePdu.class, decoded.getClass());
        assertEquals(0b001, decoded.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, decoded.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, decoded.getDirection());
        assertFalse(decoded.isLargeFile());
        assertFalse(decoded.isSegmentMetadata());
        assertFalse(decoded.isSegmentationControlPreserved());
        assertTrue(decoded.isCrcPresent());
        assertTrue(decoded.isAcknowledged());
        assertEquals(10, decoded.getDataFieldLength());
        assertEquals(3, decoded.getEntityIdLength());
        assertEquals(2, decoded.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, decoded.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, decoded.getDestinationEntityId());
        assertEquals(0x0000000000009155L, decoded.getTransactionSequenceNumber());
        assertEquals(ConditionCode.CC_NOERROR, ((EndOfFilePdu)decoded).getConditionCode());
        assertEquals(0xC5A134D2, ((EndOfFilePdu)decoded).getFileChecksum());
        assertEquals(0x0032112D, ((EndOfFilePdu)decoded).getFileSize());
        assertNull(((EndOfFilePdu)decoded).getFaultLocation());
    }
}