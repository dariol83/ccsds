package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EndOfFilePduTest {

    private final byte[] P1_NOERROR     = StringUtil.toByteArray("26 0009 21 F11204 9155 A2A1A3 00 C5A134D2 0032112D".replace(" ", ""));
    private final byte[] P1_ERROR       = StringUtil.toByteArray("26 000E 21 F11204 9155 A2A1A3 B0 C5A134D2 0032112D 0603F4F5F6".replace(" ", ""));

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
        assertEquals(9, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(FileDirectivePdu.CC_NOERROR, pdu.getConditionCode());
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
        assertEquals(14, pdu.getDataFieldLength());
        assertEquals(3, pdu.getEntityIdLength());
        assertEquals(2, pdu.getTransactionSequenceNumberLength());
        assertEquals(0x0000000000F11204L, pdu.getSourceEntityId());
        assertEquals(0x0000000000A2A1A3L, pdu.getDestinationEntityId());
        assertEquals(0x0000000000009155L, pdu.getTransactionSequenceNumber());
        assertEquals(FileDirectivePdu.CC_UNSUPPORTED_CHECKSUM_TYPE, pdu.getConditionCode());
        assertEquals(0xC5A134D2, pdu.getFileChecksum());
        assertEquals(0x0032112D, pdu.getFileSize());
        assertNotNull(pdu.getFaultLocation());
        assertEquals(0x0000000000F4F5F6L, pdu.getFaultLocation().getEntityId());
    }
}