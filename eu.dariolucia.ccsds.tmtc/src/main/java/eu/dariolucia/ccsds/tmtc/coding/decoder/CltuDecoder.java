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

package eu.dariolucia.ccsds.tmtc.coding.decoder;

import eu.dariolucia.ccsds.tmtc.algorithm.BchCltuAlgorithm;

import java.util.function.UnaryOperator;

/**
 * This functional class wraps a {@link BchCltuAlgorithm} instance to allow its usage in expression using {@link java.util.stream.Stream}
 * objects or in {@link eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder} instances.
 *
 * XXX: It could be considered redundant, since the decodeCltu method can be addressed by using method references.
 */
public class CltuDecoder implements UnaryOperator<byte[]> {

    private final BchCltuAlgorithm cltuDecoderAlgorithm;

    public CltuDecoder(BchCltuAlgorithm cltuDecoderAlgorithm) {
        this.cltuDecoderAlgorithm = cltuDecoderAlgorithm;
    }

    public CltuDecoder() {
        this(new BchCltuAlgorithm());
    }

    @Override
    public byte[] apply(byte[] input) {
        if(input == null) {
            throw new NullPointerException("Input cannot be null");
        }
        return this.cltuDecoderAlgorithm.decodeCltu(input);
    }
}
