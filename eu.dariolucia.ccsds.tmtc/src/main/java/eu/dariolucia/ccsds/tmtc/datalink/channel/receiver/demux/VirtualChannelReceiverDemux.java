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
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class VirtualChannelReceiverDemux implements Consumer<AbstractTransferFrame> {

    private final Map<Integer, AbstractReceiverVirtualChannel> vcid2receiver = new HashMap<>();

    private final Consumer<AbstractTransferFrame> missingVcHandler;

    public VirtualChannelReceiverDemux() {
        this((Consumer<AbstractTransferFrame>) null);
    }

    public VirtualChannelReceiverDemux(Consumer<AbstractTransferFrame> missingVcHandler) {
        this.missingVcHandler = missingVcHandler;
    }

    public VirtualChannelReceiverDemux(AbstractReceiverVirtualChannel... receivers) {
        this(null, receivers);
    }

    public VirtualChannelReceiverDemux(Consumer<AbstractTransferFrame> missingVcHandler, AbstractReceiverVirtualChannel... receivers) {
        this.missingVcHandler = missingVcHandler;
        // Register all of them
        Arrays.stream(receivers).forEach(this::register);
    }

    public void register(AbstractReceiverVirtualChannel channel) {
        if(this.vcid2receiver.containsKey(channel.getVirtualChannelId())) {
            throw new IllegalArgumentException("Virtual channel receiver for VCID " + channel.getVirtualChannelId() + " already registered");
        }
        this.vcid2receiver.put(channel.getVirtualChannelId(), channel);
    }

    @Override
    public void accept(AbstractTransferFrame frame) {
        processFrame(frame);
    }

    public void processFrame(AbstractTransferFrame frame) {
        int vcId = frame.getVirtualChannelId();
        AbstractReceiverVirtualChannel rcv = this.vcid2receiver.get(vcId);
        if(rcv != null) {
            rcv.processFrame(frame);
        } else if(this.missingVcHandler != null) {
            this.missingVcHandler.accept(frame);
        }
    }
}
