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
import eu.dariolucia.ccsds.cfdp.entity.request.ReportRequest;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.FileSegment;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpFileSegmenter;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.ICfdpSegmentationStrategy;
import eu.dariolucia.ccsds.cfdp.entity.segmenters.impl.FixedSizeSegmenter;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfdpEntityNominalTcpTest {

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.ALL);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
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

            assertEquals(1, e1.getTransactionIds().size());
            assertEquals(1, e2.getTransactionIds().size());

            // Deactivate the UT layers
            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
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

            // Assert TX PDUs: sender
            UtLayerTxPduDecorator l1 = (UtLayerTxPduDecorator) e1.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu1 = l1.getTxPdus();
            assertEquals(13, txPdu1.size());
            // First: metadata + 10 file data + EOF + Finished ACK
            assertEquals(MetadataPdu.class, txPdu1.get(0).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(1).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(2).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(3).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(4).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(5).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(6).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(7).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(8).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(9).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(10).getClass());
            assertEquals(EndOfFilePdu.class, txPdu1.get(11).getClass());
            assertEquals(FileDirectivePdu.CC_NOERROR, ((EndOfFilePdu) txPdu1.get(11)).getConditionCode());
            assertEquals(AckPdu.class, txPdu1.get(12).getClass());
            assertEquals(FileDirectivePdu.DC_FINISHED_PDU, ((AckPdu) txPdu1.get(12)).getDirectiveCode());

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(2, txPdu2.size());
            // First: EOF ACK + Finished
            assertEquals(AckPdu.class, txPdu2.get(0).getClass());
            assertEquals(FileDirectivePdu.DC_EOF_PDU, ((AckPdu) txPdu2.get(0)).getDirectiveCode());
            assertEquals(FinishedPdu.class, txPdu2.get(1).getClass());
            assertEquals(FinishedPdu.FileStatus.RETAINED_IN_FILESTORE, ((FinishedPdu) txPdu2.get(1)).getFileStatus());
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
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 1);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 1);
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

            // Disable reachability of the two entities
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(false, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(false, 1);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(false, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(false, 1);

            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();

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

            // Assert TX PDUs: sender
            UtLayerTxPduDecorator l1 = (UtLayerTxPduDecorator) e1.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu1 = l1.getTxPdus();
            assertEquals(12, txPdu1.size());
            // First: metadata + 10 file data + EOF + Finished ACK
            assertEquals(MetadataPdu.class, txPdu1.get(0).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(1).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(2).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(3).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(4).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(5).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(6).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(7).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(8).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(9).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(10).getClass());
            assertEquals(EndOfFilePdu.class, txPdu1.get(11).getClass());
            assertEquals(FileDirectivePdu.CC_NOERROR, ((EndOfFilePdu) txPdu1.get(11)).getConditionCode());

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(1, txPdu2.size());
            // First: Finished
            assertEquals(FinishedPdu.class, txPdu2.get(0).getClass());
            assertEquals(FinishedPdu.FileStatus.RETAINED_IN_FILESTORE, ((FinishedPdu) txPdu2.get(0)).getFileStatus());
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
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 1);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 1);
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

            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();

            // Assert TX PDUs: sender
            UtLayerTxPduDecorator l1 = (UtLayerTxPduDecorator) e1.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu1 = l1.getTxPdus();
            assertEquals(12, txPdu1.size());
            // First: metadata + 10 file data + EOF + Finished ACK
            assertEquals(MetadataPdu.class, txPdu1.get(0).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(1).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(2).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(3).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(4).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(5).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(6).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(7).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(8).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(9).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(10).getClass());
            assertEquals(EndOfFilePdu.class, txPdu1.get(11).getClass());
            assertEquals(FileDirectivePdu.CC_NOERROR, ((EndOfFilePdu) txPdu1.get(11)).getConditionCode());

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(0, txPdu2.size());

            // Before disposing the entities, ask for a report
            e1.request(new ReportRequest(65537));
            s1.waitForIndication(ReportIndication.class, 1000);

            e1.dispose();
            e2.dispose();
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


    @Test
    public void testAcknowledgedTransactionSegmentationControl() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001);
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);
        // All files ending with .db are segmented by sending 100 bytes
        ICfdpSegmentationStrategy segmentationStrategy = new ICfdpSegmentationStrategy() {
            @Override
            public boolean support(Mib mib, IVirtualFilestore filestore, String fullPath) {
                return fullPath.endsWith(".db");
            }

            @Override
            public ICfdpFileSegmenter newSegmenter(Mib mib, IVirtualFilestore filestore, String fullPath, long destinationEntityId) throws FilestoreException {
                return new FixedSizeSegmenter(filestore, fullPath,  200);
            }
        };
        // This one raises an exception
        ICfdpSegmentationStrategy faultStrategy = new ICfdpSegmentationStrategy() {
            @Override
            public boolean support(Mib mib, IVirtualFilestore filestore, String fullPath) {
                throw new RuntimeException("Fault");
            }

            @Override
            public ICfdpFileSegmenter newSegmenter(Mib mib, IVirtualFilestore filestore, String fullPath, long destinationEntityId) throws FilestoreException {
                throw new RuntimeException("Fault");
            }
        };
        try {
            // Subscription to the entities
            EntityIndicationSubscriber s1 = new EntityIndicationSubscriber();
            e1.register(s1);
            EntityIndicationSubscriber s2 = new EntityIndicationSubscriber();
            e2.register(s2);
            ICfdpEntitySubscriber faultySubscriber = (emitter, indication) -> {
                throw new RuntimeException("Faulty subscriber");
            };
            e1.register(faultySubscriber);
            e2.register(faultySubscriber);

            e1.addSegmentationStrategy(faultStrategy);
            e1.addSegmentationStrategy(segmentationStrategy);

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

            // Assert indications: receiver
            s2.print();
            MetadataRecvIndication metaInd = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024L * 10, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
            FileSegmentRecvIndication fileRecInd = s2.assertPresentAt(1, FileSegmentRecvIndication.class);
            assertEquals(1024L, fileRecInd.getLength());

            s1.clear();
            s2.clear();

            // Create file in filestore
            path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.db", 1); // 10 KB
            destPath = "recv_testfile_ack.db";
            // Create request and start transaction
            fduTxReq = PutRequest.build(2, path, destPath, true, new byte[] { 0, 0, 0, 0 });
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the file was transferred and it has exactly the same contents of the source file
            assertTrue(e2.getFilestore().fileExists(destPath));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));

            e1.deregister(faultySubscriber);
            e2.deregister(faultySubscriber);

            // Deactivate the UT layers
            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
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
            metaInd = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024L, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
            fileRecInd = s2.assertPresentAt(1, FileSegmentRecvIndication.class);
            assertEquals(200L, fileRecInd.getLength());
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


    @Test
    public void testAcknowledgedTransactionSegmentationControl2() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001);
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);
        // All files ending with .db are segmented by sending 100 bytes
        ICfdpSegmentationStrategy segmentationStrategy = new ICfdpSegmentationStrategy() {
            @Override
            public boolean support(Mib mib, IVirtualFilestore filestore, String fullPath) {
                return fullPath.endsWith(".db");
            }

            @Override
            public ICfdpFileSegmenter newSegmenter(Mib mib, IVirtualFilestore filestore, String fullPath, long destinationEntityId) throws FilestoreException {
                return new ICfdpFileSegmenter() {

                    private FixedSizeSegmenter inner = new FixedSizeSegmenter(filestore, fullPath,  200);

                    @Override
                    public FileSegment nextSegment() throws FilestoreException {
                        FileSegment fs = inner.nextSegment();
                        if(fs.isEof()) {
                            return fs;
                        } else {
                            return FileSegment.segment(fs.getOffset(), fs.getData(), new byte[4], FileDataPdu.RCS_START_END);
                        }
                    }

                    @Override
                    public void close() {
                        inner.close();
                    }
                };
            }
        };
        // This one raises an exception
        ICfdpSegmentationStrategy faultStrategy = new ICfdpSegmentationStrategy() {
            @Override
            public boolean support(Mib mib, IVirtualFilestore filestore, String fullPath) {
                throw new RuntimeException("Fault");
            }

            @Override
            public ICfdpFileSegmenter newSegmenter(Mib mib, IVirtualFilestore filestore, String fullPath, long destinationEntityId) throws FilestoreException {
                throw new RuntimeException("Fault");
            }
        };
        try {
            // Subscription to the entities
            EntityIndicationSubscriber s1 = new EntityIndicationSubscriber();
            e1.register(s1);
            EntityIndicationSubscriber s2 = new EntityIndicationSubscriber();
            e2.register(s2);
            ICfdpEntitySubscriber faultySubscriber = (emitter, indication) -> {
                throw new RuntimeException("Faulty subscriber");
            };
            e1.register(faultySubscriber);
            e2.register(faultySubscriber);

            e1.addSegmentationStrategy(faultStrategy);
            e1.addSegmentationStrategy(segmentationStrategy);

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

            // Assert indications: receiver
            s2.print();
            MetadataRecvIndication metaInd = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024L * 10, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
            FileSegmentRecvIndication fileRecInd = s2.assertPresentAt(1, FileSegmentRecvIndication.class);
            assertEquals(1024L, fileRecInd.getLength());

            s1.clear();
            s2.clear();

            // Create file in filestore
            path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.db", 1); // 10 KB
            destPath = "recv_testfile_ack.db";
            // Create request and start transaction
            fduTxReq = PutRequest.build(2, path, destPath, true, new byte[] { 0, 0, 0, 0 });
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the file was transferred and it has exactly the same contents of the source file
            assertTrue(e2.getFilestore().fileExists(destPath));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));

            e1.deregister(faultySubscriber);
            e2.deregister(faultySubscriber);

            // Deactivate the UT layers
            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
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
            metaInd = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024L, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
            fileRecInd = s2.assertPresentAt(1, FileSegmentRecvIndication.class);
            assertEquals(200L, fileRecInd.getLength());
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

    @Test
    public void testAcknowledgedTransactionNullChecksum() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1_checksum_null.xml", 23001);
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2_checksum_null.xml", 23002);
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

            assertEquals(1, e1.getTransactionIds().size());
            assertEquals(1, e2.getTransactionIds().size());

            // Deactivate the UT layers
            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
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

            // Assert TX PDUs: sender
            UtLayerTxPduDecorator l1 = (UtLayerTxPduDecorator) e1.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu1 = l1.getTxPdus();
            assertEquals(13, txPdu1.size());
            // First: metadata + 10 file data + EOF + Finished ACK
            assertEquals(MetadataPdu.class, txPdu1.get(0).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(1).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(2).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(3).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(4).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(5).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(6).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(7).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(8).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(9).getClass());
            assertEquals(FileDataPdu.class, txPdu1.get(10).getClass());
            assertEquals(EndOfFilePdu.class, txPdu1.get(11).getClass());
            assertEquals(FileDirectivePdu.CC_NOERROR, ((EndOfFilePdu) txPdu1.get(11)).getConditionCode());
            assertEquals(AckPdu.class, txPdu1.get(12).getClass());
            assertEquals(FileDirectivePdu.DC_FINISHED_PDU, ((AckPdu) txPdu1.get(12)).getDirectiveCode());

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(2, txPdu2.size());
            // First: EOF ACK + Finished
            assertEquals(AckPdu.class, txPdu2.get(0).getClass());
            assertEquals(FileDirectivePdu.DC_EOF_PDU, ((AckPdu) txPdu2.get(0)).getDirectiveCode());
            assertEquals(FinishedPdu.class, txPdu2.get(1).getClass());
            assertEquals(FinishedPdu.FileStatus.RETAINED_IN_FILESTORE, ((FinishedPdu) txPdu2.get(1)).getFileStatus());
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
}
