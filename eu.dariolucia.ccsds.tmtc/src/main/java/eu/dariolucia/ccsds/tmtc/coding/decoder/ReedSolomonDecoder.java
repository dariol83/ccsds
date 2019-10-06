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

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;

import java.util.function.UnaryOperator;

/**
 * This functional class wraps a {@link ReedSolomonAlgorithm}, including the specification of the interleaving depth and
 * the error checking (set to 0 and false by default), to allow its usage in expression using {@link java.util.stream.Stream}
 * objects or in {@link eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder} instances.
 */
public class ReedSolomonDecoder implements UnaryOperator<byte[]> {

    private final ReedSolomonAlgorithm algorithm;
    private final int interleavingDepth;
    private final boolean errorChecking;

    /**
     * Construct a function that decodes a Reed-Solomon encoded frame, with the provided interleaving depth and error
     * detection capability.
     *
     * @param rs the Reed-Solomon algorithm to use for decoding
     * @param interleavingDepth the interleaving depth (meaningful only if errorChecking is true)
     * @param errorChecking true if error detection shall be enabled (in that case apply returns null if the frame has errors), false otherwise
     */
    public ReedSolomonDecoder(ReedSolomonAlgorithm rs, int interleavingDepth, boolean errorChecking) {
        if(rs == null) {
            throw new NullPointerException("Reed-Solomon algorithm cannot be null");
        }
        this.algorithm = rs;
        this.errorChecking = errorChecking;
        this.interleavingDepth = interleavingDepth;
    }

    /**
     * Construct a function that decodes a Reed-Solomon encoded frame without error detection (quick-look).
     *
     * @param rs the Reed-Solomon algorithm
     */
    public ReedSolomonDecoder(ReedSolomonAlgorithm rs) {
        this(rs, 0, false);
    }

    @Override
    public byte[] apply(byte[] input) {
        if(input == null) {
            throw new NullPointerException("Input cannot be null");
        }
        return this.algorithm.decodeFrame(input, interleavingDepth, errorChecking);
    }
}
