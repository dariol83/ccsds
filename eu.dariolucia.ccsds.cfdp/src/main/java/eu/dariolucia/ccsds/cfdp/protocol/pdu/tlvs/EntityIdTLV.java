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

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;

import java.nio.ByteBuffer;

public class EntityIdTLV implements TLV {

    public static final int TLV_TYPE = 0x06;

    private final Long entityId;

    private final int encodedLength;

    public EntityIdTLV(Long entityId, int octetLength) {
        if(entityId == null && octetLength > 0) {
            throw new IllegalArgumentException("No entityId supplied, but octet length is > 0: " + octetLength);
        }
        this.entityId = entityId;
        this.encodedLength = octetLength;
    }

    public EntityIdTLV(byte[] pdu, int offset, int octetLength) {
        // Starting from offset, assume that there is an encoded message with length len
        this.entityId = BytesUtil.readInteger(pdu, offset, octetLength);
        // Encoded length
        this.encodedLength = octetLength;
    }

    public Long getEntityId() {
        return entityId;
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
            bb.put(BytesUtil.encodeInteger(entityId, encodedLength));
        }
        return bb.array();
    }

    @Override
    public String toString() {
        return "EntityIdTLV{" +
                "entityId=" + entityId +
                ", encodedLength=" + encodedLength +
                '}';
    }
}
