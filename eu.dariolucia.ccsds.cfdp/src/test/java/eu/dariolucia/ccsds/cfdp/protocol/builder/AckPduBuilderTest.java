package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.AckPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class AckPduBuilderTest {

    @Test
    public void testAckPduBuilding() throws IOException {
        AckPduBuilder builder = new AckPduBuilder()
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
                .setConditionCode(FileDirectivePdu.CC_NOERROR)
                .setDirectiveCode((byte) 0x01)
                .setDirectiveSubtypeCode((byte) 0x02)
                .setTransactionStatus(AckPdu.TransactionStatus.ACTIVE);

        AckPdu pdu = builder.build();

        // PDU check
        assertEquals(0b001, pdu.getVersion());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isAcknowledged());
        assertEquals(3, pdu.getDataFieldLength());
        assertEquals(4, pdu.getEntityIdLength());
        assertEquals(3, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(123456L, pdu.getTransactionSequenceNumber());
        assertEquals(FileDirectivePdu.CC_NOERROR, pdu.getConditionCode());
        assertEquals(1, pdu.getDirectiveCode());
        assertEquals(2, pdu.getDirectiveSubtypeCode());
        assertEquals(AckPdu.TransactionStatus.ACTIVE, pdu.getTransactionStatus());

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
        assertEquals(FileDirectivePdu.CC_NOERROR, builder.getConditionCode());
        assertEquals(1, builder.getDirectiveCode());
        assertEquals(2, builder.getDirectiveSubtypeCode());
        assertEquals(AckPdu.TransactionStatus.ACTIVE, builder.getTransactionStatus());
    }
}