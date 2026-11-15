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
import eu.dariolucia.ccsds.tmtc.transport.builder.SpacePacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpacePacketBoundaryTest {

    private static final int PACKET_DATA_LENGTH = 256;

    @Test
    void testSpacePacketHeaderAtFrameBoundaryWithAndWithoutFrameGap() {
        List<Executable> layouts = new ArrayList<>();
        for (boolean aos : new boolean[] {false, true}) {
            for (int available : new int[] {5, 6, 7}) {
                for (boolean skipFrame : new boolean[] {false, true}) {
                    String description = (aos ? "AOS" : "TM") + ", bytes before boundary=" + available
                            + ", missing middle frame=" + skipFrame;
                    layouts.add(() -> assertAll(description, () -> {
                        if (aos) {
                            verifyLayout(new AosSenderVirtualChannel(1, 0, VirtualChannelAccessMode.PACKET,
                                            false, PACKET_DATA_LENGTH + 8, null),
                                    new AosReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, true),
                                    AosTransferFrame.class, available, skipFrame);
                        } else {
                            verifyLayout(new TmSenderVirtualChannel(1, 0, VirtualChannelAccessMode.PACKET,
                                            false, PACKET_DATA_LENGTH + 6, () -> 0, null),
                                    new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.PACKET, true),
                                    TmTransferFrame.class, available, skipFrame);
                        }
                    }));
                }
            }
        }
        assertAll("TM/AOS space packet header boundary layouts", layouts);
    }

    private static <T extends AbstractTransferFrame> void verifyLayout(AbstractSenderVirtualChannel<T> sender,
            AbstractReceiverVirtualChannel<T> receiver, Class<T> frameType, int available, boolean skipFrame) {
        SpacePacket prefix = packet(PACKET_DATA_LENGTH - available, 1);
        SpacePacket target = packet(16, 2);
        SpacePacket middle = packet(PACKET_DATA_LENGTH - (target.getLength() - available), 3);
        SpacePacket last = packet(PACKET_DATA_LENGTH, 4);
        List<T> frames = new ArrayList<>();
        sender.register((vc, frame, bufferedBytes) -> frames.add(frameType.cast(frame)));
        sender.dispatch(prefix, target, middle, last);
        assertEquals(3, frames.size());
        for (int i = 0; i < frames.size(); ++i) {
            assertEquals(i, frames.get(i).getVirtualChannelFrameCount());
        }
        byte[] firstData = frames.get(0) instanceof AosTransferFrame
                ? ((AosTransferFrame) frames.get(0)).getPacketZoneCopy() : frames.get(0).getDataFieldCopy();
        assertEquals(PACKET_DATA_LENGTH, firstData.length);
        assertArrayEquals(Arrays.copyOf(target.getPacket(), available),
                Arrays.copyOfRange(firstData, PACKET_DATA_LENGTH - available, PACKET_DATA_LENGTH));

        List<ExtractedPacket> extracted = new ArrayList<>();
        List<int[]> frameGaps = new ArrayList<>();
        receiver.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame,
                    byte[] data, boolean qualityIndicator, List<PacketGap> gaps) {
                extracted.add(new ExtractedPacket(firstFrame, data, qualityIndicator, gaps));
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc,
                    int missingFrames) {
                frameGaps.add(new int[] {expectedVc, receivedVc, missingFrames});
            }
        });
        assertDoesNotThrow(() -> receiver.accept(frames.get(0)));
        assertEquals(1, extracted.size());
        assertGoodPacket(extracted.get(0), prefix, frames.get(0));

        if (!skipFrame) {
            assertDoesNotThrow(() -> receiver.accept(frames.get(1)));
            assertEquals(3, extracted.size());
            assertGoodPacket(extracted.get(1), target, frames.get(0));
            assertGoodPacket(extracted.get(2), middle, frames.get(1));
            assertDoesNotThrow(() -> receiver.accept(frames.get(2)));
            assertEquals(4, extracted.size());
            assertGoodPacket(extracted.get(3), last, frames.get(2));
            assertTrue(frameGaps.isEmpty());
        } else {
            assertDoesNotThrow(() -> receiver.accept(frames.get(2)));
            assertEquals(1, frameGaps.size());
            assertArrayEquals(new int[] {1, 2, 1}, frameGaps.get(0));
            if (available < SpacePacket.SP_PRIMARY_HEADER_LENGTH) {
                // A gap within the header leaves the packet length unknown: discard it.
                assertEquals(2, extracted.size());
            } else {
                // A complete header provides the length even when no payload byte has arrived.
                assertEquals(3, extracted.size());
                ExtractedPacket incomplete = extracted.get(1);
                assertSame(frames.get(0), incomplete.firstFrame);
                assertFalse(incomplete.goodQuality);
                assertEquals(target.getLength(), incomplete.data.length);
                assertArrayEquals(Arrays.copyOf(target.getPacket(), available),
                        Arrays.copyOf(incomplete.data, available));
                assertEquals(1, incomplete.gaps.size());
                assertEquals(available, incomplete.gaps.get(0).getIndex());
                assertEquals(target.getLength() - available, incomplete.gaps.get(0).getLength());
            }
            assertGoodPacket(extracted.get(extracted.size() - 1), last, frames.get(2));
        }
    }

    private static void assertGoodPacket(ExtractedPacket actual, SpacePacket expected,
            AbstractTransferFrame firstFrame) {
        assertSame(firstFrame, actual.firstFrame);
        assertArrayEquals(expected.getPacket(), actual.data);
        assertTrue(actual.goodQuality);
        assertTrue(actual.gaps.isEmpty());
    }

    private static SpacePacket packet(int length, int sequenceCount) {
        byte[] payload = new byte[length - SpacePacket.SP_PRIMARY_HEADER_LENGTH];
        Arrays.fill(payload, (byte) (sequenceCount * 17));
        SpacePacketBuilder builder = SpacePacketBuilder.create().setTelemetryPacket().setApid(100)
                .setPacketSequenceCount(sequenceCount);
        assertEquals(0, builder.addData(payload));
        return builder.build();
    }

    private static final class ExtractedPacket {
        private final AbstractTransferFrame firstFrame;
        private final byte[] data;
        private final boolean goodQuality;
        private final List<PacketGap> gaps;

        private ExtractedPacket(AbstractTransferFrame firstFrame, byte[] data, boolean goodQuality,
                List<PacketGap> gaps) {
            this.firstFrame = firstFrame;
            this.data = data;
            this.goodQuality = goodQuality;
            this.gaps = gaps;
        }
    }
}
