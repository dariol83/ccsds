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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.decoder.ReedSolomonDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmAsmDecoder;
import eu.dariolucia.ccsds.tmtc.coding.reader.LineHexDumpChannelReader;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.EncapsulationPacket;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StreamUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;

class TmReceiverVirtualChannelTest {

    private static String FILE_TM1 = "dumpFile_tm_1.hex";
    private static String FILE_TM2 = "dumpFile_tm_segmentation_bug.hex";
    private static String FILE_TM1_DATA = "dumpFile_tm_user_data.hex";
    private static String FILE_TM3 = "dumpFile_tm_segmentation_mixed.hex";
    private static String FILE_TM4 = "dumpFile_tm_large_packets.hex";
    private static String FILE_TM_ENC = "dumpFile_tm_encapsulation.hex";
    private static String FILE_TM_ENC_GAP = "dumpFile_tm_encapsulation_gap.hex";

    @Test
    public void testTmVc0SpacePacket() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, true);
        assertEquals(VirtualChannelAccessMode.PACKET, vc0.getReceiverMode());
        assertEquals(-1, vc0.getCurrentVcSequenceCounter());
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        IVirtualChannelReceiverOutput output = new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                //
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                if(qualityIndicator) {
                    goodPackets.add(packet);
                } else {
                    badPackets.add(packet);
                }
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                //
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
                //
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
                //
            }
        };
        vc0.register(output);

        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .filter(o -> o.getVirtualChannelId() == 0) // Filter out VCs not equal to 0
                .forEach(vc0);
        // Check the list of packets
        assertEquals(613, goodPackets.size());
        assertEquals(0, badPackets.size());

        assertEquals(121, vc0.getCurrentVcSequenceCounter());
        vc0.deregister(output);
    }

    @Test
    public void testTmVc7SpacePacket() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc7 = new TmReceiverVirtualChannel(7, VirtualChannelAccessMode.PACKET, true);
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        vc7.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {

            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
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
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .filter(o -> o.getVirtualChannelId() == 7) // Filter out VCs not equal to 7
                .forEach(vc7);
        // Check the list of packets (none - only idle frames)
        assertEquals(0, goodPackets.size());
        assertEquals(0, badPackets.size());

        // Use stream approach: verify idle frames
        boolean allIdleFrames = StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .filter(o -> o.getVirtualChannelId() == 7) // Filter out VCs not equal to 7
                .allMatch(TmTransferFrame::isIdleFrame);

        assertTrue(allIdleFrames);
    }

    @Test
    public void testTmSegmentationSpacePacket() {
        // Create a virtual channel for VC1
        TmReceiverVirtualChannel vc1 = new TmReceiverVirtualChannel(1, VirtualChannelAccessMode.PACKET, true);
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        vc1.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {

            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                assertEquals(456, packet.length);
                goodPackets.add(packet);
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
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM2));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .forEach(vc1);
        // Check the list of packets
        assertEquals(6, goodPackets.size());
    }


    @Test
    public void testTmUserData() {
        // Create a virtual channel for VC0, 1, 7
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.DATA, true);
        TmReceiverVirtualChannel vc1 = new TmReceiverVirtualChannel(1, VirtualChannelAccessMode.DATA, true);
        TmReceiverVirtualChannel vc7 = new TmReceiverVirtualChannel(7, VirtualChannelAccessMode.DATA, true);
        // Subscribe a collector
        final AtomicInteger frameCounter = new AtomicInteger(0);
        IVirtualChannelReceiverOutput output = new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                frameCounter.incrementAndGet();
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                fail("No space packets expected");
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                assertEquals(1105, data.length);
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
                fail("No bit stream expected");
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
                fail("No gaps expected");
            }
        };
        vc0.register(output);
        vc1.register(output);
        vc7.register(output);
        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1_DATA));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .filter(o -> o.getVirtualChannelId() == 0) // Filter out VCs not equal to 0
                .forEach(vc0);

        reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1_DATA));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .filter(o -> o.getVirtualChannelId() == 1) // Filter out VCs not equal to 1
                .forEach(vc1);

        reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1_DATA));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .filter(o -> o.getVirtualChannelId() == 7) // Filter out VCs not equal to 1
                .forEach(vc7);

        // Check the number of frames
        assertEquals(30, frameCounter.get());
    }

    @Test
    public void testTmSegmentationMixedSpacePacket() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, true);
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        vc0.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                // Nothing
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                SpacePacket sp = new SpacePacket(packet, qualityIndicator);
                assertEquals(200, sp.getApid());
                goodPackets.add(packet);
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                // Nothing
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
                // Nothing
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
                // Nothing
            }
        });
        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM3));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .forEach(vc0);
        // Check the list of packets
        assertEquals(10, goodPackets.size());
    }

    @Test
    public void testTmSegmentationMissingOneFrame() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, true);
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        AtomicInteger gapDetected = new AtomicInteger(0);
        vc0.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                // Nothing
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                SpacePacket sp = new SpacePacket(packet, qualityIndicator);
                assertEquals(200, sp.getApid());
                if(qualityIndicator) {
                    goodPackets.add(packet);
                } else {
                    badPackets.add(packet);
                }
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                // Nothing
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
                // Nothing
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
                gapDetected.addAndGet(missingFrames);
            }
        });
        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM3));
        List<byte[]> frames = StreamUtil.from(reader).collect(Collectors.toList());
        // Remove frame at index 2
        frames.remove(2);

        // Use stream approach: no need for decoder
        frames.stream()
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .forEach(vc0);
        // Check the list of packets
        assertEquals(1, gapDetected.get());
        assertEquals(9, goodPackets.size());
        assertEquals(1, badPackets.size());
    }

    @Test
    public void testTmSegmentationSilentDrop() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, true);
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        AtomicInteger gapDetected = new AtomicInteger(0);
        vc0.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                // Nothing
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                SpacePacket sp = new SpacePacket(packet, qualityIndicator);
                assertEquals(200, sp.getApid());
                if(qualityIndicator) {
                    goodPackets.add(packet);
                } else {
                    badPackets.add(packet);
                }
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                // Nothing
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
                // Nothing
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
                gapDetected.addAndGet(missingFrames);
            }
        });
        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM3));
        List<byte[]> frames = StreamUtil.from(reader).collect(Collectors.toList());
        // Remove frame at index 7
        frames.remove(7);

        // Use stream approach: no need for decoder
        frames.stream()
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .forEach(vc0);
        // Check the list of packets
        assertEquals(1, gapDetected.get());
        assertEquals(9, goodPackets.size());
        assertEquals(0, badPackets.size());
    }

    @Test
    public void testTmLargePacketSegmentedStart() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, true);
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        AtomicInteger gapDetected = new AtomicInteger(0);
        vc0.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                // Nothing
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                SpacePacket sp = new SpacePacket(packet, qualityIndicator);
                assertEquals(200, sp.getApid());
                if(goodPackets.size() == 4) {
                    // Last packet is shorter
                    assertEquals(684, sp.getPacketDataLength());
                } else {
                    assertEquals(14000, sp.getPacketDataLength());
                }
                if(qualityIndicator) {
                    goodPackets.add(packet);
                } else {
                    badPackets.add(packet);
                }
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                // Nothing
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
                // Nothing
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
                gapDetected.addAndGet(missingFrames);
            }
        });
        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM4));
        List<byte[]> frames = StreamUtil.from(reader).collect(Collectors.toList());

        // Use stream approach: no need for decoder
        frames.stream()
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .forEach(vc0);
        // Check the list of packets
        assertEquals(0, gapDetected.get());
        assertEquals(5, goodPackets.size());
        assertEquals(0, badPackets.size());
    }

    @Test
    public void testTmVc0EncapsulationPacket() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.ENCAPSULATION, true);
        assertEquals(VirtualChannelAccessMode.ENCAPSULATION, vc0.getReceiverMode());
        assertEquals(-1, vc0.getCurrentVcSequenceCounter());
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        IVirtualChannelReceiverOutput output = new IVirtualChannelReceiverOutput() {

            @Override
            public void encapsulationPacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                if(qualityIndicator) {
                    goodPackets.add(packet);
                } else {
                    badPackets.add(packet);
                }
            }

            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                //
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                throw new RuntimeException("Should not be called");
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                //
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
                //
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
                //
            }
        };
        vc0.register(output);

        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM_ENC));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .filter(o -> o.getVirtualChannelId() == 0) // Filter out VCs not equal to 0
                .forEach(vc0);
        // Check the list of packets
        assertEquals(13, goodPackets.size());
        assertEquals(0, badPackets.size());

        // Check the encapsulation packets
        for(int i = 0; i < 13; ++i) {
            EncapsulationPacket ep = new EncapsulationPacket(goodPackets.get(i), true);
            switch (i) {
                case 0:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    break;
                case 1:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    break;
                case 2:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    break;
                case 3:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    break;
                case 4:
                    assertEquals(2, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    break;
                case 5:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    assertEquals(1115 - 4 - 6 - 1, ep.getLength());
                    break;
                case 6:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    break;
                case 7:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    break;
                case 8:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    break;
                case 9:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
                    break;
                case 10:
                    assertEquals(1, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE, ep.getEncapsulationProtocolId());
                    break;
                case 11:
                    assertEquals(4, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE, ep.getEncapsulationProtocolId());
                    break;
                case 12:
                    assertEquals(8, ep.getPrimaryHeaderLength());
                    assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE, ep.getEncapsulationProtocolId());
                    break;
            }
        }

        assertEquals(10, vc0.getCurrentVcSequenceCounter());
        vc0.deregister(output);
    }

    @Test
    public void testTmVc0EncapsulationPacketGap() {
        // Create a virtual channel for VC0
        TmReceiverVirtualChannel vc0 = new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.ENCAPSULATION, true);
        assertEquals(VirtualChannelAccessMode.ENCAPSULATION, vc0.getReceiverMode());
        assertEquals(-1, vc0.getCurrentVcSequenceCounter());
        // Subscribe a packet collector
        List<byte[]> goodPackets = new CopyOnWriteArrayList<>();
        List<byte[]> badPackets = new CopyOnWriteArrayList<>();
        IVirtualChannelReceiverOutput output = new IVirtualChannelReceiverOutput() {

            @Override
            public void encapsulationPacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                if(qualityIndicator) {
                    goodPackets.add(packet);
                } else {
                    badPackets.add(packet);
                }
            }

            @Override
            public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                //
            }

            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                throw new RuntimeException("Should not be called");
            }

            @Override
            public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                //
            }

            @Override
            public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
                //
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
                //
            }
        };
        vc0.register(output);

        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM_ENC_GAP));
        // Use stream approach: no need for decoder
        StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .filter(o -> o.getVirtualChannelId() == 0) // Filter out VCs not equal to 0
                .forEach(vc0);
        // Check the list of packets
        assertEquals(12, goodPackets.size());
        assertEquals(1, badPackets.size());

        assertEquals(10, vc0.getCurrentVcSequenceCounter());
        vc0.deregister(output);
    }
}