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

class KeepAlivePduTest {

    private final byte[] P1_NOERROR     = StringUtil.toByteArray("28 0005 21 F11204 9155 A2A1A3 0C 00000020".replace(" ", ""));

    @Test
    public void testKeepAlivePduParsing() {
        KeepAlivePdu pdu = new KeepAlivePdu(P1_NOERROR);
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_SENDER, pdu.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertFalse(pdu.isCrcPresent());
        assertTrue(pdu.isCrcValid());
        assertTrue(pdu.isAcknowledged());
        assertEquals(5, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(32, pdu.getProgress());

        assertNotNull(pdu.toString());
    }

    @Test
    public void testDecoderByteArray() {
        CfdpPdu cpdu = CfdpPduDecoder.decode(P1_NOERROR);
        assertEquals(KeepAlivePdu.class, cpdu.getClass());
        KeepAlivePdu pdu = (KeepAlivePdu) cpdu;
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_SENDER, pdu.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertFalse(pdu.isCrcPresent());
        assertTrue(pdu.isCrcValid());
        assertTrue(pdu.isAcknowledged());
        assertEquals(5, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(32, pdu.getProgress());
    }

    @Test
    public void testDecoderStream() throws IOException {
        CfdpPdu cpdu = CfdpPduDecoder.decode(new ByteArrayInputStream(P1_NOERROR));
        assertEquals(KeepAlivePdu.class, cpdu.getClass());
        KeepAlivePdu pdu = (KeepAlivePdu) cpdu;
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_SENDER, pdu.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertFalse(pdu.isCrcPresent());
        assertTrue(pdu.isCrcValid());
        assertTrue(pdu.isAcknowledged());
        assertEquals(5, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(32, pdu.getProgress());
    }
}