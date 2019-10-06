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

import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;

import java.util.Arrays;
import java.util.function.UnaryOperator;

/**
 * This functional class transforms the provided frame by returning a copy having the specified sync marker removed.
 * If no sync marker is specified, the 4 bytes sync marker specified by CCSDS for RS encoded frames are used (0x1ACFFC1D).
 * This class actually checks whether the sync marker is present. If it is not detected, the apply method throws an
 * {@link IllegalArgumentException}.
 */
public class TmAsmDecoder implements UnaryOperator<byte[]> {

    public static final byte[] DEFAULT_ATTACHED_SYNC_MARKER = TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER;

    private final byte[] synchMarker;

    /**
     * Construct an instance using the provided sync marker.
     *
     * @param synchMarker the sync marker to be used
     */
    public TmAsmDecoder(byte[] synchMarker) {
        this.synchMarker = synchMarker;
    }

    /**
     * Construct an instance using the default sync marker.
     */
    public TmAsmDecoder() {
        this.synchMarker = DEFAULT_ATTACHED_SYNC_MARKER;
    }

    /**
     * This method removes the sync marker from the provided input and returns a copy of the data without the sync marker.
     *
     * @param input the data from which the sync marker shall be removed
     * @return a copy of the data without the sync marker
     * @throws NullPointerException if input is null
     * @throws IllegalArgumentException if the sync marker cannot be detected in the provided input
     */
    @Override
    public byte[] apply(byte[] input) {
        if(input == null) {
            throw new NullPointerException("Input cannot be null");
        }
        if(!Arrays.equals(input, 0, synchMarker.length, synchMarker, 0, synchMarker.length)) {
            throw new IllegalArgumentException("Configured ASM cannot be detected: " + Arrays.toString(synchMarker));
        }

        return Arrays.copyOfRange(input, synchMarker.length, input.length);
    }
}
