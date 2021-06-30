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
import eu.dariolucia.ccsds.cfdp.entity.request.CancelRequest;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.entity.request.ResumeRequest;
import eu.dariolucia.ccsds.cfdp.entity.request.SuspendRequest;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class CfdpEntityCancelTcpTest {

    private static final Logger LOG = Logger.getLogger(CfdpEntityCancelTcpTest.class.getName());

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.OFF);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testAcknowledgedTransactionCancel() throws Exception {
        final Semaphore waitForSuspendSem = new Semaphore(0);
        final Semaphore suspendSem = new Semaphore(0);
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1_suspend.xml", 23001, UtLayerTxPduDecorator.rule("Slow down sending", new Function<>() {
            int count = 0;
            boolean skip = false;
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
                if(count == 3 && !skip && cfdpPdu instanceof FileDataPdu) {
                    skip = true;
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
            // At this stage, cancel the sending side
            e1.request(new CancelRequest(65537));
            // Activate again the sending and wait a bit
            suspendSem.release();
            Thread.sleep(2000);

            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the file was not transferred and it has exactly the same contents of the source file
            assertFalse(e2.getFilestore().fileExists(destPath));
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
            TransactionFinishedIndication traFin1 = s1.assertPresentAt(1, TransactionFinishedIndication.class);
            assertEquals(FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED, traFin1.getConditionCode());
            assertEquals(CfdpTransactionState.CANCELLED, traFin1.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(2, EofSentIndication.class);
            TransactionDisposedIndication dispInd = s1.assertPresentAt(3, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.CANCELLED, dispInd.getStatusReport().getCfdpTransactionState());
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
            int pos = s2.assertPresentAfter(3, TransactionFinishedIndication.class);
            TransactionFinishedIndication traFin2 = s2.assertPresentAt(pos, TransactionFinishedIndication.class);
            assertEquals(FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED, traFin2.getConditionCode());
            assertEquals(1L, traFin2.getStatusReport().getLastFaultEntity());
            assertEquals(CfdpTransactionState.CANCELLED, traFin1.getStatusReport().getCfdpTransactionState());

            // Assert TX PDUs: sender
            UtLayerTxPduDecorator l1 = (UtLayerTxPduDecorator) e1.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu1 = l1.getTxPdus();
            if(txPdu1.size() == 6) {
                assertEquals(6, txPdu1.size());
                // First: metadata + 4 file data + EOF
                assertEquals(MetadataPdu.class, txPdu1.get(0).getClass());
                assertEquals(FileDataPdu.class, txPdu1.get(1).getClass());
                assertEquals(FileDataPdu.class, txPdu1.get(2).getClass());
                assertEquals(FileDataPdu.class, txPdu1.get(3).getClass());
                assertEquals(FileDataPdu.class, txPdu1.get(4).getClass());
                assertEquals(EndOfFilePdu.class, txPdu1.get(5).getClass());
                assertEquals(FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED, ((EndOfFilePdu) txPdu1.get(5)).getConditionCode());
            } else { // 5
                assertEquals(5, txPdu1.size());
                // First: metadata + 3 file data + EOF
                assertEquals(MetadataPdu.class, txPdu1.get(0).getClass());
                assertEquals(FileDataPdu.class, txPdu1.get(1).getClass());
                assertEquals(FileDataPdu.class, txPdu1.get(2).getClass());
                assertEquals(FileDataPdu.class, txPdu1.get(3).getClass());
                assertEquals(EndOfFilePdu.class, txPdu1.get(4).getClass());
                assertEquals(FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED, ((EndOfFilePdu) txPdu1.get(4)).getConditionCode());
            }
            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(1, txPdu2.size());
            // First: EOF ACK + Finished
            assertEquals(AckPdu.class, txPdu2.get(0).getClass());
            assertEquals(FileDirectivePdu.DC_EOF_PDU, ((AckPdu) txPdu2.get(0)).getDirectiveCode());
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
