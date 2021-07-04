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
import eu.dariolucia.ccsds.cfdp.entity.request.*;
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

public class CfdpEntityReportPromptTcpTest {

    private static final Logger LOG = Logger.getLogger(CfdpEntityReportPromptTcpTest.class.getName());

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.OFF);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testAcknowledgedTransactionReportPrompt() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1_suspend.xml", 23001, UtLayerTxPduDecorator.rule("Slow down sending", new Function<>() {
            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    fail("Interrupt not expected");
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
            // With the slow down it will take ca 26 seconds to complete the transaction
            // Wait to transmit 1-2 PDUs and then request a report
            Thread.sleep(4000);
            e1.request(new ReportRequest(65537));
            e2.request(new ReportRequest(65537));
            Thread.sleep(3000);
            // Request the sending of a keep alive
            e1.request(new KeepAliveRequest(65537));
            e2.request(new KeepAliveRequest(65537)); // No effect
            Thread.sleep(3000);
            // Request the sending of a NAK prompt
            e1.request(new PromptNakRequest(65537));
            e2.request(new PromptNakRequest(65537)); // No effect
            Thread.sleep(3000);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 20000);
            s2.waitForIndication(TransactionDisposedIndication.class, 20000);
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
            s1.assertPresentAt(1, ReportIndication.class);
            s1.assertPresentAt(2, EofSentIndication.class);
            s1.assertPresentAt(3, TransactionFinishedIndication.class);
            TransactionDisposedIndication dispInd = s1.assertPresentAt(4, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.COMPLETED, dispInd.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(5, EntityDisposedIndication.class);

            // Assert indications: receiver
            s2.print();
            s2.assertPresentAt(0, MetadataRecvIndication.class);
            int pos = s2.assertPresentAfter(1, ReportIndication.class);
            pos = s2.assertPresentAfter(pos + 1, EofRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, TransactionFinishedIndication.class);
            pos = s2.assertPresentAfter(pos + 1, TransactionDisposedIndication.class);
            s2.assertPresentAfter(pos + 1, EntityDisposedIndication.class);
            // 10 indications for file segment reception
            pos = s2.assertPresentAfter(0, FileSegmentRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, FileSegmentRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, FileSegmentRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, FileSegmentRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, FileSegmentRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, FileSegmentRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, FileSegmentRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, FileSegmentRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, FileSegmentRecvIndication.class);
            pos = s2.assertPresentAfter(pos + 1, FileSegmentRecvIndication.class);

            // Assert TX PDUs: sender
            UtLayerTxPduDecorator l1 = (UtLayerTxPduDecorator) e1.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu1 = l1.getTxPdus();
            assertEquals(15, txPdu1.size());
            // First: metadata + 10 file data with 2 prompt PDUs in between + EOF + Finished ACK
            int nakPduCount = 0;
            int keepAlivePduCount = 0;
            for(CfdpPdu pdu : txPdu1) {
                if(pdu instanceof PromptPdu) {
                    if(((PromptPdu) pdu).isNakResponseRequired()) {
                        nakPduCount++;
                    }
                    if(((PromptPdu) pdu).isKeepAliveResponseRequired()) {
                        keepAlivePduCount++;
                    }
                }
            }
            assertEquals(1, nakPduCount);
            assertEquals(1, keepAlivePduCount);

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(3, txPdu2.size());
            // First: KeepAlive PDU + EOF ACK + Finished (no NAKs)
            assertEquals(KeepAlivePdu.class, txPdu2.get(0).getClass());
            assertEquals(AckPdu.class, txPdu2.get(1).getClass());
            assertEquals(FileDirectivePdu.DC_EOF_PDU, ((AckPdu) txPdu2.get(1)).getDirectiveCode());
            assertEquals(FinishedPdu.class, txPdu2.get(2).getClass());
            assertEquals(FinishedPdu.FileStatus.RETAINED_IN_FILESTORE, ((FinishedPdu) txPdu2.get(2)).getFileStatus());
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
