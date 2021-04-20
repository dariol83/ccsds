package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FileDataPduBuilderTest {

    @Test
    public void testAckPduBuilding() throws IOException {
        FileDataPduBuilder builder = new FileDataPduBuilder()
                .setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER)
                .setLargeFile(false)
                .setSegmentMetadataPresent(false)
                .setSegmentationControlPreserved(false)
                .setAcknowledged(true)
                .setCrcPresent(true)
                .setEntityIdLength(4)
                .setDestinationEntityId(0x00A2A1A3)
                .setSourceEntityId(0x00F11204)
                .setTransactionSequenceNumber(123456, 3)
                .setOffset(14)
                .setFileData(new byte[] {14, 15, 16});

        FileDataPdu pdu = builder.build();

        // PDU check
        assertEquals(0b001, pdu.getVersion());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(7, pdu.getDataFieldLength());
        assertEquals(4, pdu.getEntityIdLength());
        assertEquals(3, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(123456L, pdu.getTransactionSequenceNumber());
        assertEquals(FileDataPdu.RCS_NOT_PRESENT, pdu.getRecordContinuationState());
        assertEquals(-1, pdu.getSegmentMetadataLength());
        assertNull(pdu.getSegmentMetadata());
        assertEquals(14, pdu.getOffset());
        assertArrayEquals(new byte[] {14, 15, 16}, pdu.getFileData());

        // Builder check
        assertEquals(0b001, builder.getVersion());
        assertFalse(builder.isLargeFile());
        assertFalse(builder.isSegmentMetadataPresent());
        assertFalse(builder.isSegmentationControlPreserved());
        assertTrue(builder.isCrcPresent());
        assertTrue(builder.isAcknowledged());
        assertEquals(4, builder.getEntityIdLength());
        assertEquals(3, builder.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, builder.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, builder.getDestinationEntityId());
        assertEquals(123456L, builder.getTransactionSequenceNumber());
        assertEquals(FileDataPdu.RCS_NOT_PRESENT, builder.getRecordContinuationState());
        assertEquals(-1, builder.getSegmentMetadataLength());
        assertNull(builder.getSegmentMetadata());
        assertEquals(14, builder.getOffset());
        assertArrayEquals(new byte[] {14, 15, 16}, builder.getFileData());
    }
}