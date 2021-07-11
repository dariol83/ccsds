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

import eu.dariolucia.ccsds.cfdp.entity.indication.EntityDisposedIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.TransactionDisposedIndication;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.KeepAlivePdu;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class CfdpKeepAliveSendingTest {

    private static final Logger LOG = Logger.getLogger(CfdpKeepAliveSendingTest.class.getName());

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.OFF);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testReceiverKeepAliveTimer() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1_keepalive.xml", 23001, UtLayerTxPduDecorator.rule("Slow down sending", new Function<>() {
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
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2_keepalive.xml", 23002);
        try {
            // Subscription to the entities
            EntityIndicationSubscriber s1 = new EntityIndicationSubscriber();
            e1.register(s1);
            EntityIndicationSubscriber s2 = new EntityIndicationSubscriber();
            e2.register(s2);
            // Enable reachability of the two entities
            ((AbstractUtLayer) ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 2);
            ((AbstractUtLayer) ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 1);
            ((AbstractUtLayer) ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 2);
            ((AbstractUtLayer) ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 1);
            // Create file in filestore
            String path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.bin", 5); // 5 KB
            String destPath = "recv_testfile_ack.bin";
            // Create request and start transaction
            PutRequest fduTxReq = PutRequest.build(2, path, destPath, false, null);
            e1.request(fduTxReq);

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
            s1.waitForIndication(EntityDisposedIndication.class, 2000);
            s2.waitForIndication(EntityDisposedIndication.class, 2000);

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            int keepAliveCount = 0;
            for (CfdpPdu p : txPdu2) {
                if (p instanceof KeepAlivePdu) {
                    ++keepAliveCount;
                }
            }
            assertTrue(keepAliveCount >= 5);
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
