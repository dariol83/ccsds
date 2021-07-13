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
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.builder.EndOfFilePduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.builder.FinishedPduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfdpEntityNoTransactionTest {

    private static final Logger LOG = Logger.getLogger(CfdpEntityNoTransactionTest.class.getName());

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.ALL);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testPduToUnknownDisposedTransactions() throws Exception {
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

            // Send a EOF PDU to e2 from entity 1
            {
                LOG.info("Send a EOF PDU to e2 from entity 1");
                EndOfFilePdu pdu = prepareEndOfFilePdu(1, 2, 65537);
                sendTo(2, pdu);
                Thread.sleep(2000);
            }
            // Send a EOF PDU to e1 from entity 3 and destination 2
            {
                LOG.info("Send a EOF PDU to e1 from entity 1");
                EndOfFilePdu pdu = prepareEndOfFilePdu(3, 2, 1234);
                sendTo(1, pdu);
                Thread.sleep(2000);
            }

            // Send a Finished PDU to e1 from entity 3
            {
                LOG.info("Send a Finished PDU to e1 from entity 3");
                FinishedPdu pdu = prepareFinishedPdu(3, 1);
                sendTo(1, pdu);
                Thread.sleep(2000);
            }
            // Send a Finished PDU to e1 from entity 2
            {
                LOG.info("Send a Finished PDU to e1 from entity 2");
                FinishedPdu pdu = prepareFinishedPdu(2, 1);
                sendTo(1, pdu);
                Thread.sleep(2000);
            }
            // Send a Finished PDU to e2 from entity 1
            {
                LOG.info("Send a Finished PDU to e2 from entity 1");
                FinishedPdu pdu = prepareFinishedPdu(1, 2);
                sendTo(2, pdu);
                Thread.sleep(2000);
            }
            // Send a corrupted Finished PDU to e2 from entity 1
            {
                LOG.info("Send a corrupted Finished PDU to e2 from entity 1");
                FinishedPdu pdu = prepareFinishedPdu(1, 2);
                byte[] data = pdu.getPdu();
                data[data.length - 1] = 0;
                sendTo(2, pdu);
                Thread.sleep(2000);
            }
            // Send a Finished PDU to e2 from entity 7
            {
                LOG.info("Send a Finished PDU to e2 from entity 7");
                FinishedPdu pdu = prepareFinishedPdu(2, 7);
                sendTo(2, pdu);
                Thread.sleep(2000);
            }
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

    private EndOfFilePdu prepareEndOfFilePdu(long sourceId, long destinationId, long transactionId) {
        EndOfFilePduBuilder b = new EndOfFilePduBuilder();
        b.setAcknowledged(true);
        b.setCrcPresent(true);
        b.setDestinationEntityId(destinationId);
        b.setSourceEntityId(sourceId);
        b.setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER);
        b.setSegmentationControlPreserved(false);
        // Set the length for the entity ID
        b.setEntityIdLength(BytesUtil.getEncodingOctetsNb(1));
        // Set the transaction ID
        b.setTransactionSequenceNumber(transactionId, 3);
        b.setLargeFile(false);
        // EOF specific
        b.setFileChecksum(12345679);
        b.setFileSize(4321);
        b.setConditionCode(FileDirectivePdu.CC_NOERROR, null);

        return b.build();
    }

    private FinishedPdu prepareFinishedPdu(long sourceId, long destinationId) {
        FinishedPduBuilder b = new FinishedPduBuilder();
        b.setAcknowledged(true);
        b.setCrcPresent(true);
        b.setDestinationEntityId(destinationId);
        b.setSourceEntityId(sourceId);
        b.setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER);
        b.setSegmentationControlPreserved(false);
        // Set the length for the entity ID
        b.setEntityIdLength(BytesUtil.getEncodingOctetsNb(1));
        // Set the transaction ID
        b.setTransactionSequenceNumber(1234, 3);
        b.setLargeFile(false);
        // EOF specific
        b.setConditionCode(FileDirectivePdu.CC_NOERROR, null);
        b.setDataComplete(true);
        b.setFileStatus(FinishedPdu.FileStatus.STATUS_UNREPORTED);
        return b.build();
    }
}
