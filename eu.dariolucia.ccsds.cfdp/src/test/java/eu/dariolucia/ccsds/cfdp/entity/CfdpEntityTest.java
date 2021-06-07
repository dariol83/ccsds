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

import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

// TODO: add additional checks on indications
// TODO: add a way to check the exchanged PDUs
// TODO: add a way to synch on the completion of transactions, instead of hardcoding 10 seconds sleep
public class CfdpEntityTest {

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
        // Enable reachability of the two entities
        ((AbstractUtLayer) e1.getUtLayerByName("TCP")).setRxAvailability(true, 2);
        ((AbstractUtLayer) e2.getUtLayerByName("TCP")).setRxAvailability(true, 1);
        ((AbstractUtLayer) e1.getUtLayerByName("TCP")).setTxAvailability(true, 2);
        ((AbstractUtLayer) e2.getUtLayerByName("TCP")).setTxAvailability(true, 1);
        // Create file in filestore
        String path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.bin", 10); // 10 KB
        String destPath = "recv_testfile_ack.bin";
        PutRequest fduTxReq = PutRequest.build(2, path, destPath, false, null);
        e1.request(fduTxReq);

        Thread.sleep(10000);

        assertTrue(e2.getFilestore().fileExists(destPath));
        assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));

        ((AbstractUtLayer) e1.getUtLayerByName("TCP")).deactivate();
        ((AbstractUtLayer) e2.getUtLayerByName("TCP")).deactivate();

        e1.dispose();
        e2.dispose();
    }

    @Test
    public void testUnacknowledgedTransactionWithClosure() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001);
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);
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

        Thread.sleep(10000);

        assertTrue(e2.getFilestore().fileExists(destPath));
        assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));

        ((AbstractUtLayer) e1.getUtLayerByName("TCP")).deactivate();
        ((AbstractUtLayer) e2.getUtLayerByName("TCP")).deactivate();

        e1.dispose();
        e2.dispose();
    }

    @Test
    public void testUnacknowledgedTransactionWithoutClosure() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001);
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);
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

        Thread.sleep(10000);

        assertTrue(e2.getFilestore().fileExists(destPath));
        assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));

        ((AbstractUtLayer) e1.getUtLayerByName("TCP")).deactivate();
        ((AbstractUtLayer) e2.getUtLayerByName("TCP")).deactivate();

        e1.dispose();
        e2.dispose();
    }
}
