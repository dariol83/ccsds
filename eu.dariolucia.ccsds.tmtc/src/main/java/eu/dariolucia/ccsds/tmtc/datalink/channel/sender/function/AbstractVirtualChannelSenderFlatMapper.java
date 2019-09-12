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

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender.function;

import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.AbstractSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.IVirtualChannelSenderOutput;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * This abstract class allows, when subclassed, to use a virtual channel as a {@link Function} object, converting a stream
 * of {@link SpacePacket}, {@link eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData} or byte[], depending on the
 * virtual channel access mode, into a stream of transfer frames, derived from {@link AbstractTransferFrame}.
 *
 * Since this object must return a {@link Stream} when apply(K) is called, this function must be applied using the
 * {@link Stream#flatMap(Function)} method.
 *
 * When the use of the flat mapper is over, the method dispose() shall be called to deregister the flat mapper from the
 * virtual channel.
 *
 * @param <T> the specific frame class
 * @param <K> the specific output
 */
public abstract class AbstractVirtualChannelSenderFlatMapper<T extends AbstractTransferFrame, K> implements Function<K, Stream<T>>, IVirtualChannelSenderOutput<T> {

    protected final AbstractSenderVirtualChannel<T> sender;
    private volatile boolean disposed = false;
    protected volatile List<T> data = null;

    public AbstractVirtualChannelSenderFlatMapper(AbstractSenderVirtualChannel<T> sender) {
        this.sender = sender;
        this.sender.register(this);
    }

    public int getVirtualChannelId() {
        return this.sender.getVirtualChannelId();
    }

    public void dispose() {
        this.sender.deregister(this);
        this.disposed = true;
    }

    @Override
    public Stream<T> apply(K sp) {
        if (this.disposed) {
            throw new IllegalStateException("Virtual channel mapper disposed");
        }
        this.data = new LinkedList<>();
        doDispatch(sp);
        return data.stream();
    }

    @Override
    public void transferFrameGenerated(AbstractSenderVirtualChannel<T> vc, T generatedFrame, int bufferedBytes) {
        this.data.add(generatedFrame);
    }

    protected abstract void doDispatch(K data);

}
