/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and 
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class FlowLabelTLV implements TLV {

    public static final int TLV_TYPE = 0x05;

    private final byte[] data;

    private final int encodedLength;

    public FlowLabelTLV(byte[] data) {
        this.data = data;
        this.encodedLength = data == null ? 0 : data.length;
    }

    public FlowLabelTLV(byte[] pdu, int offset, int len) {
        // Starting from offset, assume that there is an encoded message with length len
        this.data = len > 0 ? Arrays.copyOfRange(pdu, offset, offset + len) : null;
        // Encoded length
        this.encodedLength = this.data == null ? 0 : this.data.length;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public int getType() {
        return TLV_TYPE;
    }

    @Override
    public int getLength() {
        return encodedLength;
    }

    @Override
    public byte[] encode() {
        ByteBuffer bb = ByteBuffer.allocate(2 + this.encodedLength);
        bb.put((byte) TLV_TYPE);
        bb.put((byte) (this.encodedLength & 0xFF));
        if(encodedLength > 0) {
            bb.put(data);
        }
        return bb.array();
    }

    @Override
    public String toString() {
        return "FlowLabelTLV{" +
                "data=" + Arrays.toString(data) +
                ", encodedLength=" + encodedLength +
                '}';
    }
}
