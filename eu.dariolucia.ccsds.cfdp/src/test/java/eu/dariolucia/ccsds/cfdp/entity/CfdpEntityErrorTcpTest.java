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

import java.io.File;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfdpEntityErrorTcpTest {

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testAcknowledgedTransactionMissingFileSegment() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001, UtLayerTxPduDecorator.rule("Drop 5th file metadata PDU", new Function<>() {
            int filePduCount = 0;

            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                if (cfdpPdu instanceof FileDataPdu) {
                    ++filePduCount;
                    return filePduCount == 5;
                } else {
                    return false; // No discard
                }
            }
        }));
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
            s2.assertPresentAt(10, EofRecvIndication.class);
            s2.assertPresentAt(11, FileSegmentRecvIndication.class);
            s2.assertPresentAt(12, TransactionFinishedIndication.class);
            s2.assertPresentAt(13, TransactionDisposedIndication.class);
            s2.assertPresentAt(14, EntityDisposedIndication.class);

            // Assert TX PDUs: sender
            UtLayerTxPduDecorator l1 = (UtLayerTxPduDecorator) e1.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu1 = l1.getTxPdus();
            assertEquals(14, txPdu1.size());
            // First: metadata + 11 file data + EOF + Finished ACK
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
            assertEquals(FileDataPdu.class, txPdu1.get(12).getClass());
            assertEquals(AckPdu.class, txPdu1.get(13).getClass());
            assertEquals(FileDirectivePdu.DC_FINISHED_PDU, ((AckPdu) txPdu1.get(13)).getDirectiveCode());

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(3, txPdu2.size());
            // First: EOF ACK + Finished
            assertEquals(AckPdu.class, txPdu2.get(0).getClass());
            assertEquals(FileDirectivePdu.DC_EOF_PDU, ((AckPdu) txPdu2.get(0)).getDirectiveCode());
            assertEquals(NakPdu.class, txPdu2.get(1).getClass());
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

    @Test
    public void testAcknowledgedTransactionMissingMetadataFileSegment() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001, UtLayerTxPduDecorator.rule("Drop 5th file & metadata PDU", new Function<>() {
            int filePduCount = 0;
            boolean metadataDropped = false;

            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                if (cfdpPdu instanceof FileDataPdu) {
                    ++filePduCount;
                    return filePduCount == 5;
                } else if (cfdpPdu instanceof MetadataPdu) {
                    if (metadataDropped) {
                        return false;
                    } else {
                        metadataDropped = true;
                        return true;
                    }
                } else {
                    return false; // No discard
                }
            }
        }));
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

            // I cannot assert the TX PDUs on the sender side, because the order depends on the time the Metadata NAK PDU arrives,
            // and it can even arrive more than one (NAK generated upon reception of the first FileData PDU and NAK generated
            // upon reception of the EOF.

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertTrue(txPdu2.size() == 5 || txPdu2.size() == 4);
            assertEquals(NakPdu.class, txPdu2.get(0).getClass()); // Metadata request after the first file segment
            assertEquals(1, ((NakPdu) txPdu2.get(0)).getSegmentRequests().size()); // Metadata request after the first file segment
            assertEquals(0L, ((NakPdu) txPdu2.get(0)).getSegmentRequests().get(0).getStartOffset()); // Metadata request after the first file segment
            assertEquals(0L, ((NakPdu) txPdu2.get(0)).getSegmentRequests().get(0).getEndOffset()); // Metadata request after the first file segment
            assertEquals(AckPdu.class, txPdu2.get(1).getClass());
            assertEquals(FileDirectivePdu.DC_EOF_PDU, ((AckPdu) txPdu2.get(1)).getDirectiveCode());
            if(txPdu2.size() == 5) {
                // Metadata PDU not arrived before EOF
                assertEquals(NakPdu.class, txPdu2.get(2).getClass()); // Metadata not arrived yet
                assertEquals(NakPdu.class, txPdu2.get(3).getClass()); // File segment
                assertEquals(1024 * 4, ((NakPdu) txPdu2.get(3)).getSegmentRequests().get(0).getStartOffset()); // FileData Nak after reception of EOF
                assertEquals(1024 * 5, ((NakPdu) txPdu2.get(3)).getSegmentRequests().get(0).getEndOffset()); // FileData Nak after reception of EOF
                assertEquals(FinishedPdu.class, txPdu2.get(4).getClass());
                assertEquals(FinishedPdu.FileStatus.RETAINED_IN_FILESTORE, ((FinishedPdu) txPdu2.get(4)).getFileStatus());
            } else {
                // Metadata PDU arrived before EOF
                assertEquals(NakPdu.class, txPdu2.get(2).getClass()); // File segment
                assertEquals(1024 * 4, ((NakPdu) txPdu2.get(2)).getSegmentRequests().get(0).getStartOffset()); // FileData Nak after reception of EOF
                assertEquals(1024 * 5, ((NakPdu) txPdu2.get(2)).getSegmentRequests().get(0).getEndOffset()); // FileData Nak after reception of EOF
                assertEquals(FinishedPdu.class, txPdu2.get(3).getClass());
                assertEquals(FinishedPdu.FileStatus.RETAINED_IN_FILESTORE, ((FinishedPdu) txPdu2.get(3)).getFileStatus());
            }
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
    public void testAcknowledgedTransactionMissingAllUntilEOF() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001, UtLayerTxPduDecorator.rule("Drop all till EOF PDU", new Function<>() {
            boolean eofMet;

            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                if (cfdpPdu instanceof EndOfFilePdu) {
                    eofMet = true;
                }
                return !eofMet;
            }
        }));
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

            s2.print();
            s2.assertPresentAt(0, EofRecvIndication.class);

            // I cannot assert the TX PDUs on the sender side, because the order depends on the time the NAK PDUs arrive.

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(4, txPdu2.size());
            assertEquals(AckPdu.class, txPdu2.get(0).getClass()); // Ack of EOF
            assertEquals(NakPdu.class, txPdu2.get(1).getClass()); // Metadata NAK
            assertEquals(1, ((NakPdu) txPdu2.get(1)).getSegmentRequests().size());
            assertEquals(NakPdu.class, txPdu2.get(2).getClass()); // File NAK
            assertEquals(1, ((NakPdu) txPdu2.get(2)).getSegmentRequests().size());
            assertEquals(FinishedPdu.class, txPdu2.get(3).getClass()); // Finished PDU
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
    public void testAcknowledgedTransactionMissingEofAck() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001);
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002, UtLayerTxPduDecorator.rule("Drop first EOF Ack", new Function<>() {
            boolean firstAck;

            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                if (cfdpPdu instanceof AckPdu && !firstAck) {
                    firstAck = true;
                    return true;
                }
                return false;
            }
        }));
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

            s2.print();
            s2.assertPresentAt(0, MetadataRecvIndication.class);
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
            // First: metadata + 11 file data + EOF + Finished ACK
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
            assertEquals(EndOfFilePdu.class, txPdu1.get(12).getClass());
            assertEquals(FileDirectivePdu.CC_NOERROR, ((EndOfFilePdu) txPdu1.get(12)).getConditionCode());
            assertEquals(AckPdu.class, txPdu1.get(13).getClass());
            assertEquals(FileDirectivePdu.DC_FINISHED_PDU, ((AckPdu) txPdu1.get(13)).getDirectiveCode());

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(4, txPdu2.size());
            assertEquals(AckPdu.class, txPdu2.get(0).getClass()); // Ack of EOF
            assertEquals(FinishedPdu.class, txPdu2.get(1).getClass()); //
            assertEquals(AckPdu.class, txPdu2.get(2).getClass()); // Ack of EOF resent after receiving it again
            assertEquals(FinishedPdu.class, txPdu2.get(3).getClass()); // Finished resent after local timeout

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
    public void testAcknowledgedTransactionFileNotExisting() throws Exception {
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
            PutRequest fduTxReq = PutRequest.build(2, path + ".wrong", destPath, false, null);
            e1.request(fduTxReq);
            // Wait for the transaction to be abandoned and disposed on the first entity
            s1.waitForIndication(TransactionIndication.class, 10000);
            s1.waitForIndication(AbandonedIndication.class, 10000);
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);

            assertEquals(0, s2.getIndicationListSize());

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

    @Test
    public void testAcknowledgedTransactionMissingFinishedPdu() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001);
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002, UtLayerTxPduDecorator.rule("Drop Finished PDU", new Function<>() {
            boolean firstFinished;

            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                if (cfdpPdu instanceof FinishedPdu && !firstFinished) {
                    firstFinished = true;
                    return true;
                }
                return false;
            }
        }));
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

            s2.print();
            s2.assertPresentAt(0, MetadataRecvIndication.class);
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
            assertEquals(3, txPdu2.size());
            assertEquals(AckPdu.class, txPdu2.get(0).getClass()); // Ack of EOF
            assertEquals(FinishedPdu.class, txPdu2.get(1).getClass()); // First Finished PDU
            assertEquals(FinishedPdu.class, txPdu2.get(2).getClass()); // Second Finished PDU resent after local timeout

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
    public void testAcknowledgedTransactionMissingFileSegmentAndNak() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001, UtLayerTxPduDecorator.rule("Drop 5th file metadata PDU", new Function<>() {
            int filePduCount = 0;

            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                if (cfdpPdu instanceof FileDataPdu) {
                    ++filePduCount;
                    return filePduCount == 5;
                } else {
                    return false; // No discard
                }
            }
        }));
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002, UtLayerTxPduDecorator.rule("Drop 1st NAK", new Function<>() {
            boolean nakOut = false;

            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                if (cfdpPdu instanceof NakPdu && !nakOut) {
                    nakOut = true;
                    return true;
                }
                return false;
            }
        }));
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
            s2.assertPresentAt(10, EofRecvIndication.class);
            s2.assertPresentAt(11, FileSegmentRecvIndication.class);
            s2.assertPresentAt(12, TransactionFinishedIndication.class);
            s2.assertPresentAt(13, TransactionDisposedIndication.class);
            s2.assertPresentAt(14, EntityDisposedIndication.class);

            // Assert TX PDUs: sender
            UtLayerTxPduDecorator l1 = (UtLayerTxPduDecorator) e1.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu1 = l1.getTxPdus();
            assertEquals(14, txPdu1.size());
            // First: metadata + 11 file data + EOF + Finished ACK
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
            assertEquals(FileDataPdu.class, txPdu1.get(12).getClass());
            assertEquals(AckPdu.class, txPdu1.get(13).getClass());
            assertEquals(FileDirectivePdu.DC_FINISHED_PDU, ((AckPdu) txPdu1.get(13)).getDirectiveCode());

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(4, txPdu2.size());
            // First: EOF ACK + Finished
            assertEquals(AckPdu.class, txPdu2.get(0).getClass());
            assertEquals(FileDirectivePdu.DC_EOF_PDU, ((AckPdu) txPdu2.get(0)).getDirectiveCode());
            assertEquals(NakPdu.class, txPdu2.get(1).getClass());
            assertEquals(NakPdu.class, txPdu2.get(2).getClass());
            assertEquals(FinishedPdu.class, txPdu2.get(3).getClass());
            assertEquals(FinishedPdu.FileStatus.RETAINED_IN_FILESTORE, ((FinishedPdu) txPdu2.get(3)).getFileStatus());
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
    public void testAcknowledgedTransactionInactivityReceiver() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1_inactivity.xml", 23001, UtLayerTxPduDecorator.rule("Drop everything except metadata PDU", new Function<>() {
            @Override
            public Boolean apply(CfdpPdu cfdpPdu) {
                return !(cfdpPdu instanceof MetadataPdu);
            }
        }));
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2_inactivity.xml", 23002);
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
            TransactionFinishedIndication ai = (TransactionFinishedIndication) s2.waitForIndication(TransactionFinishedIndication.class, 20000);
            assertEquals(FileDirectivePdu.CC_INACTIVITY_DETECTED, ai.getConditionCode());
            // Wait for the disposition of s1
            s1.waitForIndication(TransactionDisposedIndication.class, 30000);

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
            s1.assertPresentAt(0, TransactionIndication.class); // OK
            s1.assertPresentAt(1, EofSentIndication.class); // OK
            TransactionFinishedIndication tfi = s1.assertPresentAt(2, TransactionFinishedIndication.class); // Failure in receiving the ACK, because the other side does not receive anything
            assertEquals(FileDirectivePdu.CC_POS_ACK_LIMIT_REACHED, tfi.getConditionCode());
            s1.assertPresentAt(3, EofSentIndication.class); // EOF(Cancel) sent: perfectly acceptable by the standard, see 4.11.1, point 2
            s1.assertPresentAt(4, AbandonedIndication.class); // Fail on ACK -> Abandon, as we are already cancelling
            TransactionDisposedIndication dispInd = s1.assertPresentAt(5, TransactionDisposedIndication.class);
            assertEquals(CfdpTransactionState.ABANDONED, dispInd.getStatusReport().getCfdpTransactionState());
            s1.assertPresentAt(6, EntityDisposedIndication.class);

            s2.print();
            s2.assertPresentAt(0, MetadataRecvIndication.class);
            TransactionFinishedIndication trFi2 = s2.assertPresentAt(1, TransactionFinishedIndication.class);
            assertEquals(FileDirectivePdu.CC_INACTIVITY_DETECTED, trFi2.getConditionCode()); // No other PDUs received besides metadata, inactivity triggers before Pos. ACK for Finished
            AbandonedIndication abbI = s2.assertPresentAt(2, AbandonedIndication.class); // Timeout during cancelling, i.e. sending another Finished PDU
            assertEquals(FileDirectivePdu.CC_POS_ACK_LIMIT_REACHED, abbI.getConditionCode());
            s2.assertPresentAt(3, TransactionDisposedIndication.class);
            s2.assertPresentAt(4, EntityDisposedIndication.class);

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
    public void testAcknowledgedTransactionSwappedFileSegments() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntityEnhanced("configuration_entity_1.xml", 23001, new UtLayerTxPduSwapperDecorator.TriConsumer() {
            FileDataPdu data7 = null;
            int count = 0;
            @Override
            public void accept(CfdpPdu pdu, IUtLayer delegate, long destinationEntityId) throws UtLayerException {
                if(pdu instanceof FileDataPdu) {
                    ++count;
                    if(count == 7) {
                        data7 = (FileDataPdu) pdu;
                    } else if (count == 8) {
                        delegate.request(pdu, destinationEntityId);
                        delegate.request(data7, destinationEntityId);
                    } else {
                        delegate.request(pdu, destinationEntityId);
                    }
                } else {
                    delegate.request(pdu, destinationEntityId);
                }
            }
        });
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);
        try {
            // Subscription to the entities
            EntityIndicationSubscriber s1 = new EntityIndicationSubscriber();
            e1.register(s1);
            EntityIndicationSubscriber s2 = new EntityIndicationSubscriber();
            e2.register(s2);
            // Enable reachability of the two entities
            ((AbstractUtLayer)((UtLayerTxPduDecorator)((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate()).getDelegate()).setRxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 1);
            ((AbstractUtLayer)((UtLayerTxPduDecorator)((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate()).getDelegate()).setTxAvailability(true, 2);
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
            // Deactivate the UT layers
            ((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
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

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP"));
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(2, txPdu2.size());
            assertEquals(FileDirectivePdu.DC_EOF_PDU, ((AckPdu) txPdu2.get(0)).getDirectiveCode());
            assertEquals(FinishedPdu.class, txPdu2.get(1).getClass());
            assertEquals(FinishedPdu.FileStatus.RETAINED_IN_FILESTORE, ((FinishedPdu) txPdu2.get(1)).getFileStatus());
        } catch (Throwable e) {
            // Deactivate the UT layers
            ((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
            // Dispose the entities
            e1.dispose();
            e2.dispose();
            throw e;
        }
    }

    @Test
    public void testUnacknowledgedTransactionSwappedFileSegmentMetadata() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntityEnhanced("configuration_entity_1.xml", 23001, new UtLayerTxPduSwapperDecorator.TriConsumer() {
            MetadataPdu metadata = null;
            @Override
            public void accept(CfdpPdu pdu, IUtLayer delegate, long destinationEntityId) throws UtLayerException {
                if(pdu instanceof MetadataPdu) {
                    metadata = (MetadataPdu) pdu;
                } else if(pdu instanceof FileDataPdu) {
                    if(metadata != null) {
                        delegate.request(pdu, destinationEntityId);
                        delegate.request(metadata, destinationEntityId);
                        metadata = null;
                    } else {
                        delegate.request(pdu, destinationEntityId);
                    }
                } else {
                    delegate.request(pdu, destinationEntityId);
                }
            }
        });
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);
        try {
            // Subscription to the entities
            EntityIndicationSubscriber s1 = new EntityIndicationSubscriber();
            e1.register(s1);
            EntityIndicationSubscriber s2 = new EntityIndicationSubscriber();
            e2.register(s2);
            // Enable reachability of the two entities
            ((AbstractUtLayer)((UtLayerTxPduDecorator)((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate()).getDelegate()).setRxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 1);
            ((AbstractUtLayer)((UtLayerTxPduDecorator)((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate()).getDelegate()).setTxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 1);
            // Create file in filestore
            String path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.bin", 10); // 10 KB
            String destPath = "recv_testfile_ack.bin";
            // Create request and start transaction
            PutRequest fduTxReq = new PutRequest(2, path, destPath, false, null, false, false, null, null, null);
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the file was transferred and it has exactly the same contents of the source file
            assertTrue(e2.getFilestore().fileExists(destPath));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));
            // Deactivate the UT layers
            ((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
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

            s2.print();
            s2.assertPresentAt(0, FileSegmentRecvIndication.class);
            MetadataRecvIndication metaInd = s2.assertPresentAt(1, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024 * 10, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
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

            // Assert TX PDUs: receiver (not acked, no closure)
            UtLayerTxPduDecorator l2 = ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP"));
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(0, txPdu2.size());
        } catch (Throwable e) {
            // Deactivate the UT layers
            ((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
            // Dispose the entities
            e1.dispose();
            e2.dispose();
            throw e;
        }
    }

    @Test
    public void testUnacknowledgedTransactionSwappedFileSegmentMetadataWithClosure() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntityEnhanced("configuration_entity_1.xml", 23001, new UtLayerTxPduSwapperDecorator.TriConsumer() {
            MetadataPdu metadata = null;
            @Override
            public void accept(CfdpPdu pdu, IUtLayer delegate, long destinationEntityId) throws UtLayerException {
                if(pdu instanceof MetadataPdu) {
                    metadata = (MetadataPdu) pdu;
                } else if(pdu instanceof FileDataPdu) {
                    if(metadata != null) {
                        delegate.request(pdu, destinationEntityId);
                        delegate.request(metadata, destinationEntityId);
                        metadata = null;
                    } else {
                        delegate.request(pdu, destinationEntityId);
                    }
                } else {
                    delegate.request(pdu, destinationEntityId);
                }
            }
        });
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);
        try {
            // Subscription to the entities
            EntityIndicationSubscriber s1 = new EntityIndicationSubscriber();
            e1.register(s1);
            EntityIndicationSubscriber s2 = new EntityIndicationSubscriber();
            e2.register(s2);
            // Enable reachability of the two entities
            ((AbstractUtLayer)((UtLayerTxPduDecorator)((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate()).getDelegate()).setRxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setRxAvailability(true, 1);
            ((AbstractUtLayer)((UtLayerTxPduDecorator)((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate()).getDelegate()).setTxAvailability(true, 2);
            ((AbstractUtLayer)((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate()).setTxAvailability(true, 1);
            // Create file in filestore
            String path = TestUtils.createRandomFileIn(e1.getFilestore(), "testfile_ack.bin", 10); // 10 KB
            String destPath = "recv_testfile_ack.bin";
            // Create request and start transaction
            PutRequest fduTxReq = new PutRequest(2, path, destPath, false, null, false, true, null, null, null);
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);
            s2.waitForIndication(TransactionDisposedIndication.class, 10000);
            // Check that the file was transferred and it has exactly the same contents of the source file
            assertTrue(e2.getFilestore().fileExists(destPath));
            assertTrue(TestUtils.compareFiles(e1.getFilestore(), path, e2.getFilestore(), destPath));
            // Deactivate the UT layers
            ((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
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

            s2.print();
            s2.assertPresentAt(0, FileSegmentRecvIndication.class);
            MetadataRecvIndication metaInd = s2.assertPresentAt(1, MetadataRecvIndication.class);
            assertEquals(1L, metaInd.getSourceEntityId());
            assertEquals(1024 * 10, metaInd.getFileSize());
            assertEquals(destPath, metaInd.getDestinationFileName());
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

            // Assert TX PDUs: receiver (not acked, closure)
            UtLayerTxPduDecorator l2 = ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP"));
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(1, txPdu2.size());
            assertEquals(FinishedPdu.class, txPdu2.get(0).getClass());
        } catch (Throwable e) {
            // Deactivate the UT layers
            ((UtLayerTxPduSwapperDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
            // Dispose the entities
            e1.dispose();
            e2.dispose();
            throw e;
        }
    }

    // TODO add tests with missing PDUs for Class 1 delivery, with and without closure
}
