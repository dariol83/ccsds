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
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDataPdu;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CfdpEntityErrorTcpSuspendTest {

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.ALL);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testAcknowledgedTransactionSuspendOnFault() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1_unack.xml", 23001, UtLayerTxPduDecorator.rule("Drop 5th file data PDU", new Function<>() {
            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                return cfdpPdu instanceof FileDataPdu && ((FileDataPdu) cfdpPdu).getOffset() == 0;
            }
        }));
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2_unack.xml", 23002);
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
            PutRequest fduTxReq = new PutRequest(2, path, destPath, false, null,
                    true, false,
                    null, Map.of(ConditionCode.CC_INACTIVITY_DETECTED, FaultHandlerStrategy.Action.NOTICE_OF_SUSPENSION), null);
            e1.request(fduTxReq);
            // Wait for the transaction to be suspended on the two entities
            s2.waitForIndication(SuspendedIndication.class, 10000);
            // Check that the file was not transferred
            assertFalse(e2.getFilestore().fileExists(destPath));
            // Wait for further updates to arrive (e.g. TransactionFinished)
            Thread.sleep(2000);
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
            s2.assertPresentAt(10, EofRecvIndication.class);
            SuspendedIndication finInd = s2.assertPresentAt(11, SuspendedIndication.class);
            assertEquals(ConditionCode.CC_SUSPEND_REQUEST_RECEIVED, finInd.getConditionCode());
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
