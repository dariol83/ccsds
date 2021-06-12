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

import eu.dariolucia.ccsds.cfdp.entity.indication.*;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TODO: add a way to check the exchanged PDUs
public class CfdpEntityBasicTcpTest {

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testAcknowledgedTransaction() throws Exception {
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
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).setRxAvailability(true, 2);
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).setRxAvailability(true, 1);
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).setTxAvailability(true, 2);
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).setTxAvailability(true, 1);
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
            // Deactivate the UT layers
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).deactivate();
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).deactivate();
            // Dispose the entities
            e1.dispose();
            e2.dispose();

            // Wait for the entity disposition
            s1.waitForIndication(EntityDisposedIndication.class, 1000);
            s2.waitForIndication(EntityDisposedIndication.class, 1000);

            // Assert indications: sender
            s1.print();
            s1.assertPresentAt(0, TransactionIndication.class);
            s1.assertPresentAt(1, EofSentIndication.class);
            s1.assertPresentAt(2, TransactionFinishedIndication.class);
            TransactionDisposedIndication dispInd = s1.assertPresentAt(3, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.COMPLETED, dispInd.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(4, EntityDisposedIndication.class);

            // Assert indications: receiver
            s2.print();
            MetadataRecvIndication metaInd = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024 * 10, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
            s2.assertPresentAt(1, FileSegmentRecvIndication.class);
            s2.assertPresentAt(2, FileSegmentRecvIndication.class);
            s2.assertPresentAt(3, FileSegmentRecvIndication.class);
            s2.assertPresentAt(4, FileSegmentRecvIndication.class);
            s2.assertPresentAt(5, FileSegmentRecvIndication.class);
            s2.assertPresentAt(6, FileSegmentRecvIndication.class);
            s2.assertPresentAt(7, FileSegmentRecvIndication.class);
            s2.assertPresentAt(8, FileSegmentRecvIndication.class);
            s2.assertPresentAt(9, FileSegmentRecvIndication.class);
            s2.assertPresentAt(10, FileSegmentRecvIndication.class);
            s2.assertPresentAt(11, EofRecvIndication.class);
            s2.assertPresentAt(12, TransactionFinishedIndication.class);
            s2.assertPresentAt(13, TransactionDisposedIndication.class);
            s2.assertPresentAt(14, EntityDisposedIndication.class);
        } catch (Throwable e) {
            // Deactivate the UT layers
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).deactivate();
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).deactivate();
            // Dispose the entities
            e1.dispose();
            e2.dispose();
            throw e;
        }
    }

    @Test
    public void testUnacknowledgedTransactionWithClosure() throws Exception {
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
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).setRxAvailability(true, 2);
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).setRxAvailability(true, 1);
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).setTxAvailability(true, 2);
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).setTxAvailability(true, 1);
            // Create file in filestore
            String path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.bin", 10); // 10 KB
            String destPath = "recv_testfile_ack.bin";
            PutRequest fduTxReq = new PutRequest(2, path, destPath, false, null, false, true, null, null, null);
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);

            assertTrue(e2.getFilestore().fileExists(destPath));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));

            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).deactivate();
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).deactivate();

            e1.dispose();
            e2.dispose();

            // Wait for the entity disposition
            s1.waitForIndication(EntityDisposedIndication.class, 1000);
            s2.waitForIndication(EntityDisposedIndication.class, 1000);

            // Assert indications: sender
            s1.print();
            s1.assertPresentAt(0, TransactionIndication.class);
            s1.assertPresentAt(1, EofSentIndication.class);
            s1.assertPresentAt(2, TransactionFinishedIndication.class);
            s1.assertPresentAt(3, TransactionDisposedIndication.class);
            s1.assertPresentAt(4, EntityDisposedIndication.class);

            // Assert indications: receiver
            s2.print();
            MetadataRecvIndication metaInd = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024 * 10, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
            s2.assertPresentAt(1, FileSegmentRecvIndication.class);
            s2.assertPresentAt(2, FileSegmentRecvIndication.class);
            s2.assertPresentAt(3, FileSegmentRecvIndication.class);
            s2.assertPresentAt(4, FileSegmentRecvIndication.class);
            s2.assertPresentAt(5, FileSegmentRecvIndication.class);
            s2.assertPresentAt(6, FileSegmentRecvIndication.class);
            s2.assertPresentAt(7, FileSegmentRecvIndication.class);
            s2.assertPresentAt(8, FileSegmentRecvIndication.class);
            s2.assertPresentAt(9, FileSegmentRecvIndication.class);
            s2.assertPresentAt(10, FileSegmentRecvIndication.class);
            s2.assertPresentAt(11, EofRecvIndication.class);
            s2.assertPresentAt(12, TransactionFinishedIndication.class);
            s2.assertPresentAt(13, TransactionDisposedIndication.class);
            s2.assertPresentAt(14, EntityDisposedIndication.class);
        } catch (Throwable e) {
            // Deactivate the UT layers
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).deactivate();
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).deactivate();
            // Dispose the entities
            e1.dispose();
            e2.dispose();
            throw e;
        }
    }

    @Test
    public void testUnacknowledgedTransactionWithoutClosure() throws Exception {
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
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).setRxAvailability(true, 2);
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).setRxAvailability(true, 1);
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).setTxAvailability(true, 2);
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).setTxAvailability(true, 1);
            // Create file in filestore
            String path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.bin", 10); // 10 KB
            String destPath = "recv_testfile_ack.bin";
            PutRequest fduTxReq = new PutRequest(2, path, destPath, false, null, false, false, null, null, null);
            e1.request(fduTxReq);

            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);

            assertTrue(e2.getFilestore().fileExists(destPath));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));

            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).deactivate();
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).deactivate();

            e1.dispose();
            e2.dispose();
        } catch (Throwable e) {
            // Deactivate the UT layers
            ((AbstractUtLayer) e1.getUtLayerByName("TCP")).deactivate();
            ((AbstractUtLayer) e2.getUtLayerByName("TCP")).deactivate();
            // Dispose the entities
            e1.dispose();
            e2.dispose();
            throw e;
        }
    }
}
