/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.demux;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.decoder.ReedSolomonDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmAsmDecoder;
import eu.dariolucia.ccsds.tmtc.coding.reader.LineHexDumpChannelReader;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.IVirtualChannelReceiverOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.TmReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StreamUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class VirtualChannelReceiverDemuxTest {

    @Test
    public void testTmVc0SpacePacket() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.Packet, true);
        // Create a virtual channel for VC7
        TmReceiverVirtualChannel vc7 = new TmReceiverVirtualChannel(7, VirtualChannelAccessMode.Data, true);
        // Create a VC demux for VC0 and VC7
        VirtualChannelReceiverDemux demux = new VirtualChannelReceiverDemux(vc0, vc7);
        // Subscribe a packet collector for VC0
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        List<byte[]> idleFrames = new CopyOnWriteArrayList<>();
        IVirtualChannelReceiverOutput channelListener = new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {

            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                assertEquals(0, vc.getVirtualChannelId());
                if(qualityIndicator) {
                    goodPackets.add(packet);
                } else {
                    badPackets.add(packet);
                }
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                assertEquals(7, vc.getVirtualChannelId());
                assertTrue(frame.isIdleFrame());
                idleFrames.add(data);
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {

            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {

            }
        };
        vc0.register(channelListener);
        vc7.register(channelListener);
        // Build the reader
        String FILE_TM1 = "dumpFile_tm_1.hex";
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .forEach(demux); // Push to the demux
        // Check the list of packets
        assertEquals(613, goodPackets.size());
        assertEquals(0, badPackets.size());
        assertEquals(30, idleFrames.size());
    }

    @Test
    public void testTmVc0MissingHandler() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.Packet, true);
        // Unhandled frames
        List<AbstractTransferFrame> notHandled = new LinkedList<>();
        // Create a VC demux for VC0 and VC7
        VirtualChannelReceiverDemux demux = new VirtualChannelReceiverDemux(notHandled::add, vc0);
        // Subscribe a packet collector for VC0
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        List<byte[]> idleFrames = new CopyOnWriteArrayList<>();
        IVirtualChannelReceiverOutput channelListener = new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                //
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                assertEquals(0, vc.getVirtualChannelId());
                if(qualityIndicator) {
                    goodPackets.add(packet);
                } else {
                    badPackets.add(packet);
                }
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                assertEquals(7, vc.getVirtualChannelId());
                assertTrue(frame.isIdleFrame());
                idleFrames.add(data);
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {

            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {

            }
        };
        vc0.register(channelListener);
        // Build the reader
        String FILE_TM1 = "dumpFile_tm_1.hex";
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .forEach(demux); // Push to the demux
        // Check the list of packets
        assertEquals(613, goodPackets.size());
        assertEquals(0, badPackets.size());
        assertEquals(0, idleFrames.size());
        assertEquals(30, notHandled.size());
    }

    @Test
    public void testDoubleRegistration() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.Packet, true);
        // Unhandled frames
        List<AbstractTransferFrame> notHandled = new LinkedList<>();
        // Create a VC demux for VC0 and VC7
        VirtualChannelReceiverDemux demux = new VirtualChannelReceiverDemux(notHandled::add, vc0);
        // Register again vc0
        TmReceiverVirtualChannel vc0double = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.Packet, true);
        try {
            demux.register(vc0);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // Good
        }
    }
}