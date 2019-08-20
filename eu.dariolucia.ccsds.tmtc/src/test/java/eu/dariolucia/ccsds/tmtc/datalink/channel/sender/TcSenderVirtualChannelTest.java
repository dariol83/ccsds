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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender;

import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.builder.SpacePacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TcSenderVirtualChannelTest {

    private SpacePacket generateSpacePacket(int apid, int counter) {
        return generateSpacePacket(apid, counter, 400);
    }

    private SpacePacket generateSpacePacket(int apid, int counter, int size) {
        SpacePacketBuilder spp = SpacePacketBuilder.create()
                .setApid(apid)
                .setQualityIndicator(true)
                .setSecondaryHeaderFlag(true)
                .setTelemetryPacket();
        spp.addData(new byte[size]);
        spp.setPacketSequenceCount(counter % 16384);
        return spp.build();
    }


    @Test
    public void testSinglePacketsUnsegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);
        // Generate 10 TC packets
        for (int i = 0; i < 10; ++i) {
            vc0.dispatch(generateSpacePacket(300, i));
        }
        //
        // Checks
        assertEquals(10, list.size());
        for (int i = 0; i < 10; ++i) {
            SpacePacket sp = new SpacePacket(list.get(i).getDataFieldCopy(), true);
            assertEquals(300, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
            assertFalse(list.get(i).isSecurityUsed());
            assertEquals(0, list.get(i).getSecurityHeaderLength());
            assertEquals(0, list.get(i).getSecurityTrailerLength());
        }

        assertFalse(vc0.isSecured());
        assertFalse(vc0.isSegmented());
    }

    @Test
    public void testSinglePacketsUnsegmentedWithFecf() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, true, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);
        // Generate 10 TC packets
        for (int i = 0; i < 10; ++i) {
            vc0.dispatch(generateSpacePacket(300, i));
        }
        //
        // Checks
        assertEquals(10, list.size());
        for (int i = 0; i < 10; ++i) {
            assertTrue(list.get(i).isValid());
            assertTrue(list.get(i).getFecf() != 0);
            SpacePacket sp = new SpacePacket(list.get(i).getDataFieldCopy(), true);
            assertEquals(300, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
        }
    }

    @Test
    public void testDoublePacketsUnsegmentedWithFecf() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, true, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);
        // Generate 10 TC packets
        for (int i = 0; i < 10; ++i) {
            vc0.dispatch(generateSpacePacket(300, i*2), generateSpacePacket(300, i*2 + 1));
        }
        //
        // Checks
        assertEquals(10, list.size());
        for (int i = 0; i < 10; ++i) {
            assertTrue(list.get(i).isValid());
            assertTrue(list.get(i).getFecf() != 0);
            SpacePacket sp = new SpacePacket(Arrays.copyOfRange(list.get(i).getDataFieldCopy(), 0, 400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH), true);
            assertEquals(300, sp.getApid());
            assertEquals(i*2, sp.getPacketSequenceCount());
            assertEquals(400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());

            SpacePacket sp2 = new SpacePacket(Arrays.copyOfRange(list.get(i).getDataFieldCopy(), sp.getPacketDataLength() + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getPacketDataLength() + SpacePacket.SP_PRIMARY_HEADER_LENGTH + 400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH), true);
            assertEquals(300, sp2.getApid());
            assertEquals(i*2 + 1, sp2.getPacketSequenceCount());
            assertEquals(400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp2.getLength());
        }
    }

    // test segmentation + map ID
    @Test
    public void testSinglePacketSegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, true);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);
        // Set the map ID to 2, will be overwritten
        vc0.setMapId(2);

        // Generate 10 TC packets
        for (int i = 0; i < 10; ++i) {
            vc0.dispatch(true, i, generateSpacePacket(300, i));
        }
        //
        // Checks
        assertEquals(10, list.size());
        for (int i = 0; i < 10; ++i) {
            TcTransferFrame fr = list.get(i);
            assertFalse(fr.isBypassFlag());
            assertFalse(fr.isControlCommandFlag());
            byte[] segment = fr.getDataFieldCopy();
            assertEquals(TcTransferFrame.SequenceFlagType.NO_SEGMENT, fr.getSequenceFlag());
            assertEquals(i, fr.getMapId());
            SpacePacket sp = new SpacePacket(Arrays.copyOfRange(segment, 1, segment.length), true);
            assertEquals(300, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
        }
    }

    // test segmentation + map ID
    @Test
    public void testSinglePacketSegmentedWithFecf() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, true, true);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        // Generate 10 TC packets
        for (int i = 0; i < 10; ++i) {
            vc0.dispatch(true, i, generateSpacePacket(300, i));
        }
        //
        // Checks
        assertEquals(10, list.size());
        for (int i = 0; i < 10; ++i) {
            TcTransferFrame fr = list.get(i);
            assertFalse(fr.isBypassFlag());
            assertFalse(fr.isControlCommandFlag());
            assertTrue(fr.isValid());
            assertTrue(fr.getFecf() != 0);
            byte[] segment = fr.getDataFieldCopy();
            assertEquals(TcTransferFrame.SequenceFlagType.NO_SEGMENT, fr.getSequenceFlag());
            assertEquals(i, fr.getMapId());
            SpacePacket sp = new SpacePacket(Arrays.copyOfRange(segment, 1, segment.length), true);
            assertEquals(300, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
        }
    }

    // test unlock
    @Test
    public void testUnlock() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        // Unlock
        vc0.dispatchUnlock();

        // Checks
        assertEquals(1, list.size());
        TcTransferFrame fr = list.get(0);
        assertTrue(fr.isBypassFlag());
        assertTrue(fr.isControlCommandFlag());
        assertEquals(1, fr.getDataFieldLength());
        assertEquals(0x00, fr.getDataFieldCopy()[0]);
        assertEquals(TcTransferFrame.FrameType.BC, fr.getFrameType());
        assertEquals(TcTransferFrame.ControlCommandType.UNLOCK, fr.getControlCommandType());
    }

    // test set VR
    @Test
    public void testSetVr() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        // Set V(R)
        vc0.dispatchSetVr(42);

        // Checks
        assertEquals(1, list.size());
        TcTransferFrame fr = list.get(0);
        assertTrue(fr.isBypassFlag());
        assertTrue(fr.isControlCommandFlag());
        assertEquals(3, fr.getDataFieldLength());
        assertEquals((byte) 0x82, fr.getDataFieldCopy()[0]);
        assertEquals(0x0, fr.getDataFieldCopy()[1]);
        assertEquals(42, fr.getDataFieldCopy()[2]);
        assertEquals(42, fr.getSetVrValue());
        assertEquals(TcTransferFrame.FrameType.BC, fr.getFrameType());
        assertEquals(TcTransferFrame.ControlCommandType.SET_VR, fr.getControlCommandType());
    }

    // test multi packet with segmentation
    @Test
    public void testMultiPacketsSegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, true);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        // Generate 10 TC packets
        for (int i = 0; i < 10; ++i) {
            vc0.dispatch(true, i, generateSpacePacket(300, i, 400), generateSpacePacket(200, i, 200), generateSpacePacket(2000, i, 100));
        }
        //
        // Checks
        assertEquals(10, list.size());
        for (int i = 0; i < 10; ++i) {
            TcTransferFrame fr = list.get(i);
            assertFalse(fr.isBypassFlag());
            assertFalse(fr.isControlCommandFlag());

            byte[] segment = fr.getDataFieldCopy();
            assertEquals(TcTransferFrame.SequenceFlagType.NO_SEGMENT, fr.getSequenceFlag());
            assertEquals(i, fr.getMapId());

            SpacePacket sp = new SpacePacket(Arrays.copyOfRange(segment, 1, 407), true);
            assertEquals(300, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());

            sp = new SpacePacket(Arrays.copyOfRange(segment, 407, 613), true);
            assertEquals(200, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(200 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());

            sp = new SpacePacket(Arrays.copyOfRange(segment, 613, 719), true);
            assertEquals(2000, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(100 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
        }
    }

    // test multi packet without segmentation
    @Test
    public void testMultiPacketsUnsegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        // Generate 10 TC packets
        for (int i = 0; i < 10; ++i) {
            vc0.dispatch(true, i, generateSpacePacket(300, i, 400), generateSpacePacket(200, i, 200), generateSpacePacket(2000, i, 100));
        }
        //
        // Checks
        assertEquals(10, list.size());
        for (int i = 0; i < 10; ++i) {
            TcTransferFrame fr = list.get(i);
            assertFalse(fr.isBypassFlag());
            assertFalse(fr.isControlCommandFlag());

            byte[] segment = fr.getDataFieldCopy();

            SpacePacket sp = new SpacePacket(Arrays.copyOfRange(segment, 0, 406), true);
            assertEquals(300, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(400 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());

            sp = new SpacePacket(Arrays.copyOfRange(segment, 406, 612), true);
            assertEquals(200, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(200 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());

            sp = new SpacePacket(Arrays.copyOfRange(segment, 612, 718), true);
            assertEquals(2000, sp.getApid());
            assertEquals(i, sp.getPacketSequenceCount());
            assertEquals(100 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
        }
    }

    // test large packet with segmentation
    @Test
    public void testLargePacketSegmented() throws IOException {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, true);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        int maxDataLen = vc0.getMaxUserDataLength();

        // Generate 1 TC large packet
        vc0.dispatch(true, 12, generateSpacePacket(300, 22, maxDataLen * 3 + 30)); // spawn on 4 frames

        //
        // Checks
        assertEquals(4, list.size());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        for (int i = 0; i < 4; ++i) {
            TcTransferFrame fr = list.get(i);
            assertFalse(fr.isBypassFlag());
            assertFalse(fr.isControlCommandFlag());

            byte[] segment = fr.getDataFieldCopy();
            if (i == 0) {
                assertEquals(TcTransferFrame.SequenceFlagType.FIRST, fr.getSequenceFlag());
            } else if (i == 3) {
                assertEquals(TcTransferFrame.SequenceFlagType.LAST, fr.getSequenceFlag());
            } else {
                assertEquals(TcTransferFrame.SequenceFlagType.CONTINUE, fr.getSequenceFlag());
            }
            assertEquals(12, fr.getMapId());

            bos.write(Arrays.copyOfRange(segment, 1, segment.length));
        }

        byte[] fullPacket = bos.toByteArray();
        SpacePacket sp = new SpacePacket(fullPacket, true);
        assertEquals(300, sp.getApid());
        assertEquals(22, sp.getPacketSequenceCount());
        assertEquals(maxDataLen * 3 + 30 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
    }

    // test large packet without segmentation
    @Test
    public void testLargePacketUnsegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        int maxDataLen = vc0.getMaxUserDataLength();

        // Generate 1 TC large packet: fail
        try {
            vc0.dispatch(true, 12, generateSpacePacket(300, 22, maxDataLen * 3 + 30)); // spawn on 4 frames
            fail("IllegalArgumentException expected: packet too large");
        } catch (IllegalArgumentException e) {
            // Good
        }
    }

    // test large precise packet without segmentation
    @Test
    public void testLargePrecisePacketUnsegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        int maxDataLen = vc0.getMaxUserDataLength();

        // Generate 1 TC large packet: good
        vc0.dispatch(true, 12, generateSpacePacket(300, 22, maxDataLen - SpacePacket.SP_PRIMARY_HEADER_LENGTH));
        // Checks
        assertEquals(1, list.size());

        TcTransferFrame fr = list.get(0);
        assertFalse(fr.isBypassFlag());
        assertFalse(fr.isControlCommandFlag());

        byte[] fullPacket = fr.getDataFieldCopy();
        SpacePacket sp = new SpacePacket(fullPacket, true);
        assertEquals(300, sp.getApid());
        assertEquals(22, sp.getPacketSequenceCount());
        assertEquals(maxDataLen, sp.getLength());
    }

    @Test
    public void testLargeAndSmallPacketSegmented() throws IOException {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Packet, false, true);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        int maxDataLen = vc0.getMaxUserDataLength();

        // Generate 1 TC small packet, 1 TC small packet, 1 TC large packet, 1 TC small packet
        vc0.dispatch(true, 12,
                generateSpacePacket(300, 22, 200),
                generateSpacePacket(300, 23, 600), // 1 frame here
                generateSpacePacket(300, 24, maxDataLen * 3 + 30), // spawn on 4 frames
                generateSpacePacket(300, 25, 100)); // 1 frame here

        //
        // Checks
        assertEquals(6, list.size());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        for (int i = 0; i < 6; ++i) {
            TcTransferFrame fr = list.get(i);
            assertFalse(fr.isBypassFlag());
            assertFalse(fr.isControlCommandFlag());

            byte[] segment = fr.getDataFieldCopy();
            if (i == 0) {
                SpacePacket sp = new SpacePacket(Arrays.copyOfRange(segment, 1, 207), true);
                assertEquals(300, sp.getApid());
                assertEquals(22, sp.getPacketSequenceCount());
                assertEquals(200 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());

                sp = new SpacePacket(Arrays.copyOfRange(segment, 207, 813), true);
                assertEquals(300, sp.getApid());
                assertEquals(23, sp.getPacketSequenceCount());
                assertEquals(600 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
            } else if (i == 1) {
                assertEquals(TcTransferFrame.SequenceFlagType.FIRST, fr.getSequenceFlag());
                bos.write(Arrays.copyOfRange(segment, 1, segment.length));
            } else if (i == 4) {
                assertEquals(TcTransferFrame.SequenceFlagType.LAST, fr.getSequenceFlag());
                bos.write(Arrays.copyOfRange(segment, 1, segment.length));
            } else if (i == 2 || i == 3) {
                assertEquals(TcTransferFrame.SequenceFlagType.CONTINUE, fr.getSequenceFlag());
                bos.write(Arrays.copyOfRange(segment, 1, segment.length));
            } else if (i == 5) {
                SpacePacket sp = new SpacePacket(Arrays.copyOfRange(segment, 1, 107), true);
                assertEquals(300, sp.getApid());
                assertEquals(25, sp.getPacketSequenceCount());
                assertEquals(100 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
            }
            assertEquals(12, fr.getMapId());
        }

        byte[] fullPacket = bos.toByteArray();
        SpacePacket sp = new SpacePacket(fullPacket, true);
        assertEquals(300, sp.getApid());
        assertEquals(24, sp.getPacketSequenceCount());
        assertEquals(maxDataLen * 3 + 30 + SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getLength());
    }

    // test user data segmented (single frame)
    @Test
    public void testSingleFrameUserDataSegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Data, false, true);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        // Generate 10 TC frames: 5 with full user data, 5 with less data
        for (int i = 0; i < 10; ++i) {
            if(i < 5) {
                vc0.dispatch(true, 44, new byte[vc0.getMaxUserDataLength()]);
            } else {
                vc0.dispatch(true, 45, new byte[vc0.getMaxUserDataLength()/2]);
            }
        }
        //
        // Checks
        assertEquals(10, list.size());
        for (int i = 0; i < 10; ++i) {
            TcTransferFrame fr = list.get(i);
            assertFalse(fr.isBypassFlag());
            assertFalse(fr.isControlCommandFlag());
            byte[] segment = fr.getDataFieldCopy();
            assertEquals(TcTransferFrame.SequenceFlagType.NO_SEGMENT, fr.getSequenceFlag());
            if(i < 5) {
                assertEquals(44, fr.getMapId());
                assertEquals(vc0.getMaxUserDataLength() + 1, segment.length); // it is a segment, so +1 for the segment header
            } else {
                assertEquals(45, fr.getMapId());
                assertEquals(vc0.getMaxUserDataLength()/2 + 1, segment.length); // same as above
            }
        }
    }

    // test user data unsegmented (single frame)
    @Test
    public void testSingleFrameUserDataUnsegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Data, false, false);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        // Generate 10 TC frames: 5 with full user data, 5 with less data
        for (int i = 0; i < 10; ++i) {
            if(i < 5) {
                vc0.dispatch(true, 44, new byte[vc0.getMaxUserDataLength()]);
            } else {
                vc0.dispatch(true, 45, new byte[vc0.getMaxUserDataLength()/2]);
            }
        }
        //
        // Checks
        assertEquals(10, list.size());
        for (int i = 0; i < 10; ++i) {
            TcTransferFrame fr = list.get(i);
            assertFalse(fr.isBypassFlag());
            assertFalse(fr.isControlCommandFlag());
            byte[] segment = fr.getDataFieldCopy();
            if(i < 5) {
                assertEquals(vc0.getMaxUserDataLength(), segment.length);
            } else {
               assertEquals(vc0.getMaxUserDataLength()/2, segment.length);
            }
        }
    }

    // test user data (multi frame)
    @Test
    public void testMultiFrameUserDataSegmented() {
        // Create a sink consumer
        List<TcTransferFrame> list = new LinkedList<>();
        IVirtualChannelSenderOutput<TcTransferFrame> sink = (vc, generatedFrame, bufferedBytes) -> list.add(generatedFrame);
        // Setup the VC
        TcSenderVirtualChannel vc0 = new TcSenderVirtualChannel(123, 0, VirtualChannelAccessMode.Data, false, true);
        // Register the sink
        vc0.register(sink);
        // Set channel properties (BD mode)
        vc0.setAdMode(false);

        // Generate 30 TC frames: FIRST, CONTINUE, LAST
        for (int i = 0; i < 10; ++i) {
            vc0.dispatch(true, 44, new byte[vc0.getMaxUserDataLength() * 3]);
        }
        //
        // Checks
        assertEquals(30, list.size());
        for (int i = 0; i < 30; ++i) {
            TcTransferFrame fr = list.get(i);
            assertFalse(fr.isBypassFlag());
            assertFalse(fr.isControlCommandFlag());
            byte[] segment = fr.getDataFieldCopy();
            assertEquals(44, fr.getMapId());
            if(i % 3 == 0) {
                assertEquals(TcTransferFrame.SequenceFlagType.FIRST, fr.getSequenceFlag());
                assertEquals(vc0.getMaxUserDataLength() + 1, segment.length); // it is a segment, so +1 for the segment header
            } else if(i % 3 == 1) {
                assertEquals(TcTransferFrame.SequenceFlagType.CONTINUE, fr.getSequenceFlag());
                assertEquals(vc0.getMaxUserDataLength() + 1, segment.length); // same as above
            } else if(i % 3 == 2) {
                assertEquals(TcTransferFrame.SequenceFlagType.LAST, fr.getSequenceFlag());
                assertEquals(vc0.getMaxUserDataLength() + 1, segment.length); // same as above
            }
        }
    }
}