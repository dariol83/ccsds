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
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.IVirtualChannelReceiverOutput;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.util.function.Function;

public abstract class AbstractVirtualChannelReceiverMapper<T extends AbstractTransferFrame, K> implements Function<T, K>, IVirtualChannelReceiverOutput {

    private final AbstractReceiverVirtualChannel<T> virtualChannel;
    private volatile boolean disposed = false;
    protected volatile K data = null;

    public AbstractVirtualChannelReceiverMapper(AbstractReceiverVirtualChannel<T> virtualChannel) {
        this.virtualChannel = virtualChannel;
        this.virtualChannel.register(this);
    }

    public int getVirtualChannelId() {
        return this.virtualChannel.getVirtualChannelId();
    }

    @Override
    public K apply(T t) {
        if(this.disposed) {
            throw new IllegalStateException("Virtual channel mapper disposed");
        }
        this.data = createEmptyData();
        this.virtualChannel.processFrame(t);
        return data;
    }

    protected abstract K createEmptyData();

    public void dispose() {
        this.virtualChannel.deregister(this);
        this.disposed = true;
    }

    @Override
    public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
        // Not used
    }

    @Override
    public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame lastFrame, byte[] packet, boolean qualityIndicator) {
        // Not used
    }

    @Override
    public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
        // Not used
    }

    @Override
    public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
        // Not used
    }

    @Override
    public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
        // Not used
    }
}