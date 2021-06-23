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

package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.MetadataPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MetadataPduBuilderTest {

    @Test
    public void testMetadataPduBuilding() {
        MetadataPduBuilder builder = new MetadataPduBuilder()
                .setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER)
                .setLargeFile(false)
                .setSegmentMetadataPresent(false)
                .setSegmentationControlPreserved(false)
                .setAcknowledged(false)
                .setCrcPresent(true)
                .setEntityIdLength(4)
                .setDestinationEntityId(0x00A2A1A3)
                .setSourceEntityId(0x00F11204)
                .setTransactionSequenceNumber(123456, 3)
                .setFileSize(45678)
                .setSourceFileName("s1.txt")
                .setChecksumType((byte) 0)
                .setClosureRequested(true)
                .setDestinationFileName("d1.txt")
                .addOption(new FlowLabelTLV(new byte[] { 1, 2, 3, 4}))
                .addOption(new FilestoreRequestTLV(ActionCode.RENAME, "d1.txt", "d2.txt"))
                .addOption(new MessageToUserTLV("testMessage".getBytes(StandardCharsets.ISO_8859_1)))
                .addOption(new FaultHandlerOverrideTLV(FileDirectivePdu.CC_INACTIVITY_DETECTED, FaultHandlerOverrideTLV.HandlerCode.IGNORE_ERROR));

        MetadataPdu pdu = builder.build();

        // PDU check
        assertEquals(0b001, pdu.getVersion());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isCrcValid());
        assertFalse(pdu.isAcknowledged());
        assertEquals(4, pdu.getEntityIdLength());
        assertEquals(3, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(123456L, pdu.getTransactionSequenceNumber());
        assertEquals(45678, pdu.getFileSize());
        assertEquals(0, pdu.getChecksumType());
        assertEquals("s1.txt", pdu.getSourceFileName());
        assertEquals("d1.txt", pdu.getDestinationFileName());
        assertTrue(pdu.isClosureRequested());

        assertEquals(61, pdu.getDataFieldLength());

        assertEquals(4, pdu.getOptions().size());

        assertEquals(FlowLabelTLV.class, pdu.getOptions().get(0).getClass());
        assertArrayEquals(new byte[] { 1, 2, 3, 4}, ((FlowLabelTLV) pdu.getOptions().get(0)).getData());
        assertEquals(FilestoreRequestTLV.class, pdu.getOptions().get(1).getClass());
        assertEquals("d1.txt", ((FilestoreRequestTLV) pdu.getOptions().get(1)).getFirstFileName());
        assertEquals("d2.txt", ((FilestoreRequestTLV) pdu.getOptions().get(1)).getSecondFileName());
        assertEquals(ActionCode.RENAME, ((FilestoreRequestTLV) pdu.getOptions().get(1)).getActionCode());
        assertEquals(MessageToUserTLV.class, pdu.getOptions().get(2).getClass());
        assertEquals("testMessage", new String(((MessageToUserTLV) pdu.getOptions().get(2)).getData(), StandardCharsets.ISO_8859_1));
        assertEquals(FaultHandlerOverrideTLV.class, pdu.getOptions().get(3).getClass());
        assertEquals(FileDirectivePdu.CC_INACTIVITY_DETECTED, ((FaultHandlerOverrideTLV) pdu.getOptions().get(3)).getConditionCode());
        assertEquals(FaultHandlerOverrideTLV.HandlerCode.IGNORE_ERROR, ((FaultHandlerOverrideTLV) pdu.getOptions().get(3)).getHandlerCode());

        // Builder check
        assertEquals(0b001, builder.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE,builder.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, builder.getDirection());
        assertFalse(builder.isLargeFile());
        assertFalse(builder.isSegmentMetadataPresent());
        assertFalse(builder.isSegmentationControlPreserved());
        assertTrue(builder.isCrcPresent());
        assertFalse(builder.isAcknowledged());
        assertEquals(4, builder.getEntityIdLength());
        assertEquals(3, builder.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, builder.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, builder.getDestinationEntityId());
        assertEquals(123456L, builder.getTransactionSequenceNumber());
        assertEquals(45678, builder.getFileSize());
        assertEquals(0, builder.getChecksumType());
        assertEquals("s1.txt", builder.getSourceFileName());
        assertEquals("d1.txt", builder.getDestinationFileName());
        assertTrue(builder.isClosureRequested());

        assertEquals(4, builder.getOptions().size());

        assertEquals(FlowLabelTLV.class, builder.getOptions().get(0).getClass());
        assertArrayEquals(new byte[] { 1, 2, 3, 4}, ((FlowLabelTLV) builder.getOptions().get(0)).getData());
        assertEquals(FilestoreRequestTLV.class, builder.getOptions().get(1).getClass());
        assertEquals("d1.txt", ((FilestoreRequestTLV) builder.getOptions().get(1)).getFirstFileName());
        assertEquals("d2.txt", ((FilestoreRequestTLV) builder.getOptions().get(1)).getSecondFileName());
        assertEquals(ActionCode.RENAME, ((FilestoreRequestTLV) builder.getOptions().get(1)).getActionCode());
        assertEquals(MessageToUserTLV.class, builder.getOptions().get(2).getClass());
        assertEquals("testMessage", new String(((MessageToUserTLV) builder.getOptions().get(2)).getData(), StandardCharsets.ISO_8859_1));
        assertEquals(FaultHandlerOverrideTLV.class, builder.getOptions().get(3).getClass());
        assertEquals(FileDirectivePdu.CC_INACTIVITY_DETECTED, ((FaultHandlerOverrideTLV) builder.getOptions().get(3)).getConditionCode());
        assertEquals(FaultHandlerOverrideTLV.HandlerCode.IGNORE_ERROR, ((FaultHandlerOverrideTLV) builder.getOptions().get(3)).getHandlerCode());

    }
}