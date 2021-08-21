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

package eu.dariolucia.ccsds.cfdp.ut.impl;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.protocol.builder.EndOfFilePduBuilder;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.EndOfFilePdu;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayerSubscriber;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class UdpLayerTest {

    @Test
    public void testUdpLayer() throws IOException, UtLayerException, InterruptedException {
        UdpLayer tl2 = null;
        UdpLayer tl = null;
        try {
            InputStream in = TestUtils.class.getClassLoader().getResourceAsStream("configuration_entity_1.xml");
            Mib conf1File = Mib.load(in);

            assertThrows(IllegalArgumentException.class, () -> new TcpLayer(conf1File, -1));

            conf1File.getRemoteEntityById(2).setUtAddress("whatever");
            tl = new UdpLayer(conf1File, 23001);
            tl.setRxAvailability(true, 2);
            tl.setTxAvailability(true, 2);
            // Add failing subscriber
            tl.register(new IUtLayerSubscriber() {
                @Override
                public void indication(IUtLayer layer, CfdpPdu pdu) {
                    throw new RuntimeException("On purpose failure");
                }

                @Override
                public void startTxPeriod(IUtLayer layer, long entityId) {
                    throw new RuntimeException("On purpose failure");
                }

                @Override
                public void endTxPeriod(IUtLayer layer, long entityId) {
                    throw new RuntimeException("On purpose failure");
                }

                @Override
                public void startRxPeriod(IUtLayer layer, long entityId) {
                    throw new RuntimeException("On purpose failure");
                }

                @Override
                public void endRxPeriod(IUtLayer layer, long entityId) {
                    throw new RuntimeException("On purpose failure");
                }
            });
            assertNotNull(tl.toString());
            tl.activate();
            // Again, nothing should happen
            tl.activate();

            UdpLayer ftl = tl;
            assertThrows(UtLayerException.class, () -> ftl.request(prepareEndOfFilePdu(), 2));
            conf1File.getRemoteEntityById(2).setUtAddress("whatever:test:test");
            assertThrows(UtLayerException.class, () -> ftl.request(prepareEndOfFilePdu(), 2));
            conf1File.getRemoteEntityById(2).setUtAddress("udp:forget_this_exists_test:test");
            assertThrows(UtLayerException.class, () -> ftl.request(prepareEndOfFilePdu(), 2));
            conf1File.getRemoteEntityById(2).setUtAddress("udp:localhost:test");
            assertThrows(UtLayerException.class, () -> ftl.request(prepareEndOfFilePdu(), 2));
            conf1File.getRemoteEntityById(2).setUtAddress("udp:localhost:123123");
            assertThrows(UtLayerException.class, () -> ftl.request(prepareEndOfFilePdu(), 2));
            conf1File.getRemoteEntityById(2).setUtAddress("udp:localhost:23002");

            UdpLayer lerr = new UdpLayer(conf1File, 23001);
            assertThrows(UtLayerException.class, lerr::activate);

            InputStream in2 = TestUtils.class.getClassLoader().getResourceAsStream("configuration_entity_2.xml");
            Mib conf2File = Mib.load(in2);
            conf2File.getRemoteEntityById(1).setUtAddress("udp:localhost:23001");

            tl2 = new UdpLayer(conf2File, 23002);
            tl2.setRxAvailability(false, 1);
            tl2.setTxAvailability(false, 1);
            // Add failing subscriber
            tl2.register(new IUtLayerSubscriber() {
                @Override
                public void indication(IUtLayer layer, CfdpPdu pdu) {
                    throw new RuntimeException("On purpose failure");
                }

                @Override
                public void startTxPeriod(IUtLayer layer, long entityId) {
                    throw new RuntimeException("On purpose failure");
                }

                @Override
                public void endTxPeriod(IUtLayer layer, long entityId) {
                    throw new RuntimeException("On purpose failure");
                }

                @Override
                public void startRxPeriod(IUtLayer layer, long entityId) {
                    throw new RuntimeException("On purpose failure");
                }

                @Override
                public void endRxPeriod(IUtLayer layer, long entityId) {
                    throw new RuntimeException("On purpose failure");
                }
            });
            tl2.setRxAvailability(true, 1);
            tl2.setTxAvailability(true, 1);
            assertNotNull(tl.toString());
            tl2.activate();

            // Send a crap PDU first
            // Build the datagram and send it to the socket
            DatagramPacket dp = new DatagramPacket(new byte[] {1,2,3,4,5,6}, 6);
            try {
                DatagramSocket ds = new DatagramSocket();
                ds.connect(InetAddress.getByName("127.0.0.1"), 23002);
                ds.send(dp);
            } catch (IOException e) {
                fail("Cannot send forged CfdpPdu to entity 1");
            }

            tl.request(prepareEndOfFilePdu(), 2);

            final UdpLayer ftl2 = tl;
            assertThrows(UtLayerException.class, () -> ftl2.request(prepareEndOfFilePdu(), 12));

            tl.deactivate();

            Thread.sleep(2000);

            tl2.setRxAvailability(false, 1);
            tl2.setTxAvailability(false, 1);

        } finally {
            if(tl2 != null) {
                tl2.deactivate();
            }
            if(tl != null) {
                tl.deactivate();
            }
        }
    }

    private EndOfFilePdu prepareEndOfFilePdu() {
        EndOfFilePduBuilder b = new EndOfFilePduBuilder();
        b.setAcknowledged(true);
        b.setCrcPresent(true);
        b.setDestinationEntityId(2);
        b.setSourceEntityId(1);
        b.setDirection(CfdpPdu.Direction.TOWARD_FILE_RECEIVER);
        b.setSegmentationControlPreserved(false);
        // Set the length for the entity ID
        b.setEntityIdLength(BytesUtil.getEncodingOctetsNb(1));
        // Set the transaction ID
        b.setTransactionSequenceNumber(65537, 3);
        b.setLargeFile(false);
        // EOF specific
        b.setFileChecksum(12345679);
        b.setFileSize(4321);
        b.setConditionCode(ConditionCode.CC_NOERROR, null);

        return b.build();
    }

}