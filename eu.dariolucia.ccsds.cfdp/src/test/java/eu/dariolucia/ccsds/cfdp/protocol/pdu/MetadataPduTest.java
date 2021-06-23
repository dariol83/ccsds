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

import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpChecksumRegistry;
import eu.dariolucia.ccsds.cfdp.protocol.decoder.CfdpPduDecoder;
import eu.dariolucia.ccsds.tmtc.algorithm.Crc16Algorithm;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class MetadataPduTest {

    private final byte[] P1_NOERROR     = StringUtil.toByteArray("26 002F 02 01 01000102074000002800107465737466696C655F61636B2E62696E15726563765F7465737466696C655F61636B2E62696EC6ED".replace(" ", ""));

    @Test
    public void testMetadataPduParsing() {
        MetadataPdu pdu = new MetadataPdu(P1_NOERROR);
        assertEquals(0b001, pdu.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, pdu.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, pdu.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isClosureRequested());
        assertFalse(pdu.isAcknowledged());
        assertEquals(1, pdu.getEntityIdLength());
        assertEquals(3, pdu.getTransactionSequenceNumberLength());
        assertEquals(47, pdu.getDataFieldLength());
        assertEquals(1L, pdu.getSourceEntityId());
        assertEquals(2L, pdu.getDestinationEntityId());
        assertEquals(65537, pdu.getTransactionSequenceNumber());
        assertEquals("testfile_ack.bin", pdu.getSourceFileName());
        assertEquals("recv_testfile_ack.bin", pdu.getDestinationFileName());
        assertEquals(0, pdu.getOptions().size());
        assertEquals(10240, pdu.getFileSize());
        assertEquals(CfdpChecksumRegistry.MODULAR_CHECKSUM_TYPE, pdu.getChecksumType());
        assertTrue(pdu.isCrcValid());

        assertNotNull(pdu.toString());
    }

    @Test
    public void testDecoderByteArray() {
        CfdpPdu decoded = CfdpPduDecoder.decode(P1_NOERROR);
        assertEquals(MetadataPdu.class, decoded.getClass());
        MetadataPdu pdu = (MetadataPdu) decoded;
        assertEquals(0b001, decoded.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, decoded.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, decoded.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isClosureRequested());
        assertFalse(pdu.isAcknowledged());
        assertEquals(1, pdu.getEntityIdLength());
        assertEquals(3, pdu.getTransactionSequenceNumberLength());
        assertEquals(47, pdu.getDataFieldLength());
        assertEquals(1L, pdu.getSourceEntityId());
        assertEquals(2L, pdu.getDestinationEntityId());
        assertEquals(65537, pdu.getTransactionSequenceNumber());
        assertEquals("testfile_ack.bin", pdu.getSourceFileName());
        assertEquals("recv_testfile_ack.bin", pdu.getDestinationFileName());
        assertEquals(0, pdu.getOptions().size());
        assertTrue(pdu.isCrcValid());
    }

    @Test
    public void testDecoderStream() throws IOException {
        CfdpPdu decoded = CfdpPduDecoder.decode(new ByteArrayInputStream(P1_NOERROR));
        assertEquals(MetadataPdu.class, decoded.getClass());
        MetadataPdu pdu = (MetadataPdu) decoded;
        assertEquals(0b001, decoded.getVersion());
        assertEquals(CfdpPdu.PduType.FILE_DIRECTIVE, decoded.getType());
        assertEquals(CfdpPdu.Direction.TOWARD_FILE_RECEIVER, decoded.getDirection());
        assertFalse(pdu.isLargeFile());
        assertFalse(pdu.isSegmentMetadata());
        assertFalse(pdu.isSegmentationControlPreserved());
        assertTrue(pdu.isCrcPresent());
        assertTrue(pdu.isClosureRequested());
        assertFalse(pdu.isAcknowledged());
        assertEquals(1, pdu.getEntityIdLength());
        assertEquals(3, pdu.getTransactionSequenceNumberLength());
        assertEquals(47, pdu.getDataFieldLength());
        assertEquals(1L, pdu.getSourceEntityId());
        assertEquals(2L, pdu.getDestinationEntityId());
        assertEquals(65537, pdu.getTransactionSequenceNumber());
        assertEquals("testfile_ack.bin", pdu.getSourceFileName());
        assertEquals("recv_testfile_ack.bin", pdu.getDestinationFileName());
        assertEquals(0, pdu.getOptions().size());
        assertTrue(pdu.isCrcValid());
    }
}