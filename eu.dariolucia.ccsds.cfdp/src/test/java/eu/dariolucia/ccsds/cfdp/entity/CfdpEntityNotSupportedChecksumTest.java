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

package eu.dariolucia.ccsds.cfdp.entity;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.entity.indication.*;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.protocol.builder.MetadataPduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.MetadataPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FaultHandlerOverrideTLV;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfdpEntityNotSupportedChecksumTest {

    private static final Logger LOG = Logger.getLogger(CfdpEntityNotSupportedChecksumTest.class.getName());

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.ALL);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testNotSupportedChecksumTest() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001);
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);
        try {
            // Subscription to the entities
            EntityIndicationSubscriber s1 = new EntityIndicationSubscriber();
            e1.register(s1);
            EntityIndicationSubscriber s2 = new EntityIndicationSubscriber();
            e2.register(s2);
            // Enable reachability of the two entities
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 1);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 1);
            // Create file in filestore
            String path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.bin", 10); // 10 KB
            String destPath = "recv_testfile_ack.bin";
            // Create request and start transaction
            PutRequest fduTxReq = PutRequest.build(2, path, destPath, false, null);
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the file was transferred and it has exactly the same contents of the source file
            assertTrue(e2.getFilestore().fileExists(destPath));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));

            s1.clear();
            s2.clear();

            // Send a MetadataPDU to e2 from entity 1
            {
                LOG.info("Send a Metadata PDU to e2 from entity 1");
                MetadataPdu pdu = prepareMetadataPdu(123, true); // Abandon
                sendTo(2, pdu);
            }

            s2.waitForIndication(MetadataRecvIndication.class, 10000);
            s2.waitForIndication(AbandonedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.print();

            s2.clear();
            // Send a MetadataPDU to e2 from entity 1
            {
                LOG.info("Send a Metadata PDU to e2 from entity 1");
                MetadataPdu pdu = prepareMetadataPdu(124, false); // No action
                sendTo(2, pdu);
            }

            s2.waitForIndication(MetadataRecvIndication.class, 10000);
            FaultIndication fi = s2.waitForIndication(FaultIndication.class, 10000);
            assertEquals(ConditionCode.CC_UNSUPPORTED_CHECKSUM_TYPE, fi.getConditionCode());
            assertEquals(0, fi.getProgress());
            assertEquals(124, fi.getTransactionId());

            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.print();

            // Deactivate the UT layers
            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
            // Dispose the entities
            e1.dispose();
            e2.dispose();

            // Wait for the entity disposition
            s1.waitForIndication(EntityDisposedIndication.class, 1000);
            s2.waitForIndication(EntityDisposedIndication.class, 1000);

        } catch (Throwable e) {
            // Deactivate the UT layers
            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
            // Dispose the entities
            e1.dispose();
            e2.dispose();
            throw e;
        }
    }

    private void sendTo(long destId, CfdpPdu pdu) throws IOException {
        int port;
        if(destId == 1) {
            port = 23001;
        } else {
            port = 23002;
        }
        Socket sock = new Socket("127.0.0.1", port);
        sock.getOutputStream().write(pdu.getPdu());
        sock.close();
    }

    private MetadataPdu prepareMetadataPdu(long trId, boolean abandon) {
        MetadataPduBuilder b = new MetadataPduBuilder();
        b.setAcknowledged(true);
        b.setCrcPresent(true);
        b.setDestinationEntityId(2);
        b.setSourceEntityId(1);
        b.setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER);
        b.setSegmentationControlPreserved(false);
        // Set the length for the entity ID
        long maxEntityId = 1;
        b.setEntityIdLength(BytesUtil.getEncodingOctetsNb(maxEntityId));
        // Set the transaction ID
        b.setTransactionSequenceNumber(trId, BytesUtil.getEncodingOctetsNb(trId));
        b.setLargeFile(true);
        // Metadata specific
        b.setSegmentationControlPreserved(false); // Always 0 for file directive PDUs
        b.setClosureRequested(false);
        b.setChecksumType((byte) 3); // Not supported
        // File data
        b.setSourceFileName("sourcefile");
        b.setDestinationFileName("destfile");
        b.setFileSize(1234);

        // Segment metadata not present
        b.setSegmentMetadataPresent(false); // Always 0 for file directive PDUs

        // Add the declared options
        b.addOption(new FaultHandlerOverrideTLV(ConditionCode.CC_UNSUPPORTED_CHECKSUM_TYPE, abandon ? FaultHandlerOverrideTLV.HandlerCode.ABANDON_TRANSACTION : FaultHandlerOverrideTLV.HandlerCode.IGNORE_ERROR));
        return b.build();
    }
}
