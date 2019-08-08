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

package eu.dariolucia.ccsds.inspector.manager;

import eu.dariolucia.ccsds.inspector.api.IConnector;
import eu.dariolucia.ccsds.inspector.api.SeverityEnum;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.*;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SpacePacketExtractor {

    private final Map<Integer, AbstractReceiverVirtualChannel<?>> id2vc = new ConcurrentHashMap<>();

    private final Consumer<SpacePacket> packetSink;
    private final BiConsumer<SeverityEnum, String> messageSink;

    public SpacePacketExtractor(Consumer<SpacePacket> packetSink, BiConsumer<SeverityEnum, String> messageSink) {
        this.packetSink = packetSink;
        this.messageSink = messageSink;
    }

    @SuppressWarnings("unchecked")
    public void process(AbstractTransferFrame atf) {
        // Map type to byte (internal representation)
        byte type = mapType(atf); // 0: TM, 1: TC, 2: AOS
        byte vcId = (byte) atf.getVirtualChannelId(); // In AOS it is 6 bits, so OK
        short scId = atf.getSpacecraftId();

        int key = type;
        key <<= 8;
        key |= (int) vcId;
        key <<= 16;
        key |= (int) scId;

        AbstractReceiverVirtualChannel channel = id2vc.get(key);
        if (channel == null) {
            AbstractReceiverVirtualChannel newChannel = buildChannel(atf);
            channel = id2vc.putIfAbsent(key, newChannel);
            if (channel == null) {
                channel = newChannel;
                channel.register(new VirtualChannelObserver(scId, packetSink, messageSink));
            }
        }

        channel.processFrame(atf);
    }

    private AbstractReceiverVirtualChannel buildChannel(AbstractTransferFrame atf) {
        if (atf instanceof TmTransferFrame) {
            TmTransferFrame ttf = (TmTransferFrame) atf;
            return new TmReceiverVirtualChannel(ttf.getVirtualChannelId(), VirtualChannelAccessMode.Packet, true);
        } else if (atf instanceof AosTransferFrame) {
            AosTransferFrame ttf = (AosTransferFrame) atf;
            return new AosReceiverVirtualChannel(ttf.getVirtualChannelId(), VirtualChannelAccessMode.Packet, true);
        } else if (atf instanceof TcTransferFrame) {
            TcTransferFrame ttf = (TcTransferFrame) atf;
            return new TcReceiverVirtualChannel(ttf.getVirtualChannelId(), VirtualChannelAccessMode.Packet, true, ttf.getSecurityHeaderLength(), ttf.getSecurityTrailerLength());
        }
        throw new IllegalArgumentException("Frame type " + atf.getClass() + " not supported");
    }

    private byte mapType(AbstractTransferFrame atf) {
        if (atf instanceof TmTransferFrame) {
            return 0x00;
        } else if (atf instanceof TcTransferFrame) {
            return 0x01;
        } else if (atf instanceof AosTransferFrame) {
            return 0x02;
        } else {
            throw new IllegalArgumentException("Frame type " + atf.getClass() + " not supported");
        }
    }

    public void dispose() {
        this.id2vc.clear();
    }

    private class VirtualChannelObserver implements IVirtualChannelReceiverOutput {

        private final short scId;

        private final Consumer<SpacePacket> packetSink;
        private final BiConsumer<SeverityEnum, String> messageSink;

        public VirtualChannelObserver(short scId, Consumer<SpacePacket> packetSink, BiConsumer<SeverityEnum, String> messageSink) {
            this.scId = scId;
            this.packetSink = packetSink;
            this.messageSink = messageSink;
        }

        @Override
        public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
            // Ignore
        }

        @Override
        public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame lastFrame, byte[] packet, boolean qualityIndicator) {
            try {
                SpacePacket spp = new SpacePacket(packet, qualityIndicator);
                spp.setAnnotationValue(IConnector.ANNOTATION_TIME_KEY, Instant.now());
                spp.setAnnotationValue(ConnectorManager.ANNOTATION_SCID_KEY, (int) scId);
                spp.setAnnotationValue(ConnectorManager.ANNOTATION_VCID_KEY, vc.getVirtualChannelId());
                packetSink.accept(spp);
            } catch(Exception e) {
                messageSink.accept(SeverityEnum.ALARM, "Packet reconstruction error on S/C ID " + scId + ", VCID " + vc.getVirtualChannelId() + ": " + e.getMessage());
            }
        }

        @Override
        public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
            // Ignore
        }

        @Override
        public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
            // Ignore
        }

        @Override
        public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
            messageSink.accept(SeverityEnum.WARNING, "Frame gap detected on S/C ID " + scId
                    + ", virtual channel " + vc.getVirtualChannelId()
                    + ": expected VC count " + expectedVc
                    + ", received " + receivedVc
                    + ", missed " + missingFrames + " frame(s)");
        }
    }
}
