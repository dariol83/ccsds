/*
 * Copyright 2026 Dario Lucia (https://www.dariolucia.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver;

import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AbstractSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AosSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TmSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.builder.EncapsulationPacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.pdu.EncapsulationPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncapsulationPacketBoundaryTest {

    private static final int PACKET_DATA_LENGTH = 16;

    /**
     * Headers may be split across frames or end exactly at a packet data field boundary,
     * including packets shorter than the maximum primary header length of eight bytes.
     */
    @Test
    void testShortEncapsulationPacketReconstructionAtFrameBoundary() {
        List<Executable> layouts = new ArrayList<>();
        for (boolean aos : new boolean[] {false, true}) {
            EncapsulationPacket idle = EncapsulationPacketBuilder.create().setIdle().build();
            addLayout(layouts, aos, idle, 1);
            addLayout(layouts, aos, idle, 2);

            // Explicit-length idle packets and non-idle packets follow the same reconstruction path.
            for (int lengthOfLength = 1; lengthOfLength <= 3; ++lengthOfLength) {
                int headerLength = 1 << lengthOfLength;
                for (int packetLength = headerLength; packetLength <= 8; ++packetLength) {
                    EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                            .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC)
                            .setLengthOfLength(lengthOfLength)
                            .setData(payload(packetLength - headerLength))
                            .build();
                    // Every header split, a header ending at the boundary, and header + one byte.
                    for (int available = 1; available <= headerLength + 1; ++available) {
                        addLayout(layouts, aos, packet, available);
                    }
                }
            }
            EncapsulationPacket explicitLengthIdle = EncapsulationPacketBuilder.create()
                    .setIdle().setLengthOfLength(1).setData(payload(1)).build();
            addLayout(layouts, aos, explicitLengthIdle, 2);
            addLayout(layouts, aos, explicitLengthIdle, 3);
        }
        assertAll("TM/AOS encapsulation packet boundary layouts", layouts);
    }

    private static void addLayout(List<Executable> layouts, boolean aos, EncapsulationPacket packet,
                                  int available) {
        String description = (aos ? "AOS" : "TM") + ", packet length=" + packet.getPacket().length
                + ", header length=" + packet.getPrimaryHeaderLength() + ", idle=" + packet.isIdle()
                + ", bytes before boundary=" + available;
        layouts.add(() -> assertAll(description, () -> {
            if (aos) {
                verifyLayout(new AosSenderVirtualChannel(1, 0, VirtualChannelAccessMode.ENCAPSULATION,
                                false, PACKET_DATA_LENGTH + 8, null),
                        new AosReceiverVirtualChannel(0, VirtualChannelAccessMode.ENCAPSULATION, true),
                        AosTransferFrame.class, packet, available);
            } else {
                verifyLayout(new TmSenderVirtualChannel(1, 0, VirtualChannelAccessMode.ENCAPSULATION,
                                false, PACKET_DATA_LENGTH + 6, () -> 0, null),
                        new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.ENCAPSULATION, true),
                        TmTransferFrame.class, packet, available);
            }
        }));
    }

    private static <T extends AbstractTransferFrame> void verifyLayout(AbstractSenderVirtualChannel<T> sender,
            AbstractReceiverVirtualChannel<T> receiver, Class<T> frameType, EncapsulationPacket packet,
            int available) {
        EncapsulationPacket prefix = dataPacket(PACKET_DATA_LENGTH - available);
        EncapsulationPacket suffix = dataPacket(2 * PACKET_DATA_LENGTH
                - prefix.getPacket().length - packet.getPacket().length);
        List<T> frames = new ArrayList<>();
        sender.register((vc, frame, bufferedBytes) -> frames.add(frameType.cast(frame)));
        sender.dispatch(prefix, packet, suffix);
        assertEquals(2, frames.size());
        assertEquals(0, frames.get(0).getVirtualChannelFrameCount());
        assertEquals(1, frames.get(1).getVirtualChannelFrameCount());
        byte[] firstData = frames.get(0) instanceof AosTransferFrame
                ? ((AosTransferFrame) frames.get(0)).getPacketZoneCopy() : frames.get(0).getDataFieldCopy();
        assertEquals(PACKET_DATA_LENGTH, firstData.length);
        assertArrayEquals(Arrays.copyOf(packet.getPacket(), Math.min(available, packet.getPacket().length)),
                Arrays.copyOfRange(firstData, PACKET_DATA_LENGTH - available,
                        PACKET_DATA_LENGTH - available + Math.min(available, packet.getPacket().length)));

        List<byte[]> extracted = new ArrayList<>();
        List<AbstractTransferFrame> firstFrames = new ArrayList<>();
        receiver.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void encapsulationPacketExtracted(AbstractReceiverVirtualChannel vc,
                    AbstractTransferFrame firstFrame, byte[] data, boolean qualityIndicator) {
                assertTrue(qualityIndicator);
                extracted.add(data);
                firstFrames.add(firstFrame);
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc,
                                    int missingFrames) {
                fail("The frames are consecutive; reconstruction must not encounter a gap");
            }
        });
        assertDoesNotThrow(() -> receiver.accept(frames.get(0)));
        assertEquals(packet.getPacket().length <= available ? 2 : 1, extracted.size(),
                "Every packet completed in the first frame must be emitted immediately");
        assertArrayEquals(prefix.getPacket(), extracted.get(0));
        assertDoesNotThrow(() -> receiver.accept(frames.get(1)));
        assertEquals(3, extracted.size());
        assertArrayEquals(packet.getPacket(), extracted.get(1));
        assertArrayEquals(suffix.getPacket(), extracted.get(2));
        assertSame(frames.get(0), firstFrames.get(0));
        assertSame(frames.get(0), firstFrames.get(1));
        assertSame(frames.get(prefix.getPacket().length + packet.getPacket().length < PACKET_DATA_LENGTH
                ? 0 : 1), firstFrames.get(2));
    }

    private static EncapsulationPacket dataPacket(int length) {
        return EncapsulationPacketBuilder.create()
                .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC)
                .setLengthOfLength(1).setData(payload(length - 2)).build();
    }

    private static byte[] payload(int length) {
        byte[] data = new byte[length];
        Arrays.fill(data, (byte) 0x5A);
        return data;
    }
}
