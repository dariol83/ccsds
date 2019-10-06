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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.function;

import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper used to convert frames to a list of {@link SpacePacket}.
 *
 * @param <T> the source frame type
 */
public class VirtualChannelReceiverSpacePacketMapper<T extends AbstractTransferFrame> extends AbstractVirtualChannelReceiverMapper<T, List<SpacePacket>> {

    public VirtualChannelReceiverSpacePacketMapper(AbstractReceiverVirtualChannel<T> virtualChannel) {
        super(virtualChannel);
    }

    @Override
    protected List<SpacePacket> createEmptyData() {
        return new ArrayList<>(100);
    }

    @Override
    public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
        this.data.get().add(new SpacePacket(packet, qualityIndicator));
    }
}
