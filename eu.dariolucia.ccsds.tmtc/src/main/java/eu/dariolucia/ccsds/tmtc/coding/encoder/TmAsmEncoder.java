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

package eu.dariolucia.ccsds.tmtc.coding.encoder;

import eu.dariolucia.ccsds.tmtc.coding.IEncodingFunction;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.nio.ByteBuffer;

public class TmAsmEncoder<T extends AbstractTransferFrame> implements IEncodingFunction<T> {

    public static final byte[] DEFAULT_ATTACHED_SYNC_MARKER = new byte[] { 0x1A, (byte) 0xCF, (byte) 0xFC, 0x1D };

    private final byte[] synchMarker;

    public TmAsmEncoder(byte[] synchMarker) {
        this.synchMarker = synchMarker;
    }

    public TmAsmEncoder() {
        this.synchMarker = DEFAULT_ATTACHED_SYNC_MARKER;
    }

    @Override
    public byte[] encode(T original, byte[] input) {
        if(input == null) {
            throw new NullPointerException("Input cannot be null");
        }
        ByteBuffer bb = ByteBuffer.allocate(input.length + synchMarker.length);

        return bb.put(synchMarker).put(input).array();
    }
}
