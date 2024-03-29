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
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.ActionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FilestoreRequestTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FilestoreResponseTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.MessageToUserTLV;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class CfdpEntityFileOperationsTcpTest {

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.ALL);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testAcknowledgedFileOperationsTransactionWitError() throws Exception {
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
            TestUtils.createRandomFileIn(e2.getFilestore(), "test4.file", 10); // 10 KB
            String destPath = "recv_testfile_ack.bin";
            // Create request and start transaction
            PutRequest fduTxReq = new PutRequest(2, path, destPath, false, new byte[]{1,2,3},
                    true, false,
                    Arrays.asList(new MessageToUserTLV("CFDP test".getBytes(StandardCharsets.US_ASCII))),
                    Map.of(ConditionCode.CC_FILE_CHECKSUM_FAILURE, FaultHandlerStrategy.Action.NO_ACTION),
                    Arrays.asList(
                            new FilestoreRequestTLV(ActionCode.CREATE_DIRECTORY, "moved", null),
                            new FilestoreRequestTLV(ActionCode.CREATE_DIRECTORY, "moved2", null),
                            new FilestoreRequestTLV(ActionCode.CREATE, "moved/moved.file", null),
                            new FilestoreRequestTLV(ActionCode.REPLACE, "moved/moved.file", "recv_testfile_ack.bin"),
                            new FilestoreRequestTLV(ActionCode.DELETE, "does.not.exist", null),
                            new FilestoreRequestTLV(ActionCode.CREATE, "test.file", null),
                            new FilestoreRequestTLV(ActionCode.APPEND, "test.file", "moved/moved.file"),
                            new FilestoreRequestTLV(ActionCode.RENAME, "moved/moved.file", "moved/ren.file"),
                            new FilestoreRequestTLV(ActionCode.REMOVE_DIRECTORY, "moved2", null),
                            new FilestoreRequestTLV(ActionCode.DENY_DIRECTORY, "moved3", null),
                            new FilestoreRequestTLV(ActionCode.DENY_FILE, "test.file", null),
                            new FilestoreRequestTLV(ActionCode.DELETE, "test4.file", null)
                    ));
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);

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
            TransactionFinishedIndication trFin1 = s1.assertPresentAt(2, TransactionFinishedIndication.class);
            assertEquals(12, trFin1.getFilestoreResponses().size());

            assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, trFin1.getFilestoreResponses().get(0).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, trFin1.getFilestoreResponses().get(1).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, trFin1.getFilestoreResponses().get(2).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, trFin1.getFilestoreResponses().get(3).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.FILE_DOES_NOT_EXIST, trFin1.getFilestoreResponses().get(4).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.NOT_PERFORMED, trFin1.getFilestoreResponses().get(5).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.NOT_PERFORMED, trFin1.getFilestoreResponses().get(6).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.NOT_PERFORMED, trFin1.getFilestoreResponses().get(7).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.NOT_PERFORMED, trFin1.getFilestoreResponses().get(8).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.NOT_PERFORMED, trFin1.getFilestoreResponses().get(9).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.NOT_PERFORMED, trFin1.getFilestoreResponses().get(10).getStatusCode());
            assertEquals(FilestoreResponseTLV.StatusCode.NOT_PERFORMED, trFin1.getFilestoreResponses().get(11).getStatusCode());

            TransactionDisposedIndication dispInd = s1.assertPresentAt(3, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.COMPLETED, dispInd.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(4, EntityDisposedIndication.class);

            // Assert indications: receiver
            s2.print();
            MetadataRecvIndication metRcv2 = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1, metRcv2.getMessagesToUser().size());
            assertEquals("CFDP test", new String(metRcv2.getMessagesToUser().get(0).getData(), StandardCharsets.US_ASCII));
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
    public void testAcknowledgedFileOperationsTransaction() throws Exception {
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
            TestUtils.createRandomFileIn(e2.getFilestore(), "test4.file", 10); // 10 KB
            String destPath = "recv_testfile_ack.bin";
            // Create request and start transaction
            PutRequest fduTxReq = new PutRequest(2, path, destPath, false, new byte[]{1,2,3},
                    true, false,
                    Arrays.asList(new MessageToUserTLV("CFDP test".getBytes(StandardCharsets.US_ASCII))),
                    Map.of(ConditionCode.CC_FILE_CHECKSUM_FAILURE, FaultHandlerStrategy.Action.NO_ACTION),
                    Arrays.asList(
                        new FilestoreRequestTLV(ActionCode.CREATE_DIRECTORY, "moved", null),
                        new FilestoreRequestTLV(ActionCode.CREATE_DIRECTORY, "moved2", null),
                        new FilestoreRequestTLV(ActionCode.CREATE, "moved/moved.file", null),
                        new FilestoreRequestTLV(ActionCode.REPLACE, "moved/moved.file", "recv_testfile_ack.bin"),
                        new FilestoreRequestTLV(ActionCode.DELETE, "recv_testfile_ack.bin", null),
                        new FilestoreRequestTLV(ActionCode.CREATE, "test.file", null),
                        new FilestoreRequestTLV(ActionCode.APPEND, "test.file", "moved/moved.file"),
                        new FilestoreRequestTLV(ActionCode.RENAME, "moved/moved.file", "moved/ren.file"),
                        new FilestoreRequestTLV(ActionCode.REMOVE_DIRECTORY, "moved2", null),
                        new FilestoreRequestTLV(ActionCode.DENY_DIRECTORY, "moved3", null),
                        new FilestoreRequestTLV(ActionCode.DENY_FILE, "test.file", null),
                        new FilestoreRequestTLV(ActionCode.DELETE, "test4.file", null)
                    ));
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the file was transferred and it has exactly the same contents of the source file
            assertFalse(e2.getFilestore().fileExists(destPath));
            assertFalse(e2.getFilestore().fileExists("test4.file"));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), "moved/ren.file"));
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
            TransactionFinishedIndication trFin1 = s1.assertPresentAt(2, TransactionFinishedIndication.class);
            assertEquals(12, trFin1.getFilestoreResponses().size());
            for(FilestoreResponseTLV resp : trFin1.getFilestoreResponses()) {
                assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, resp.getStatusCode());
            }
            TransactionDisposedIndication dispInd = s1.assertPresentAt(3, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.COMPLETED, dispInd.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(4, EntityDisposedIndication.class);

            // Assert indications: receiver
            s2.print();
            MetadataRecvIndication metRcv2 = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1, metRcv2.getMessagesToUser().size());
            assertEquals("CFDP test", new String(metRcv2.getMessagesToUser().get(0).getData(), StandardCharsets.US_ASCII));
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
    public void testAcknowledgedFileOperationTransactionOnly() throws Exception {
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
            e2.getFilestore().createDirectory("test");
            assertTrue(e2.getFilestore().directoryExists("test"));
            // Create request and start transaction
            PutRequest fduTxReq = new PutRequest(2, null, null, false, new byte[]{1,2,3},
                    true, false,
                    Arrays.asList(new MessageToUserTLV("CFDP test".getBytes(StandardCharsets.US_ASCII))),
                    Map.of(ConditionCode.CC_FILE_CHECKSUM_FAILURE, FaultHandlerStrategy.Action.NO_ACTION),
                    Arrays.asList(
                            new FilestoreRequestTLV(ActionCode.REMOVE_DIRECTORY, "test", null)
                    ));
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the directory is removed
            assertFalse(e2.getFilestore().directoryExists("test"));
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
            TransactionFinishedIndication trFin1 = s1.assertPresentAt(1, TransactionFinishedIndication.class);
            assertEquals(1, trFin1.getFilestoreResponses().size());
            for(FilestoreResponseTLV resp : trFin1.getFilestoreResponses()) {
                assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, resp.getStatusCode());
            }
            TransactionDisposedIndication dispInd = s1.assertPresentAt(2, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.COMPLETED, dispInd.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(3, EntityDisposedIndication.class);

            // Assert indications: receiver
            s2.print();
            MetadataRecvIndication metRcv2 = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1, metRcv2.getMessagesToUser().size());
            assertEquals("CFDP test", new String(metRcv2.getMessagesToUser().get(0).getData(), StandardCharsets.US_ASCII));
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
    public void testUnacknowledgedWithClosureFileOperationTransactionOnly() throws Exception {
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
            e2.getFilestore().createDirectory("test");
            assertTrue(e2.getFilestore().directoryExists("test"));
            // Create request and start transaction
            PutRequest fduTxReq = new PutRequest(2, null, null, false, new byte[]{1,2,3},
                    false, true,
                    Arrays.asList(new MessageToUserTLV("CFDP test".getBytes(StandardCharsets.US_ASCII))),
                    Map.of(ConditionCode.CC_FILE_CHECKSUM_FAILURE, FaultHandlerStrategy.Action.NO_ACTION),
                    Arrays.asList(
                            new FilestoreRequestTLV(ActionCode.REMOVE_DIRECTORY, "test", null)
                    ));
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the directory is removed
            assertFalse(e2.getFilestore().directoryExists("test"));
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
            TransactionFinishedIndication trFin1 = s1.assertPresentAt(1, TransactionFinishedIndication.class);
            assertEquals(1, trFin1.getFilestoreResponses().size());
            for(FilestoreResponseTLV resp : trFin1.getFilestoreResponses()) {
                assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, resp.getStatusCode());
            }
            TransactionDisposedIndication dispInd = s1.assertPresentAt(2, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.COMPLETED, dispInd.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(3, EntityDisposedIndication.class);

            // Assert indications: receiver
            s2.print();
            MetadataRecvIndication metRcv2 = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1, metRcv2.getMessagesToUser().size());
            assertEquals("CFDP test", new String(metRcv2.getMessagesToUser().get(0).getData(), StandardCharsets.US_ASCII));
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
    public void testUnacknowledgedWithoutClosureFileOperationTransactionOnly() throws Exception {
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
            e2.getFilestore().createDirectory("test");
            assertTrue(e2.getFilestore().directoryExists("test"));
            // Create request and start transaction
            PutRequest fduTxReq = new PutRequest(2, null, null, false, new byte[]{1,2,3},
                    false, false,
                    Arrays.asList(new MessageToUserTLV("CFDP test".getBytes(StandardCharsets.US_ASCII))),
                    Map.of(ConditionCode.CC_FILE_CHECKSUM_FAILURE, FaultHandlerStrategy.Action.NO_ACTION),
                    Arrays.asList(
                            new FilestoreRequestTLV(ActionCode.REMOVE_DIRECTORY, "test", null)
                    ));
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the directory is removed
            assertFalse(e2.getFilestore().directoryExists("test"));
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
            TransactionFinishedIndication trFin1 = s1.assertPresentAt(1, TransactionFinishedIndication.class);
            assertEquals(0, trFin1.getFilestoreResponses().size()); // No Finished PDU here, so 0
            for(FilestoreResponseTLV resp : trFin1.getFilestoreResponses()) {
                assertEquals(FilestoreResponseTLV.StatusCode.SUCCESSFUL, resp.getStatusCode());
            }
            TransactionDisposedIndication dispInd = s1.assertPresentAt(2, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.COMPLETED, dispInd.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(3, EntityDisposedIndication.class);

            // Assert indications: receiver
            s2.print();
            MetadataRecvIndication metRcv2 = s2.assertPresentAt(0, MetadataRecvIndication.class);
            assertEquals(1, metRcv2.getMessagesToUser().size());
            assertEquals("CFDP test", new String(metRcv2.getMessagesToUser().get(0).getData(), StandardCharsets.US_ASCII));
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
