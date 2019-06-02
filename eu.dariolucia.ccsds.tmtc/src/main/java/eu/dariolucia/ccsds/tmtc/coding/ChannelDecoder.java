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

package eu.dariolucia.ccsds.tmtc.coding;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class ChannelDecoder<T extends AbstractTransferFrame> implements Function<byte[], T> {

    public static <T extends AbstractTransferFrame> ChannelDecoder<T> create(IDecodingFunction<T> f) {
        return new ChannelDecoder<>(f);
    }

    private final IDecodingFunction<T> frameDecoder;

    private final List<Function<byte[], byte[]>> sequentialDecoders = new LinkedList<>();

    private boolean configured = false;

    private ChannelDecoder(IDecodingFunction<T> frameDecoder) {
        if(frameDecoder == null) {
            throw new NullPointerException("Frame decoder must be set");
        }
        this.frameDecoder = frameDecoder;
    }

    public ChannelDecoder<T> addDecodingFunction(Function<byte[], byte[]>... functions) {
        if(this.configured) {
            throw new IllegalStateException("Channel decoder already configured");
        }
        this.sequentialDecoders.addAll(Arrays.asList(functions));
        return this;
    }

    public ChannelDecoder<T> configure() {
        this.configured = true;
        return this;
    }

    @Override
    public T apply(byte[] item) {
        if(!this.configured) {
            throw new IllegalStateException("Channel decoder not configured yet");
        }
        byte[] toDecode = item;
        for(Function<byte[], byte[]> f : sequentialDecoders) {
            toDecode = f.apply(toDecode);
        }
        return this.frameDecoder.decode(toDecode);
    }
}
