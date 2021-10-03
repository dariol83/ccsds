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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.processor;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.internal.TransformationListProcessor;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class VirtualChannelReceiverSpacePacketProcessor<T extends AbstractTransferFrame> extends TransformationListProcessor<T, SpacePacket> {

    /**
     * Construct a processor to extract {@link SpacePacket}.
     *
     * @param mapper the function mapper, from {@link AbstractTransferFrame} to the collection of space packets, it cannot be null
     * @param executor the {@link ExecutorService} used to perform the function: if null, the same thread used to inject the frame will be used to extract the packets
     * @param timely if true, data is allowed to be discarded in case of backpressure. If no data should be discarded, set it to false
     */
    public VirtualChannelReceiverSpacePacketProcessor(Function<T, ? extends Collection<SpacePacket>> mapper, ExecutorService executor, boolean timely) {
        super(mapper, executor, timely);
    }
}
