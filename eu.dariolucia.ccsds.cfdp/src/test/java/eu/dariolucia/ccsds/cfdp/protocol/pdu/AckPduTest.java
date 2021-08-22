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

class AckPduTest {

    private final byte[] P1_NOERROR     = StringUtil.toByteArray("22 0003 21 F11204 9155 A2A1A3 06 43 12".replace(" ", ""));

    @Test
    public void testAckPduParsing() {
        AckPdu pdu = new AckPdu(P1_NOERROR);
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, pdu.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(3, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(ConditionCode.CC_POS_ACK_LIMIT_REACHED, pdu.getConditionCode());
        assertEquals(DirectiveCode.DC_EOF_PDU, pdu.getDirectiveCode());
        assertEquals(3, pdu.getDirectiveSubtypeCode());
        assertEquals(AckPdu.TransactionStatus.TERMINATED, pdu.getTransactionStatus());

        assertNotNull(pdu.toString());
    }

    @Test
    public void testDecoderByteArray() {
        CfdpPdu pdu = CfdpPduDecoder.decode(P1_NOERROR);
        assertEquals(AckPdu.class, pdu.getClass());
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, pdu.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(3, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(ConditionCode.CC_POS_ACK_LIMIT_REACHED, ((AckPdu) pdu).getConditionCode());
        assertEquals(DirectiveCode.DC_EOF_PDU, ((AckPdu) pdu).getDirectiveCode());
        assertEquals(3, ((AckPdu) pdu).getDirectiveSubtypeCode());
        assertEquals(AckPdu.TransactionStatus.TERMINATED, ((AckPdu) pdu).getTransactionStatus());
    }

    @Test
    public void testDecoderStream() throws IOException {
        CfdpPdu pdu = CfdpPduDecoder.decode(new ByteArrayInputStream(P1_NOERROR));
        assertEquals(AckPdu.class, pdu.getClass());
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, pdu.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(3, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(ConditionCode.CC_POS_ACK_LIMIT_REACHED, ((AckPdu) pdu).getConditionCode());
        assertEquals(DirectiveCode.DC_EOF_PDU, ((AckPdu) pdu).getDirectiveCode());
        assertEquals(3, ((AckPdu) pdu).getDirectiveSubtypeCode());
        assertEquals(AckPdu.TransactionStatus.TERMINATED, ((AckPdu) pdu).getTransactionStatus());
    }
}