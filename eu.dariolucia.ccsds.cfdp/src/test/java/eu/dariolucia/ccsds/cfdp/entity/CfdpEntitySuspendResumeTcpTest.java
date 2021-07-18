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
import eu.dariolucia.ccsds.cfdp.entity.request.ResumeRequest;
import eu.dariolucia.ccsds.cfdp.entity.request.SuspendRequest;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduDecorator;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduSwapperDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CfdpEntitySuspendResumeTcpTest {

    private static final Logger LOG = Logger.getLogger(CfdpEntitySuspendResumeTcpTest.class.getName());

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.OFF);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testAcknowledgedTransactionSuspendResume() throws Exception {
        final Semaphore waitForSuspendSem = new Semaphore(0);
        final Semaphore suspendSem = new Semaphore(0);
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1_suspend.xml", 23001, UtLayerTxPduDecorator.rule("Slow down sending", new Function<>() {
            int count = 0;
            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    fail("Interrupt not expected");
                }
                if(cfdpPdu instanceof FileDataPdu) {
                    count++;
                }
                if(count == 3) {
                    LOG.log(Level.INFO, "Suspending sending thread now");
                    try {
                        waitForSuspendSem.release();
                        suspendSem.acquire();
                    } catch (InterruptedException e) {
                        fail("Interrupt not expected");
                    }
                    LOG.log(Level.INFO, "Resuming sending thread now");
                }
                return false;
            }
        }));
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2_suspend.xml", 23002);
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
            // Wait to transmit 3 PDUs and then suspend
            waitForSuspendSem.acquire();
            // At this stage, suspend the sending side (twice to check also that no problem is introduced)
            e1.request(new SuspendRequest(65537));
            e1.request(new SuspendRequest(65537));
            // Activate again the sending and wait a bit
            suspendSem.release();
            Thread.sleep(3000);
            e2.request(new SuspendRequest(65537));
            Thread.sleep(3000);
            // Verify that the receiving end got only 3 FileDataPdus and it is not receiving more
            s2.print();
            // Depending on the suspend/release timing between the two threads, you can have that the sending thread can
            // send 4 FileDataPdu or 3 FileDataPdu, before the suspend request kicks in
            LOG.info("s2 size: " + s2.getIndicationListSize());
            assertTrue(s2.getIndicationListSize() >= 5 && s2.getIndicationListSize() <= 6 );
            MetadataRecvIndication metaInd = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024 * 10, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
            s2.assertPresentAt(1, FileSegmentRecvIndication.class);
            s2.assertPresentAt(2, FileSegmentRecvIndication.class);
            s2.assertPresentAt(3, FileSegmentRecvIndication.class);
            s2.assertPresentAt(s2.getIndicationListSize() - 1, SuspendedIndication.class);
            // Verify that the sending side sent a SuspendIndication
            s1.print();
            s1.assertPresentAt(0, TransactionIndication.class);
            s1.assertPresentAt(1, SuspendedIndication.class);
            // Resume the transaction
            e2.request(new ResumeRequest(65537));
            Thread.sleep(2000);
            e1.request(new ResumeRequest(65537));
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the file was transferred and it has exactly the same contents of the source file
            assertTrue(e2.getFilestore().fileExists(destPath));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));
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
            s1.assertPresentAt(1, SuspendedIndication.class);
            s1.assertPresentAt(2, ResumedIndication.class);
            s1.assertPresentAt(3, EofSentIndication.class);
            s1.assertPresentAt(4, TransactionFinishedIndication.class);
            TransactionDisposedIndication dispInd = s1.assertPresentAt(5, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.COMPLETED, dispInd.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(6, EntityDisposedIndication.class);

            // Assert indications: receiver
            s2.print();
            metaInd = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024 * 10, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
            s2.assertPresentAt(1, FileSegmentRecvIndication.class);
            s2.assertPresentAt(2, FileSegmentRecvIndication.class);
            s2.assertPresentAt(3, FileSegmentRecvIndication.class);
            int pos = s2.assertPresentAfter(3, SuspendedIndication.class);
            s2.assertPresentAt(pos + 1, ResumedIndication.class);
            if(pos == 4) {
                s2.assertPresentAt(6, FileSegmentRecvIndication.class);
                s2.assertPresentAt(7, FileSegmentRecvIndication.class);
                s2.assertPresentAt(8, FileSegmentRecvIndication.class);
                s2.assertPresentAt(9, FileSegmentRecvIndication.class);
                s2.assertPresentAt(10, FileSegmentRecvIndication.class);
                s2.assertPresentAt(11, FileSegmentRecvIndication.class);
                s2.assertPresentAt(12, FileSegmentRecvIndication.class);
                s2.assertPresentAt(13, EofRecvIndication.class);
                s2.assertPresentAt(14, TransactionFinishedIndication.class);
                s2.assertPresentAt(15, TransactionDisposedIndication.class);
                s2.assertPresentAt(16, EntityDisposedIndication.class);
            } else { // pos == 5
                s2.assertPresentAt(4, FileSegmentRecvIndication.class);
                s2.assertPresentAt(7, FileSegmentRecvIndication.class);
                s2.assertPresentAt(8, FileSegmentRecvIndication.class);
                s2.assertPresentAt(9, FileSegmentRecvIndication.class);
                s2.assertPresentAt(10, FileSegmentRecvIndication.class);
                s2.assertPresentAt(11, FileSegmentRecvIndication.class);
                s2.assertPresentAt(12, FileSegmentRecvIndication.class);
                s2.assertPresentAt(13, EofRecvIndication.class);
                s2.assertPresentAt(14, TransactionFinishedIndication.class);
                s2.assertPresentAt(15, TransactionDisposedIndication.class);
                s2.assertPresentAt(16, EntityDisposedIndication.class);
            }
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
    public void testAcknowledgedTransactionFreeze() throws Exception {
        final Semaphore waitForSuspendSem = new Semaphore(0);
        final Semaphore suspendSem = new Semaphore(0);
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1_suspend.xml", 23001, UtLayerTxPduDecorator.rule("Slow down sending", new Function<>() {
            int count = 0;
            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    fail("Interrupt not expected");
                }
                if(cfdpPdu instanceof FileDataPdu) {
                    count++;
                }
                if(count == 3) {
                    LOG.log(Level.INFO, "Suspending sending thread now");
                    try {
                        waitForSuspendSem.release();
                        suspendSem.acquire();
                    } catch (InterruptedException e) {
                        fail("Interrupt not expected");
                    }
                    LOG.log(Level.INFO, "Resuming sending thread now");
                }
                return false;
            }
        }));
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2_suspend.xml", 23002);
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
            // Wait to transmit 3 PDUs and then suspend
            waitForSuspendSem.acquire();
            // At this stage, freeze the sending side
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(false, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(false, 2);
            // Activate again the sending and wait a bit
            suspendSem.release();
            Thread.sleep(4000);
            // Verify that the receiving end got only 3 FileDataPdus and it is not receiving more
            s2.print();
            // Depending on the suspend/release timing between the two threads, you can have that the sending thread can send from 2 to 4 FileDataPdu,
            // before the suspend request kicks in
            assertTrue(s2.getIndicationListSize() >= 3 && s2.getIndicationListSize() <= 5 );
            MetadataRecvIndication metaInd = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024 * 10, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
            s2.assertPresentAt(1, FileSegmentRecvIndication.class);
            s2.assertPresentAt(2, FileSegmentRecvIndication.class);
            // Verify that the sending side sent a SuspendIndication
            s1.print();
            assertEquals(1, s1.getIndicationListSize());
            s1.assertPresentAt(0, TransactionIndication.class);
            // Resume the transaction
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 2);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 15000);
            s2.waitForIndication(TransactionDisposedIndication.class, 15000);
            // Check that the file was transferred and it has exactly the same contents of the source file
            assertTrue(e2.getFilestore().fileExists(destPath));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));
            // Deactivate the UT layers
            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
            // Dispose the entities
            e1.dispose();
            e2.dispose();

            // Wait for the entity disposition
            s1.waitForIndication(EntityDisposedIndication.class, 5000);
            s2.waitForIndication(EntityDisposedIndication.class, 5000);

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
            assertEquals(14, txPdu1.size());
            // First: metadata + 11 file data (one sent two times) + EOF + Finished ACK
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
            assertEquals(FileDataPdu.class, txPdu1.get(11).getClass());
            assertEquals(EndOfFilePdu.class, txPdu1.get(12).getClass());
            assertEquals(FileDirectivePdu.CC_NOERROR, ((EndOfFilePdu) txPdu1.get(12)).getConditionCode());
            assertEquals(AckPdu.class, txPdu1.get(13).getClass());
            assertEquals(FileDirectivePdu.DC_FINISHED_PDU, ((AckPdu) txPdu1.get(13)).getDirectiveCode());

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
