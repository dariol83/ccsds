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

import eu.dariolucia.ccsds.tmtc.coding.IEncodingFunction;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Function;

public class TmAsmDecoder implements Function<byte[], byte[]> {

    public static final byte[] DEFAULT_ATTACHED_SYNC_MARKER = TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER;

    private final byte[] synchMarker;

    public TmAsmDecoder(byte[] synchMarker) {
        this.synchMarker = synchMarker;
    }

    public TmAsmDecoder() {
        this.synchMarker = DEFAULT_ATTACHED_SYNC_MARKER;
    }

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
