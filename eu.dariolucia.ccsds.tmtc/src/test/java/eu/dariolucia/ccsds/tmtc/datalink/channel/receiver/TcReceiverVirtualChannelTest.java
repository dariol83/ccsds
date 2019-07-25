/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver;

import eu.dariolucia.ccsds.tmtc.coding.reader.LineHexDumpChannelReader;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StreamUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TcReceiverVirtualChannelTest {

    private static final String FILE_TC1 = "dumpFile_tc_plain_1.hex";

    @Test
    public void testTcVc0SpacePacket() {
        // Create a virtual channel for VC0
        TcReceiverVirtualChannel vc0 = new TcReceiverVirtualChannel(0, VirtualChannelAccessMode.Packet, true);
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        final AtomicInteger frameCounter = new AtomicInteger(0);
        vc0.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                frameCounter.incrementAndGet();
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame lastFrame, byte[] packet, boolean qualityIndicator) {
                if(qualityIndicator) {
                    goodPackets.add(packet);
                } else {
                    badPackets.add(packet);
                }
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {

            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {

            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {

            }
        });
        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TC1));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the TC frames
                .map(TcTransferFrame.decodingFunction(true, false)) // Convert to TC frame, segmented, no FECF
                .forEach(vc0);
        // Check the number of TC frames
        assertEquals(10, frameCounter.get());
        // Check the list of packets
        assertEquals(30, goodPackets.size());
        // No bad packets
        assertEquals(0, badPackets.size());

    }
}