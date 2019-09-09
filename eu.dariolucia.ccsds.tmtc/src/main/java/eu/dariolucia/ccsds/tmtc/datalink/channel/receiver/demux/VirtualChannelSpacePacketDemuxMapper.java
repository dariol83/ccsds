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

/**
 * This class is used to stream out packets from a transfer frame stream. It must be noted that frames for which no virtual
 * channel is registered are dropped. Subclassing can be used to provide additional functionalities, e.g. attaching the virtual
 * channel ID as annotated value to the generated space packets.
 *
 * This class is not thread-safe.
 */
public class VirtualChannelSpacePacketDemuxMapper<T extends AbstractTransferFrame> implements Function<T, List<SpacePacket>> {

    private final Map<Integer, VirtualChannelReceiverSpacePacketMapper> vcid2mapper = new HashMap<>();

    /**
     * Create an instance of {@link VirtualChannelSpacePacketDemuxMapper}.
     */
    public VirtualChannelSpacePacketDemuxMapper() {
        // Nothing
    }

    /**
     * This method is used to register a virtual channel.
     *
     * @param channel the virtual channel to register
     * @throws IllegalArgumentException if the virtual channel ID was already registered
     */
    public void register(AbstractReceiverVirtualChannel<T> channel) {
        if(this.vcid2mapper.containsKey(channel.getVirtualChannelId())) {
            throw new IllegalArgumentException("Virtual channel receiver for VCID " + channel.getVirtualChannelId() + " already registered");
        }
        this.vcid2mapper.put(channel.getVirtualChannelId(), new VirtualChannelReceiverSpacePacketMapper<>(channel));
    }

    /**
     * This method invokes {@link VirtualChannelSpacePacketDemuxMapper#processFrame(AbstractTransferFrame)}.
     *
     * @param frame the frame to process
     * @return the list of space packets emitted by the associated virtual channel
     */
    @Override
    public List<SpacePacket> apply(T frame) {
        return processFrame(frame);
    }

    /**
     * This method routes the provided frame to the correct virtual channel (if registered) and collect its
     * output. The output is returned as list.
     *
     * @param frame the frame to process
     * @return the list of space packets emitted by the associated virtual channel
     */
    public List<SpacePacket> processFrame(T frame) {
        int vcId = frame.getVirtualChannelId();
        VirtualChannelReceiverSpacePacketMapper packetMapper = this.vcid2mapper.get(vcId);
        if(packetMapper != null) {
            return (List<SpacePacket>) packetMapper.apply(frame);
        } else {
            return Collections.emptyList();
        }
    }
}
