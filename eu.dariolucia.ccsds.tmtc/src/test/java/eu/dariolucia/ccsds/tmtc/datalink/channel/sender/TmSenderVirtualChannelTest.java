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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender;

import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.mux.TmMasterChannelMuxer;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.builder.ClcwBuilder;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.AbstractOcf;
import eu.dariolucia.ccsds.tmtc.transport.builder.EncapsulationPacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.builder.SpacePacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;
import eu.dariolucia.ccsds.tmtc.transport.pdu.EncapsulationPacket;
import eu.dariolucia.ccsds.tmtc.transport.pdu.IPacket;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TmSenderVirtualChannelTest {

    @Test
    public void testPullModeSpacePacket() {
        // Create a sink consumer
        List<TmTransferFrame> list = new LinkedList<>();
        Consumer<TmTransferFrame> sink = list::add;
        // Setup the muxer
        TmMasterChannelMuxer mux = new TmMasterChannelMuxer(sink);
        // Data supplier
        IVirtualChannelDataProvider dataProvider = new IVirtualChannelDataProvider() {
            int vc0counter = 0;
            int vc1counter = 0;

            @Override
            public List<IPacket> generateSpacePackets(int virtualChannelId, int availableSpaceInCurrentFrame, int maxNumBytesBeforeOverflow) {
                if (virtualChannelId == 0) {
                    ++vc0counter;
                }
                if (virtualChannelId == 1) {
                    ++vc1counter;
                }
                if (virtualChannelId == 0 && vc0counter % 5 != 0) {
                    ++vc0counter;
                    return generateSpacePacketList(availableSpaceInCurrentFrame, maxNumBytesBeforeOverflow);
                } else if (virtualChannelId == 1 && vc1counter % 3 != 0) {
                    ++vc1counter;
                    return generateSpacePacketList(availableSpaceInCurrentFrame, maxNumBytesBeforeOverflow);
                } else {
                    return null;
                }
            }

            @Override
            public BitstreamData generateBitstreamData(int virtualChannelId, int availableSpaceInCurrentFrame) {
                return null;
            }

            @Override
            public byte[] generateData(int virtualChannelId, int availableSpaceInCurrentFrame) {
                return null;
            }
        };
        // Setup the VCs (0, 1 and 7 for idle frames)
        TmSenderVirtualChannel vc0 = new TmSenderVirtualChannel(123, 0, VirtualChannelAccessMode.PACKET, false, 1115, mux::getNextCounter, this::ocfSupplier, dataProvider);
        TmSenderVirtualChannel vc1 = new TmSenderVirtualChannel(123, 1, VirtualChannelAccessMode.PACKET, false, 1115, mux::getNextCounter, this::ocfSupplier, dataProvider);
        TmSenderVirtualChannel vc7 = new TmSenderVirtualChannel(123, 7, VirtualChannelAccessMode.PACKET, false, 1115, mux::getNextCounter, this::ocfSupplier);

        assertNotNull(vc0.getOcfSupplier());
        assertNull(vc0.getSecondaryHeaderSupplier());
        //
        vc0.register(mux);
        vc1.register(mux);
        vc7.register(mux);
        // Generation logic: generate data from VC0 if it has data. In any case, after 10 VC0 frames, generate a VC1
        // frame if it has data. If VC1 has no data, generate an idle frame on VC7. If VC0 has no data, check VC1 and
        // if no data, send idle frame on VC7.
        int vc0frames = 0;
        // Generate 300 frames overall
        for (int i = 0; i < 300; ++i) {
            if (vc0frames < 10) {
                boolean vc0generated = vc0.pullNextFrame();
                if (!vc0generated) {
                    boolean vc1generated = vc1.pullNextFrame();
                    if (!vc1generated) {
                        vc7.dispatchIdle(new byte[]{0x55});
                    }
                } else {
                    ++vc0frames;
                }
            } else {
                boolean vc1generated = vc1.pullNextFrame();
                if (!vc1generated) {
                    vc7.dispatchIdle(new byte[]{0x55});
                }
                vc0frames = 0;
            }
        }
        //
        assertEquals(300, list.size());
        assertEquals(0, list.get(0).getVirtualChannelId());
        assertEquals(0, list.get(1).getVirtualChannelId());
        assertEquals(1, list.get(2).getVirtualChannelId());
        assertEquals(0, list.get(3).getVirtualChannelId());
        assertEquals(7, list.get(4).getVirtualChannelId());

        // Dispatch bitstream -> exception
        try {
            vc0.dispatch(new BitstreamData(new byte[20], 12));
            fail("UnsupportedOperationException expected");
        } catch(UnsupportedOperationException e) {
            // Good
        }

        // Dispatch user data -> exception
        try {
            vc0.dispatch(new byte[20]);
            fail("IllegalStateException expected");
        } catch(IllegalStateException e) {
            // Good
        }
    }

    private List<IPacket> generateSpacePacketList(int availableSpaceInCurrentFrame, int maxNumBytesBeforeOverflow) {
        // Considering a fixed packet data size of 400, use the following approach:
        // - if availableSpaceInCurrentFrame is > 800, generate 3 packets
        // - if availableSpaceInCurrentFrame is < 800, generate 1 packet
        List<IPacket> packets = new LinkedList<>(generateSpacePackets(1));
        if (availableSpaceInCurrentFrame > 800) {
            packets.addAll(generateSpacePackets(2));
        }
        return packets;
    }

    private List<IPacket> generateSpacePackets(int n) {
        return generateSpacePackets(n, 400);
    }

    private List<IPacket> generateSpacePackets(int n, int bodySize) {
        SpacePacketBuilder spp = SpacePacketBuilder.create()
                .setApid(200)
                .setQualityIndicator(true)
                .setSecondaryHeaderFlag(false)
                .setTelemetryPacket();
        spp.addData(new byte[bodySize]);
        List<IPacket> toReturn = new LinkedList<>();
        for (int i = 0; i < n; ++i) {
            spp.setPacketSequenceCount(i % 16384);
            toReturn.add(spp.build());
        }
        return toReturn;
    }

    private AbstractOcf ocfSupplier(int vcId) {
        return ClcwBuilder.create()
                .setCopInEffect(false)
                .setFarmBCounter(2)
                .setLockoutFlag(false)
                .setNoBitlockFlag(true)
                .setNoRfAvailableFlag(false)
                .setReportValue(121)
                .setRetransmitFlag(false)
                .setVirtualChannelId(1)
                .build();
    }

    @Test
    public void testPullModeUserData() {
        // Create a sink consumer
        List<TmTransferFrame> list = new LinkedList<>();
        Consumer<TmTransferFrame> sink = list::add;
        // Setup the muxer
        TmMasterChannelMuxer mux = new TmMasterChannelMuxer(sink);
        // Data supplier
        IVirtualChannelDataProvider dataProvider = new IVirtualChannelDataProvider() {
            int vc0counter = 0;
            int vc1counter = 0;

            @Override
            public List<IPacket> generateSpacePackets(int virtualChannelId, int availableSpaceInCurrentFrame, int maxNumBytesBeforeOverflow) {
                return null;
            }

            @Override
            public BitstreamData generateBitstreamData(int virtualChannelId, int availableSpaceInCurrentFrame) {
                return null;
            }

            @Override
            public byte[] generateData(int virtualChannelId, int availableSpaceInCurrentFrame) {
                if (virtualChannelId == 0) {
                    ++vc0counter;
                    return new byte[Math.min(600, availableSpaceInCurrentFrame)];
                } else if (virtualChannelId == 1) {
                    ++vc1counter;
                    return new byte[Math.min(400, availableSpaceInCurrentFrame)];
                } else {
                    return null;
                }
            }
        };
        // Setup the VCs (0, 1 and 7 for idle frames)
        TmSenderVirtualChannel vc0 = new TmSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, false, 1115, mux::getNextCounter, this::ocfSupplier, dataProvider);
        TmSenderVirtualChannel vc1 = new TmSenderVirtualChannel(123, 1, VirtualChannelAccessMode.DATA, false, 1115, mux::getNextCounter, this::ocfSupplier, dataProvider);
        TmSenderVirtualChannel vc7 = new TmSenderVirtualChannel(123, 7, VirtualChannelAccessMode.DATA, false, 1115, mux::getNextCounter, this::ocfSupplier);
        //
        vc0.register(mux);
        vc1.register(mux);
        vc7.register(mux);
        // Generation logic: generate data from VC0 if it has data. In any case, after 10 VC0 frame requests, generate a VC1
        // frame if it has data. If VC1 has no data, generate an idle frame on VC7. If VC0 has no data, check VC1 and
        // if no data, send idle frame on VC7.
        int vc0frames = 0;
        // Generate 300 frames overall
        for (int i = 0; i < 300; ++i) {
            if (vc0frames < 10) {
                boolean vc0generated = vc0.pullNextFrame();
                if (!vc0generated) {
                    boolean vc1generated = vc1.pullNextFrame();
                    if (!vc1generated) {
                        vc7.dispatchIdle(new byte[]{0x55});
                    }
                } else {
                    ++vc0frames;
                }
            } else {
                boolean vc1generated = vc1.pullNextFrame();
                if (!vc1generated) {
                    vc7.dispatchIdle(new byte[]{0x55});
                }
                vc0frames = 0;
            }
        }
        // Expect 300 frames, VC7, VC0, VC7, VC0, VC1 ...
        assertEquals(300, list.size());
        assertEquals(7, list.get(0).getVirtualChannelId());
        assertEquals(0, list.get(1).getVirtualChannelId());
        assertEquals(7, list.get(2).getVirtualChannelId());
        assertEquals(0, list.get(3).getVirtualChannelId());
        assertEquals(1, list.get(4).getVirtualChannelId());
    }

    @Test
    public void testPushModeUserData() {
        // Create a sink consumer
        List<TmTransferFrame> list = new LinkedList<>();
        Consumer<TmTransferFrame> sink = list::add;
        // Setup the muxer
        TmMasterChannelMuxer mux = new TmMasterChannelMuxer(sink);

        // Setup the VCs (0, 1 and 7 for idle frames)
        TmSenderVirtualChannel vc0 = new TmSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, false, 1115, mux::getNextCounter, this::ocfSupplier);
        TmSenderVirtualChannel vc1 = new TmSenderVirtualChannel(123, 1, VirtualChannelAccessMode.DATA, false, 1115, mux::getNextCounter, this::ocfSupplier);
        TmSenderVirtualChannel vc7 = new TmSenderVirtualChannel(123, 7, VirtualChannelAccessMode.DATA, false, 1115, mux::getNextCounter, this::ocfSupplier);
        //
        vc0.register(mux);
        vc1.register(mux);
        vc7.register(mux);
        // Generation logic: round robin VC0, VC1, VC7.
        // Generate 30 frames overall
        for (int i = 0; i < 30; ++i) {
            switch (i % 3) {
                case 0:
                    vc0.dispatch(new byte[vc0.getMaxUserDataLength()]);
                    break;
                case 1:
                    vc1.dispatch(new byte[vc1.getMaxUserDataLength()]);
                    break;
                case 2:
                    vc7.dispatchIdle(new byte[]{0x55});
                    break;
            }
        }
        //
        assertEquals(10, vc0.getNbOfEmittedFrames());
        assertEquals(10, vc1.getNbOfEmittedFrames());
        assertEquals(10, vc7.getNbOfEmittedFrames());
        assertEquals(30, list.size());

        // Dispatch packet -> exception
        try {
            vc0.dispatch(generateSpacePackets(1));
            fail("IllegalStateException expected");
        } catch(IllegalStateException e) {
            // Good
        }
    }

    @Test
    public void testReset() {
        // Create a sink consumer
        List<TmTransferFrame> list = new LinkedList<>();
        Consumer<TmTransferFrame> sink = list::add;
        // Setup the muxer
        TmMasterChannelMuxer mux = new TmMasterChannelMuxer(sink);
        // Setup the VCs
        TmSenderVirtualChannel vc0 = new TmSenderVirtualChannel(123, 0, VirtualChannelAccessMode.DATA, false, 1115, mux::getNextCounter, this::ocfSupplier);
        //
        vc0.register(mux);
        //
        int freeBytes = vc0.dispatch(new byte[vc0.getMaxUserDataLength() - 1]);
        //
        assertEquals(1, freeBytes);
        assertEquals(0, list.size());
        assertTrue(vc0.isPendingFramePresent());
        assertEquals(0, vc0.getNextVirtualChannelFrameCounter());
        vc0.reset();
        assertFalse(vc0.isPendingFramePresent());
        assertEquals(0, list.size());
        assertEquals(0, vc0.getNextVirtualChannelFrameCounter());
        vc0.setVirtualChannelFrameCounter(34);
        assertEquals(34, vc0.getNextVirtualChannelFrameCounter());
        vc0.dispatch(new byte[vc0.getMaxUserDataLength()]);
        assertFalse(vc0.isPendingFramePresent());
        assertEquals(1, list.size());
        assertEquals(35, vc0.getNextVirtualChannelFrameCounter());
        assertEquals(34, list.get(0).getVirtualChannelFrameCount());

        vc0.deregister(mux);
    }

    @Test
    public void testPushModeSpacePackets() {
        // Create a sink consumer
        List<TmTransferFrame> list = new LinkedList<>();
        Consumer<TmTransferFrame> sink = list::add;
        // Setup the muxer
        TmMasterChannelMuxer mux = new TmMasterChannelMuxer(sink);
        // Setup the VCs (0)
        TmSenderVirtualChannel vc0 = new TmSenderVirtualChannel(123, 0, VirtualChannelAccessMode.PACKET, false, 1115, mux::getNextCounter, this::ocfSupplier);
        //
        vc0.register(mux);


        int maxUserDataLength = vc0.getMaxUserDataLength();
        // This is in frame 0
        vc0.dispatch(generateSpacePackets(1, maxUserDataLength - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 20));
        // This is segmented on frame 0 and frame 1
        vc0.dispatch(generateSpacePackets(1, maxUserDataLength - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 500));
        // This is in frame 1
        vc0.dispatch(generateSpacePackets(1, 400));
        // This is in frame 1, frame 2, frame 3 e part of frame 4
        int remaining = vc0.dispatch(generateSpacePackets(1, 3 * maxUserDataLength));
        // This is in frame 4
        vc0.dispatch(generateSpacePackets(1, remaining - SpacePacket.SP_PRIMARY_HEADER_LENGTH));
        // This is in frame 5, one single byte remains free in frame 5
        vc0.dispatch(generateSpacePackets(1, maxUserDataLength - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 1));
        // This is in frame 5 and frame 6
        remaining = vc0.dispatch(generateSpacePackets(1, maxUserDataLength - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 400));
        // This is in frame 6, 2 bytes free in frame 6
        vc0.dispatch(generateSpacePackets(1, remaining - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 2));
        // This is in frame 6, 7 and 8
        remaining = vc0.dispatch(generateSpacePackets(1, maxUserDataLength + 200 - SpacePacket.SP_PRIMARY_HEADER_LENGTH));
        // This is in frame 8, closed
        vc0.dispatch(generateSpacePackets(1, remaining - SpacePacket.SP_PRIMARY_HEADER_LENGTH));

        assertEquals(9, list.size());

        // list.stream().map(TmTransferFrame::getFrame).map(StringUtil::toHexDump).forEach(System.out::println);
    }

    @Test
    public void testPullModeEncapsulationPacket() {
        // Create a sink consumer
        List<TmTransferFrame> list = new LinkedList<>();
        Consumer<TmTransferFrame> sink = list::add;
        // Setup the muxer
        TmMasterChannelMuxer mux = new TmMasterChannelMuxer(sink);
        // Data supplier
        IVirtualChannelDataProvider dataProvider = new IVirtualChannelDataProvider() {
            int vc0counter = 0;
            int vc1counter = 0;

            @Override
            public List<IPacket> generateSpacePackets(int virtualChannelId, int availableSpaceInCurrentFrame, int maxNumBytesBeforeOverflow) {
                if (virtualChannelId == 0) {
                    ++vc0counter;
                }
                if (virtualChannelId == 1) {
                    ++vc1counter;
                }
                if (virtualChannelId == 0 && vc0counter % 5 != 0) {
                    ++vc0counter;
                    return generateEncapsulationPacketList(availableSpaceInCurrentFrame, maxNumBytesBeforeOverflow);
                } else if (virtualChannelId == 1 && vc1counter % 3 != 0) {
                    ++vc1counter;
                    return generateEncapsulationPacketList(availableSpaceInCurrentFrame, maxNumBytesBeforeOverflow);
                } else {
                    return null;
                }
            }

            @Override
            public BitstreamData generateBitstreamData(int virtualChannelId, int availableSpaceInCurrentFrame) {
                return null;
            }

            @Override
            public byte[] generateData(int virtualChannelId, int availableSpaceInCurrentFrame) {
                return null;
            }
        };
        // Setup the VCs (0, 1 and 7 for idle frames)
        TmSenderVirtualChannel vc0 = new TmSenderVirtualChannel(123, 0, VirtualChannelAccessMode.ENCAPSULATION, false, 1115, mux::getNextCounter, this::ocfSupplier, dataProvider);
        TmSenderVirtualChannel vc1 = new TmSenderVirtualChannel(123, 1, VirtualChannelAccessMode.ENCAPSULATION, false, 1115, mux::getNextCounter, this::ocfSupplier, dataProvider);
        TmSenderVirtualChannel vc7 = new TmSenderVirtualChannel(123, 7, VirtualChannelAccessMode.ENCAPSULATION, false, 1115, mux::getNextCounter, this::ocfSupplier);

        assertNotNull(vc0.getOcfSupplier());
        assertNull(vc0.getSecondaryHeaderSupplier());
        //
        vc0.register(mux);
        vc1.register(mux);
        vc7.register(mux);
        // Generation logic: generate data from VC0 if it has data. In any case, after 10 VC0 frames, generate a VC1
        // frame if it has data. If VC1 has no data, generate an idle frame on VC7. If VC0 has no data, check VC1 and
        // if no data, send idle frame on VC7.
        int vc0frames = 0;
        // Generate 300 frames overall
        for (int i = 0; i < 300; ++i) {
            if (vc0frames < 10) {
                boolean vc0generated = vc0.pullNextFrame();
                if (!vc0generated) {
                    boolean vc1generated = vc1.pullNextFrame();
                    if (!vc1generated) {
                        vc7.dispatchIdle(new byte[]{0x55});
                    }
                } else {
                    ++vc0frames;
                }
            } else {
                boolean vc1generated = vc1.pullNextFrame();
                if (!vc1generated) {
                    vc7.dispatchIdle(new byte[]{0x55});
                }
                vc0frames = 0;
            }
        }
        //
        assertEquals(300, list.size());
        assertEquals(0, list.get(0).getVirtualChannelId());
        assertEquals(0, list.get(1).getVirtualChannelId());
        assertEquals(1, list.get(2).getVirtualChannelId());
        assertEquals(0, list.get(3).getVirtualChannelId());
        assertEquals(7, list.get(4).getVirtualChannelId());

        // Dispatch bitstream -> exception
        try {
            vc0.dispatch(new BitstreamData(new byte[20], 12));
            fail("UnsupportedOperationException expected");
        } catch(UnsupportedOperationException e) {
            // Good
        }

        // Dispatch user data -> exception
        try {
            vc0.dispatch(new byte[20]);
            fail("IllegalStateException expected");
        } catch(IllegalStateException e) {
            // Good
        }
    }

    private List<IPacket> generateEncapsulationPacketList(int availableSpaceInCurrentFrame, int maxNumBytesBeforeOverflow) {
        // Considering a fixed packet data size of 400, use the following approach:
        // - if availableSpaceInCurrentFrame is > 800, generate 3 packets
        // - if availableSpaceInCurrentFrame is < 800, generate 1 packet
        List<IPacket> packets = new LinkedList<>(generateEncapsulationPackets(1));
        if (availableSpaceInCurrentFrame > 800) {
            packets.addAll(generateEncapsulationPackets(2));
        }
        return packets;
    }

    private List<IPacket> generateEncapsulationPackets(int n) {
        return generateEncapsulationPackets(n, 400);
    }

    private List<IPacket> generateEncapsulationPackets(int n, int bodySize) {
        EncapsulationPacketBuilder spp = EncapsulationPacketBuilder.create()
                .setQualityIndicator(true)
                .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC);
        spp.setData(new byte[bodySize]);
        List<IPacket> toReturn = new LinkedList<>();
        for (int i = 0; i < n; ++i) {
            toReturn.add(spp.build());
        }
        return toReturn;
    }


    @Test
    public void testPushModeEncapsulationPackets() {
        // Create a sink consumer
        List<TmTransferFrame> list = new LinkedList<>();
        Consumer<TmTransferFrame> sink = list::add;
        // Setup the muxer
        TmMasterChannelMuxer mux = new TmMasterChannelMuxer(sink);
        // Setup the VCs (0)
        TmSenderVirtualChannel vc0 = new TmSenderVirtualChannel(123, 0, VirtualChannelAccessMode.ENCAPSULATION, false, 1115, mux::getNextCounter, this::ocfSupplier);
        //
        vc0.register(mux);

        int maxUserDataLength = vc0.getMaxUserDataLength();
        // This is in frame 0
        vc0.dispatch(generateEncapsulationPackets(1, maxUserDataLength - 4 - 20));
        // This is segmented on frame 0 and frame 1
        vc0.dispatch(generateEncapsulationPackets(1, maxUserDataLength - 4 - 500));
        // This is in frame 1
        vc0.dispatch(generateEncapsulationPackets(1, 400));
        // This is in frame 1, frame 2, frame 3 e part of frame 4
        int remaining = vc0.dispatch(generateEncapsulationPackets(1, 3 * maxUserDataLength));
        // This is in frame 4
        vc0.dispatch(generateEncapsulationPackets(1, remaining - 2)); // remaining is < 255
        // This is in frame 5, one single byte remains free in frame 5
        vc0.dispatch(generateEncapsulationPackets(1, maxUserDataLength - 4 - 1));
        // This is in frame 5 and frame 6
        remaining = vc0.dispatch(generateEncapsulationPackets(1, maxUserDataLength - 4 - 400));
        // This is in frame 6, 2 bytes free in frame 6
        vc0.dispatch(generateEncapsulationPackets(1, remaining - 4 - 2));
        // This is in frame 6, 7 and 8
        remaining = vc0.dispatch(generateEncapsulationPackets(1, maxUserDataLength + 200 - 4));
        // This is in frame 8
        vc0.dispatch(generateEncapsulationPackets(1, remaining - 4));
        // This is in frame 9 from start, idle encapsulation packet, 1 byte
        vc0.dispatch(generateIdleEncapsulationPackets(1, 0));
        // This is in frame 9, idle encapsulation packet, not complete frame 9 (3 bytes missing)
        vc0.dispatch(generateIdleEncapsulationPackets(1, maxUserDataLength - 4 - 1 - 3));
        // This is in frame 10, idle encapsulation packet with special info, complete frame 10
        vc0.dispatch(generateIdleEncapsulationPacketsWithInfo(1, maxUserDataLength + 3 - 8));

        // list.stream().map(TmTransferFrame::getFrame).map(StringUtil::toHexDump).forEach(System.out::println);

        assertEquals(11, list.size());
    }

    private List<IPacket> generateIdleEncapsulationPacketsWithInfo(int n, int bodySize) {
        EncapsulationPacketBuilder spp = EncapsulationPacketBuilder.create()
                .setQualityIndicator(true)
                .setIdle()
                .setCcsdsDefinedField(new byte[] { 0x12, 0x34 });
        if(bodySize == 0) {
            spp.clearData();
        } else {
            spp.setData(new byte[bodySize]);
        }
        List<IPacket> toReturn = new LinkedList<>();
        for (int i = 0; i < n; ++i) {
            toReturn.add(spp.build());
        }
        return toReturn;
    }

    private List<IPacket> generateIdleEncapsulationPackets(int n, int bodySize) {
        EncapsulationPacketBuilder spp = EncapsulationPacketBuilder.create()
                .setQualityIndicator(true)
                .setIdle();
        if(bodySize == 0) {
            spp.clearData();
        } else {
            spp.setData(new byte[bodySize]);
        }
        List<IPacket> toReturn = new LinkedList<>();
        for (int i = 0; i < n; ++i) {
            toReturn.add(spp.build());
        }
        return toReturn;
    }
}