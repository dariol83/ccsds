package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.EndOfFilePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.EntityIdTLV;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class EndOfFilePduBuilderTest {

    @Test
    public void testEndOfFilePduBuilding() throws IOException {
        EndOfFilePdu pdu = new EndOfFilePduBuilder()
                .setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER)
                .setLargeFile(false)
                .setSegmentMetadata(false)
                .setSegmentationControlPreserved(false)
                .setAcknowledged(true)
                .setCrcPresent(true)
                .setEntityIdLength(4)
                .setDestinationEntityId(0x00A2A1A3)
                .setSourceEntityId(0x00F11204)
                .setTransactionSequenceNumber(123456, 3)
                .setConditionCode(FileDirectivePdu.CC_NOERROR, null)
                .setFileChecksum(123)
                .setFileSize(45678)
                .build();

        assertEquals(0b001, pdu.getVersion());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(9, pdu.getDataFieldLength());
        assertEquals(4, pdu.getEntityIdLength());
        assertEquals(3, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(123456L, pdu.getTransactionSequenceNumber());
        assertEquals(FileDirectivePdu.CC_NOERROR, pdu.getConditionCode());
        assertEquals(123L, pdu.getFileChecksum());
        assertEquals(45678L, pdu.getFileSize());
        assertNull(pdu.getFaultLocation());

        pdu = new EndOfFilePduBuilder()
                .setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER)
                .setLargeFile(false)
                .setSegmentMetadata(false)
                .setSegmentationControlPreserved(false)
                .setAcknowledged(true)
                .setCrcPresent(true)
                .setEntityIdLength(4)
                .setDestinationEntityId(0x00A2A1A3)
                .setSourceEntityId(0x00F11204)
                .setTransactionSequenceNumber(123456, 3)
                .setConditionCode(FileDirectivePdu.CC_CHECK_LIMIT_REACHED, new EntityIdTLV(1234567L, 4))
                .setFileChecksum(123)
                .setFileSize(45678)
                .build();

        assertEquals(0b001, pdu.getVersion());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(15, pdu.getDataFieldLength());
        assertEquals(4, pdu.getEntityIdLength());
        assertEquals(3, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(123456L, pdu.getTransactionSequenceNumber());
        assertEquals(FileDirectivePdu.CC_CHECK_LIMIT_REACHED, pdu.getConditionCode());
        assertEquals(123L, pdu.getFileChecksum());
        assertEquals(45678L, pdu.getFileSize());
        assertNotNull(pdu.getFaultLocation());
        assertEquals(1234567L, pdu.getFaultLocation().getEntityId());
    }
}