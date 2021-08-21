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
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FinishedPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.EntityIdTLV;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinishedPduBuilderTest {

    @Test
    public void testFinishedPduBuilding() {
        FinishedPduBuilder builder = new FinishedPduBuilder()
                .setDirection(CfdpPdu.Direction.TOWARD_FILE_SENDER)
                .setLargeFile(false)
                .setSegmentMetadataPresent(false)
                .setSegmentationControlPreserved(false)
                .setAcknowledged(true)
                .setCrcPresent(false)
                .setEntityIdLength(4)
                .setDestinationEntityId(0x00A2A1A3)
                .setSourceEntityId(0x00F11204)
                .setTransactionSequenceNumber(123456, 3)
                .setConditionCode(ConditionCode.CC_FILESTORE_REJECTION, new EntityIdTLV(2L, 4))
                .setDataComplete(true)
                .setFileStatus(FinishedPdu.FileStatus.DISCARDED_BY_FILESTORE);

        FinishedPdu pdu = builder.build();

        // PDU check
        assertEquals(0b001, pdu.getVersion());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertFalse(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(8, pdu.getDataFieldLength());
        assertEquals(4, pdu.getEntityIdLength());
        assertEquals(3, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(123456L, pdu.getTransactionSequenceNumber());
        assertEquals(ConditionCode.CC_FILESTORE_REJECTION, pdu.getConditionCode());
        assertEquals(FinishedPdu.FileStatus.DISCARDED_BY_FILESTORE, pdu.getFileStatus());

        // Builder check
        assertEquals(0b001, builder.getVersion());
        assertFalse(builder.isLargeFile());
        assertFalse(builder.isSegmentMetadataPresent());
        assertFalse(builder.isSegmentationControlPreserved());
        assertFalse(builder.isCrcPresent());
        assertTrue(builder.isAcknowledged());
        assertEquals(4, builder.getEntityIdLength());
        assertEquals(3, builder.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, builder.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, builder.getDestinationEntityId());
        assertEquals(123456L, builder.getTransactionSequenceNumber());
        assertEquals(ConditionCode.CC_FILESTORE_REJECTION, builder.getConditionCode());
        assertEquals(2L, builder.getFaultLocation().getEntityId());
        assertTrue(builder.isDataComplete());
        assertNotNull(builder.getFilestoreResponses());
        assertEquals(FinishedPdu.FileStatus.DISCARDED_BY_FILESTORE, builder.getFileStatus());
    }
}