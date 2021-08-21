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

import eu.dariolucia.ccsds.cfdp.entity.indication.AbandonedIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.EntityDisposedIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.TransactionDisposedIndication;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;
import eu.dariolucia.ccsds.cfdp.ut.impl.AbstractUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.impl.TcpLayer;
import eu.dariolucia.ccsds.cfdp.util.EntityIndicationSubscriber;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import eu.dariolucia.ccsds.cfdp.util.UtLayerTxPduDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CfdpEntityFileStoreErrorTest {

    @BeforeEach
    public void setup() {
        Logger.getLogger("").setLevel(Level.ALL);
        Logger.getLogger("eu.dariolucia.ccsds.cfdp").setLevel(Level.ALL);
        for(Handler h : Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
        }
    }

    @Test
    public void testFileRejected() throws Exception {
        // Create the two entities
        ICfdpEntity e1 = TestUtils.createTcpEntity("configuration_entity_1.xml", 23001);
        InputStream in = TestUtils.class.getClassLoader().getResourceAsStream("configuration_entity_2.xml");
        Mib conf1File = Mib.load(in);
        IVirtualFilestore fs1 = new IVirtualFilestore() {
            @Override
            public void createFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void deleteFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void renameFile(String fullPath, String newFullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void appendContentsToFile(String fullPath, byte[] data) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void replaceFileContents(String fullPath, byte[] data) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void appendFileToFile(String targetFilePath, String fileToAddPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void replaceFileWithFile(String targetFilePath, String fileToAddPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public byte[] getFile(String fullPath) throws FilestoreException {
                return new byte[0];
            }

            @Override
            public void createDirectory(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void deleteDirectory(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public List<String> listDirectory(String fullPath, boolean recursive) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public boolean fileExists(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public boolean directoryExists(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public long fileSize(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public boolean isUnboundedFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public InputStream readFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public OutputStream writeFile(String fullPath, boolean append) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }
        };
        TcpLayer tcpLayer = new TcpLayer(conf1File, 23002);
        tcpLayer.activate();
        // Add UT Layer decorator
        UtLayerTxPduDecorator decorator = new UtLayerTxPduDecorator(tcpLayer);
        ICfdpEntity e2 = ICfdpEntity.create(conf1File, fs1, decorator);
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

            // Assert indications: receiver
            s2.print();

            // Assert TX PDUs: receiver
            UtLayerTxPduDecorator l2 = (UtLayerTxPduDecorator) e2.getUtLayerByName("TCP");
            List<CfdpPdu> txPdu2 = l2.getTxPdus();
            assertEquals(2, txPdu2.size());
            // First: EOF ACK + Finished
            assertEquals(AckPdu.class, txPdu2.get(0).getClass());
            assertEquals(FileDirectivePdu.DC_EOF_PDU, ((AckPdu) txPdu2.get(0)).getDirectiveCode());
            assertEquals(FinishedPdu.class, txPdu2.get(1).getClass());
            assertEquals(FinishedPdu.FileStatus.DISCARDED_BY_FILESTORE, ((FinishedPdu) txPdu2.get(1)).getFileStatus());
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
    public void testFileNotAvailable() throws Exception {
        // Create the two entities
        ICfdpEntity e2 = TestUtils.createTcpEntity("configuration_entity_2.xml", 23002);
        InputStream in = TestUtils.class.getClassLoader().getResourceAsStream("configuration_entity_1.xml");
        Mib conf1File = Mib.load(in);
        IVirtualFilestore fs1 = new IVirtualFilestore() {
            @Override
            public void createFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void deleteFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void renameFile(String fullPath, String newFullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void appendContentsToFile(String fullPath, byte[] data) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void replaceFileContents(String fullPath, byte[] data) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void appendFileToFile(String targetFilePath, String fileToAddPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void replaceFileWithFile(String targetFilePath, String fileToAddPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public byte[] getFile(String fullPath) throws FilestoreException {
                return new byte[0];
            }

            @Override
            public void createDirectory(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public void deleteDirectory(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public List<String> listDirectory(String fullPath, boolean recursive) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public boolean fileExists(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public boolean directoryExists(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public long fileSize(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public boolean isUnboundedFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public InputStream readFile(String fullPath) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }

            @Override
            public OutputStream writeFile(String fullPath, boolean append) throws FilestoreException {
                throw new FilestoreException("Filestore test exception");
            }
        };
        TcpLayer tcpLayer = new TcpLayer(conf1File, 23001);
        tcpLayer.activate();
        // Add UT Layer decorator
        UtLayerTxPduDecorator decorator = new UtLayerTxPduDecorator(tcpLayer);
        ICfdpEntity e1 = ICfdpEntity.create(conf1File, fs1, decorator);
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
            String path = "testfile_ack.bin";
            String destPath = "recv_testfile_ack.bin";
            // Create request and start transaction
            PutRequest fduTxReq = PutRequest.build(2, path, destPath, false, null);
            e1.request(fduTxReq);
            // Wait for the transaction to be disposed on the two entities
            s1.waitForIndication(TransactionDisposedIndication.class, 10000);

            // Deactivate the UT layers
            ((UtLayerTxPduDecorator) e1.getUtLayerByName("TCP")).getDelegate().dispose();
            ((UtLayerTxPduDecorator) e2.getUtLayerByName("TCP")).getDelegate().dispose();
            // Dispose the entities
            e1.dispose();
            e2.dispose();

            // Wait for the entity disposition
            s1.waitForIndication(EntityDisposedIndication.class, 1000);

            // Assert indications: sender
            s1.print();
            assertEquals(4, s1.getIndicationListSize());
            AbandonedIndication ai = s1.assertPresentAt(1, AbandonedIndication.class);
            assertEquals(ConditionCode.CC_FILESTORE_REJECTION, ai.getConditionCode());
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
