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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * An object capable to decode a {@link AbstractTransferFrame} objects from a byte[]. An instance of
 * this class allows the addition of a list of {@link Function} objects, which are applied in order to each provided
 * byte[], plus an {@link IDecodingFunction} capable to conver a byte[] into a subclass instance of {@link AbstractTransferFrame}.
 *
 * Depending on the type of encoding functions, it is possible to instruct the channel encoder to perform a copy of the
 * frame before submitting it to the transformation process, or to run the process directly working on the inner frame
 * buffer. Bear in mind that some transformations, such as the (de)randomization functions, are implemented to run in-place.
 *
 * In order to instantiate and configure a channel encoder, the following build pattern must be used:
 * <ul>
 * <li>A new {@link ChannelEncoder} instance is created using the create() method. By default, create() instructs the channel encoder
 * to not perform a frame copy;</li>
 * <li>Zero or more encoding functions can be added by means of the addEncodingFunction method;</li>
 * <li>To complete the configuration of the object, the method configure must be invoked.</li>
 * </ul>
 *
 * If configure is invoked and a new encoder is added, an exception is thrown.
 * If the channel encoder is attempted to be used without invoking the configure method, an exception is thrown.
 *
 * @param <T> subclass of the {@link AbstractTransferFrame} class
 */
public class ChannelDecoder<T extends AbstractTransferFrame> implements Function<byte[], T> {

    /**
     * This static method creates a channel decoder. Differently from {@link ChannelEncoder}, decoders do not copy the content of the byte[].
     * Depending on the added functions, it is possible that the provided array is modified in-place. If this behaviour is not acceptable,
     * the caller of the apply() method must clone the array beforehand.
     *
     * @param <T> the {@link AbstractTransferFrame} specific type
     * @param f the decoding function transforming a byte[] to an object of type T, always applied at the end of the decoding chain
     * @return a new channel decoder to be configured
     */
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

    /**
     * This method adds a function byte[] -> byte[] to the decoding chain. Functions are applied in the
     * order used to add them to the channel decoder.
     *
     * @param function the {@link Function} to add
     * @return this object instance
     */
    public ChannelDecoder<T> addDecodingFunction(Function<byte[], byte[]> function) {
        if(this.configured) {
            throw new IllegalStateException("Channel decoder already configured");
        }
        this.sequentialDecoders.add(function);
        return this;
    }

    /**
     * This method marks the decoder as configured, allows its usage and blocks any further addition of new {@link Function}
     * objects.
     *
     * @return this object instance
     */
    public ChannelDecoder<T> configure() {
        this.configured = true;
        return this;
    }

    /**
     * This method applies the full decoding pipeline.
     *
     * @param item the encoded transfer frame to decode
     * @return the decoded frame
     * @throws IllegalStateException if the decoder is not configured via ({@link ChannelDecoder#configure()}
     */
    @Override
    public T apply(byte[] item) {
        if(!this.configured) {
            throw new IllegalStateException("Channel decoder not configured yet");
        }
        byte[] toDecode = item;
        for(Function<byte[], byte[]> f : sequentialDecoders) {
            toDecode = f.apply(toDecode);
        }
        return this.frameDecoder.apply(toDecode);
    }
}
