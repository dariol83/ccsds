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

/**
 * This class is used to demux a stream of transfer frames coming from the same master channel. The frame is redirected to
 * the appropriate virtual channel. Upon construction, it is possible to provide an handler, which is called in case
 * a transfer frame does not belong to any of the register virtual channels.
 *
 * This class is not thread-safe.
 */
public class VirtualChannelReceiverDemux implements Consumer<AbstractTransferFrame> {

    private final Map<Integer, AbstractReceiverVirtualChannel> vcid2receiver = new HashMap<>();

    private final Consumer<AbstractTransferFrame> missingVcHandler;

    /**
     * Create a demux with no initial virtual channels and no handler for transfer frames belonging to unregistered virtual
     * channels.
     */
    public VirtualChannelReceiverDemux() {
        this(null, new AbstractReceiverVirtualChannel[0]);
    }

    /**
     * Create a demux with no initial virtual channels and a handler for transfer frames belonging to unregistered virtual
     * channels.
     *
     * @param missingVcHandler the handler for frames belonging to unregistered virtual channels
     */
    public VirtualChannelReceiverDemux(Consumer<AbstractTransferFrame> missingVcHandler) {
        this(missingVcHandler, new AbstractReceiverVirtualChannel[0]);
    }

    /**
     * Create a demux with the provided virtual channels and no handler for transfer frames belonging to unregistered virtual
     * channels.
     *
     * @param receivers the initial set of virtual channels
     */
    public VirtualChannelReceiverDemux(AbstractReceiverVirtualChannel... receivers) {
        this(null, receivers);
    }

    /**
     * Create a demux with the provided virtual channels and a handler for transfer frames belonging to unregistered virtual
     * channels.
     *
     * @param missingVcHandler the handler for not registered VCs
     * @param receivers the initial set of virtual channels
     */
    public VirtualChannelReceiverDemux(Consumer<AbstractTransferFrame> missingVcHandler, AbstractReceiverVirtualChannel... receivers) {
        this.missingVcHandler = missingVcHandler;
        // Register all of them
        Arrays.stream(receivers).forEach(this::register);
    }

    /**
     * This method registers a virtual channel after the construction of the object.
     *
     * @param channel the virtual channel to register
     * @throws IllegalArgumentException if a virtual channel ID is already registered
     */
    public void register(AbstractReceiverVirtualChannel channel) {
        if(this.vcid2receiver.containsKey(channel.getVirtualChannelId())) {
            throw new IllegalArgumentException("Virtual channel receiver for VCID " + channel.getVirtualChannelId() + " already registered");
        }
        this.vcid2receiver.put(channel.getVirtualChannelId(), channel);
    }

    /**
     * This method processes a transfer frame.
     *
     * @see VirtualChannelReceiverDemux#processFrame(AbstractTransferFrame)
     * @param frame the frame to process
     */
    @Override
    public void accept(AbstractTransferFrame frame) {
        processFrame(frame);
    }

    /**
     * This method forwards the provided transfer frame to the correct virtual channel, or to the handler if there is no
     * virtual channel registered on the virtual channel ID set on the frame. If the handler is null, the frame is silently
     * dropped.
     *
     * @param frame the transfer frame to process
     */
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
