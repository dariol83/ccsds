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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncapsulationPacketFragmentedHeaderTest {

    @Test
    void testEncapsulationHeadersSpanningMultipleFrames() {
        assertAll("TM/AOS headers fragmented over small packet data fields",
                () -> verifyFragments(new TmSenderVirtualChannel(1, 0,
                                VirtualChannelAccessMode.ENCAPSULATION, true, 10, () -> 0, null),
                        new TmReceiverVirtualChannel(0, VirtualChannelAccessMode.ENCAPSULATION, true),
                        TmTransferFrame.class),
                () -> verifyFragments(new AosSenderVirtualChannel(1, 0,
                                VirtualChannelAccessMode.ENCAPSULATION, true, 12, null),
                        new AosReceiverVirtualChannel(0, VirtualChannelAccessMode.ENCAPSULATION, true),
                        AosTransferFrame.class));
    }

    private static <T extends AbstractTransferFrame> void verifyFragments(AbstractSenderVirtualChannel<T> sender,
            AbstractReceiverVirtualChannel<T> receiver, Class<T> frameType) {
        EncapsulationPacket[] packets = {
                dataPacket(3, new byte[] {0x31, 0x32}),
                dataPacket(2, new byte[0]),
                dataPacket(1, new byte[] {0x41}),
                EncapsulationPacketBuilder.create().setIdle().build()
        };
        List<T> frames = new ArrayList<>();
        sender.register((vc, frame, bufferedBytes) -> frames.add(frameType.cast(frame)));
        sender.dispatch(packets);
        assertEquals(9, frames.size());
        assertEquals(2, sender.getMaxUserDataLength());

        List<byte[]> extracted = new ArrayList<>();
        int[] firstFrameIndices = {0, 5, 7, 8};
        receiver.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void encapsulationPacketExtracted(AbstractReceiverVirtualChannel vc,
                    AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator,
                    List<PacketGap> gaps) {
                int index = extracted.size();
                assertTrue(index < packets.length, "No duplicate packet notifications expected");
                assertSame(frames.get(firstFrameIndices[index]), firstFrame);
                assertTrue(qualityIndicator);
                assertTrue(gaps.isEmpty());
                assertArrayEquals(packets[index].getPacket(), packet);
                extracted.add(packet);
            }

            @Override
            public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc,
                    int missingFrames) {
                fail("Consecutive frames must not report a gap");
            }
        });

        // The eight-byte header spans frames 0 to 3. FECF bytes must never enter the reconstructed header.
        // The header-only packet completes in frame 6; the final idle occupies frame 8's last data byte.
        int[] emittedAfterFrame = {0, 0, 0, 0, 1, 1, 2, 2, 4};
        for (int i = 0; i < frames.size(); ++i) {
            T frame = frames.get(i);
            assertEquals(i, frame.getVirtualChannelFrameCount());
            assertTrue(frame.isFecfPresent());
            assertTrue(frame.isValid());
            assertDoesNotThrow(() -> receiver.accept(frame));
            assertEquals(emittedAfterFrame[i], extracted.size(), "Packets emitted after frame " + i);
        }
    }

    private static EncapsulationPacket dataPacket(int lengthOfLength, byte[] payload) {
        return EncapsulationPacketBuilder.create()
                .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC)
                .setLengthOfLength(lengthOfLength).setData(payload).build();
    }
}
