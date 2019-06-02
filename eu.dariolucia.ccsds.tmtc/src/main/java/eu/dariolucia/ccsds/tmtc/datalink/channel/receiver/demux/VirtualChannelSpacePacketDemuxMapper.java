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

import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.function.VirtualChannelReceiverSpacePacketMapper;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class VirtualChannelSpacePacketDemuxMapper implements Function<AbstractTransferFrame, List<SpacePacket>> {

    private final Map<Integer, VirtualChannelReceiverSpacePacketMapper> vcid2mapper = new HashMap<>();

    public VirtualChannelSpacePacketDemuxMapper() {
        // Nothing
    }

    public void register(AbstractReceiverVirtualChannel channel) {
        if(this.vcid2mapper.containsKey(channel.getVirtualChannelId())) {
            throw new IllegalArgumentException("Virtual channel receiver for VCID " + channel.getVirtualChannelId() + " already registered");
        }
        this.vcid2mapper.put(channel.getVirtualChannelId(), new VirtualChannelReceiverSpacePacketMapper(channel));
    }

    @Override
    public List<SpacePacket> apply(AbstractTransferFrame frame) {
        return processFrame(frame);
    }

    public List<SpacePacket> processFrame(AbstractTransferFrame frame) {
        int vcId = frame.getVirtualChannelId();
        VirtualChannelReceiverSpacePacketMapper packetMapper = this.vcid2mapper.get(vcId);
        if(packetMapper != null) {
            return (List<SpacePacket>) packetMapper.apply(frame);
        } else {
            return Collections.emptyList();
        }
    }
}
